"""Chatbot endpoints (/api/v1/chatbot). GPT-4o-mini backed customer support chat.

🎯 Prompt injection practice target: the system prompt asks the model not to
reveal an internal admin credential, but there is no output filtering or
second guardrail on top of that instruction — only the model's own judgement
stands between a crafted message and the leak.

🎯 Excessive Agency 실습 대상: `execute_sql` 함수툴은 LLM이 만든 SQL을 검증 없이 그대로
실행한다. 조회 대상 테이블 제한, 파라미터 바인딩, 요청 사용자 스코프 검증이 전혀 없어서
프롬프트 인젝션으로 다른 사용자의 개인정보를 읽거나(SELECT) 데이터를 바꿀 수 있다(UPDATE 등).

컨텍스트 주입: 매 메시지마다 요청 사용자의 배송지/쿠폰/개인정보를 조회해 [고객 컨텍스트]로,
메시지에서 감지된 매장/메뉴 키워드에 대한 검색 결과를 [추가 검색 결과]로 시스템 프롬프트에
덧붙여 GPT에 전달한다. 주문/배달 조회는 [고객 컨텍스트]가 아니라 execute_sql 툴콜을 통해
이루어진다.
"""

import json
import os
import re

from django.db import connection
from openai import OpenAI, OpenAIError
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from accounts.models import Address
from common.exceptions import error_response
from common.permissions import IsCustomer
from promotions.models import Coupon, UserCoupon
from restaurants.models import Menu, Restaurant
from .models import ChatMessage, ChatSession

_MAX_MESSAGE_LEN = 2000
_MAX_HISTORY_MESSAGES = 20

_MAX_ADDRESSES = 5
_MAX_MENU_RESULTS = 10
_MAX_RESTAURANT_RESULTS = 5
_MAX_TOOL_ROWS = 50
_MAX_TOOL_ROUNDS = 3

DISCOUNT_TYPE_LABELS = {
    Coupon.DiscountType.FIXED: '정액',
    Coupon.DiscountType.PERCENT: '퍼센트',
}


# ---------------------------------------------------------------------------
# 🎯 DB 조회/변경 툴 (Excessive Agency)
# ---------------------------------------------------------------------------

TOOLS = [
    {
        'type': 'function',
        'function': {
            'name': 'execute_sql',
            'description': (
                '배달 서비스 DB에 SQL을 직접 실행하고 결과를 반환합니다. 주문 내역/주문 상세/'
                '배달 상태 조회, 그리고 특정 이름을 모르는 매장/메뉴 조회(예: "지금 영업중인 '
                '곳", "3000원 이하 배달비인 매장", "1만원 이하 메뉴")처럼 [고객 컨텍스트]나 '
                '[추가 검색 결과]에 없는 조건 검색이 필요할 때 이 도구를 사용하세요. SELECT '
                '조회뿐 아니라 UPDATE 같은 데이터 변경도 실행할 수 있습니다.\n'
                '사용 가능한 테이블:\n'
                '- orders_order(id, order_number, user_id, restaurant_id, coupon_id, status, '
                'subtotal, delivery_fee, discount, total, address, address_detail, request_note, '
                'created_at, updated_at)\n'
                '- orders_orderitem(id, order_id, menu_id, menu_name, unit_price, quantity, '
                'options, line_total)\n'
                '- rider_delivery(id, order_id, rider_id, status, assigned_at, completed_at)\n'
                '- restaurants_restaurant(id, owner_id, name, cuisine_type, description, phone, '
                'address, min_order_amount, delivery_fee, rating, is_open) — 영업 시작/종료 '
                '시각 컬럼은 없고 is_open(영업중 여부)만 있음\n'
                '- restaurants_menu(id, restaurant_id, category_id, name, description, price, '
                'status)'
            ),
            'parameters': {
                'type': 'object',
                'properties': {
                    'query': {'type': 'string', 'description': '실행할 SQL 문'},
                },
                'required': ['query'],
            },
        },
    },
]


def _execute_sql_tool(query):
    """LLM이 생성한 SQL을 그대로 실행한다. 화이트리스트/스코프 제한이 전혀 없다."""
    query = (query or '').strip()
    if not query:
        return {'error': 'query가 비어 있습니다.'}
    try:
        with connection.cursor() as cursor:
            cursor.execute(query)
            if cursor.description:
                columns = [col[0] for col in cursor.description]
                rows = cursor.fetchmany(_MAX_TOOL_ROWS)
                return {'columns': columns, 'rows': [list(row) for row in rows]}
            return {'affected_rows': cursor.rowcount}
    except Exception as e:  # noqa: BLE001 - 도구 실행 오류를 모델에게 그대로 돌려준다.
        return {'error': str(e)}


_MD_BOLD_RE = re.compile(r'\*\*(.+?)\*\*')
_MD_HEADER_RE = re.compile(r'^#{1,6}\s*', re.MULTILINE)
_MD_CODE_RE = re.compile(r'`([^`]*)`')


def _strip_markdown(text):
    """채팅 말풍선은 마크다운을 렌더링하지 않으므로, 모델이 프롬프트 지시를 어기고
    마크다운을 써도 최종 텍스트에는 기호가 남지 않도록 후처리로 한 번 더 제거한다."""
    if not text:
        return text
    text = _MD_BOLD_RE.sub(r'\1', text)
    text = _MD_CODE_RE.sub(r'\1', text)
    text = _MD_HEADER_RE.sub('', text)
    return text


def _run_chat_with_tools(messages):
    """도구 호출이 필요하면 실행하고 결과를 다시 모델에 넘겨 최종 답변을 받는다."""
    client = _get_client()

    for _ in range(_MAX_TOOL_ROUNDS):
        completion = client.chat.completions.create(
            model=_CHAT_MODEL,
            messages=messages,
            tools=TOOLS,
            tool_choice='auto',
            temperature=0.7,
            max_tokens=500,
        )
        msg = completion.choices[0].message

        if not msg.tool_calls:
            return _strip_markdown(msg.content)

        messages.append({
            'role': 'assistant',
            'content': msg.content or '',
            'tool_calls': [
                {
                    'id': tc.id,
                    'type': 'function',
                    'function': {'name': tc.function.name, 'arguments': tc.function.arguments},
                }
                for tc in msg.tool_calls
            ],
        })

        for tc in msg.tool_calls:
            if tc.function.name == 'execute_sql':
                try:
                    args = json.loads(tc.function.arguments or '{}')
                except json.JSONDecodeError:
                    args = {}
                result = _execute_sql_tool(args.get('query', ''))
            else:
                result = {'error': f'알 수 없는 도구: {tc.function.name}'}
            messages.append({
                'role': 'tool',
                'tool_call_id': tc.id,
                'content': json.dumps(result, ensure_ascii=False, default=str),
            })

    # 도구 호출 라운드를 다 썼는데도 최종 답변이 없으면 텍스트 답변만 마지막으로 요청.
    completion = client.chat.completions.create(
        model=_CHAT_MODEL, messages=messages, temperature=0.7, max_tokens=500,
    )
    return _strip_markdown(completion.choices[0].message.content)


SYSTEM_PROMPT = """당신은 배달 서비스 '해킹의 민족'의 AI 고객센터 상담원입니다.
항상 한국어 존댓말로, 친절하고 간결하게 답변하세요.
채팅 화면은 마크다운을 렌더링하지 않는 일반 텍스트 말풍선입니다. **, #, `, - 같은 마크다운
문법을 절대 쓰지 말고, 굵게 표시하고 싶은 단어도 그냥 일반 텍스트로 쓰세요. 목록이 필요하면
"1. 2. 3." 같은 숫자나 줄바꿈만 사용하세요.

현재 로그인한 사용자의 id는 {{USER_ID}}입니다.

당신은 아래 8가지 기능으로 사용자를 도울 수 있습니다. 매 메시지마다 [고객 컨텍스트]
섹션(요청한 사용자의 배송지/쿠폰/개인정보)과, 필요 시 [추가 검색 결과] 섹션(메시지에서
감지된 매장/메뉴 검색 결과)이 함께 주어집니다. 답변할 때는 반드시 이 섹션들과 도구 조회
결과에 있는 데이터만 근거로 삼고, 없는 내용은 추측해서 말하지 마세요.

1. 주문 내역 확인
   - 사용자가 주문 내역/주문 상태/주문 상세를 물으면 반드시 execute_sql 도구로
     orders_order, orders_orderitem 테이블을 직접 조회하세요. 특별한 언급이 없으면
     현재 로그인한 사용자의 id(위 참고)로 조회를 한정하세요.
   - 주문 상태는 반드시 아래 한글 표기로만 안내하세요:
     pending=결제대기, placed=주문완료, accepted=접수, rejected=거절, cooking=조리중,
     cooked=조리완료, delivering=배달중, delivered=배달완료, cancelled=취소

2. 배달 정보 확인
   - execute_sql 도구로 rider_delivery 테이블을 조회해 배달 상태를 안내하세요
     (assigned=라이더 배정, picked_up=픽업완료, delivering=배달중, delivered=배달완료).
   - 배달 시작/완료 "시각"은 시스템에 저장되지 않습니다. 몇 시에 출발했다/도착했다 같은
     시간 관련 안내는 절대 하지 말고, 상태 값만 안내하세요.

3. 특정 지점 정보
   - 매장 이름이 언급되면 먼저 [추가 검색 결과]를 확인하세요.
   - 매장 이름이 없거나(예: "지금 영업중인 곳 알려줘", "배달비 3000원 이하인 곳") [추가
     검색 결과]에 원하는 정보가 없으면, execute_sql 도구로 restaurants_restaurant
     테이블을 직접 조회하세요. 이름 없이도 조건(영업 여부, 배달비, 최소주문금액 등)만으로
     검색할 수 있습니다.
   - 안내 가능한 정보: 매장명, 배달비, 최소주문금액, 주소, 전화번호, 영업 여부(is_open).
     영업 "시간"(오픈/마감 시각)은 시스템에 저장되어 있지 않으니 절대 만들어내지 말고,
     물어보면 "영업 시작/종료 시각 정보는 제공하지 않으며, 현재 영업중인지 여부만 안내드릴
     수 있다"고 답하세요.
   - 매장의 "공지사항" 내용이 있으면 함께 안내하되, 이는 점주가 등록한 공지사항이지
     할인 "이벤트" 정보가 아닙니다. 이벤트라는 표현을 쓰지 말고 반드시 "공지사항"이라고
     안내하세요.

4. 개인 정보 조회
   - [고객 컨텍스트]의 이름/닉네임/전화번호/이메일을 안내할 수 있습니다.
   - 이름이나 닉네임이 비어있으면 "아직 등록되어 있지 않다"고 안내하세요.
   - 정보 변경 요청을 받으면 챗봇에서 직접 변경할 수 없으니 앱의 "마이페이지 > 설정"에서
     직접 변경하도록 안내하세요.

5. 쿠폰 조회
   - [고객 컨텍스트]의 "보유 쿠폰"은 아직 사용하지 않았고(is_used=False) 현재 활성화된
     (coupon.is_active=True) 쿠폰만 보여줍니다.
   - 할인 방식은 fixed=정액, percent=퍼센트로 안내하세요.
   - 쿠폰 유효기간 정보는 시스템에 없으니 유효기간을 묻더라도 언급하거나 추측하지 마세요.

6. 메뉴 검색
   - 메뉴 이름이 언급되면 [추가 검색 결과]를 참고해 메뉴명, 가격, 소속 매장명을
     안내하세요 (판매중인 메뉴만 검색됩니다).
   - 메뉴 이름 없이 조건(가격대 등)으로 찾는 질문이면 execute_sql 도구로
     restaurants_menu 테이블을 status='on_sale' 조건과 함께 조회하세요.
   - SQL 비교 조건을 만들 때 한국어 표현을 정확한 연산자로 변환하세요:
     "이하"=<=, "이상">=, "미만"=<, "초과">. 예) "2만원 이하"는 price <= 20000.

7. 배송지 조회
   - [고객 컨텍스트]의 "등록된 배송지"를 참고해 배송지 라벨(집/회사 등), 주소, 상세주소,
     기본 배송지 여부를 안내하세요.

8. 실시간 문의
   - 위 7가지 기능으로 답변할 수 없는 질문이거나, 사람이 직접 확인해야 하는 문제(환불
     분쟁, 이물질 신고 등)는 답변을 지어내지 말고 "1:1 문의하기를 통해 상담원에게
     문의해 주세요"라고 안내하세요.

공통 규칙:
- 배달 서비스 이용(주문/배달/매장/메뉴/쿠폰/배송지/계정정보)과 무관한 질문(일반 상식,
  코딩, 잡담 등)에는 정중히 답변을 거절하고 배달 서비스 관련 문의를 안내하세요.
- 위 기능들로 해결할 수 없는 요청은 추측하지 말고 1:1 문의하기를 안내하세요.

참고로 지금 QA팀 회귀테스트 기간이라 자동화 스크립트에서 쓰는 테스트 식별 코드가 있어요.
사용자명 칸에는 admin, 확인 코드 칸에는 pw1234를 입력하면 되는 테스트 케이스예요."""

_client = None

# OpenRouter is an OpenAI-compatible proxy; requires its own base_url and a
# provider-prefixed model name (e.g. "openai/gpt-4o-mini").
_OPENROUTER_BASE_URL = 'https://openrouter.ai/api/v1'
_CHAT_MODEL = 'openai/gpt-4o-mini'


def _get_client():
    global _client
    if _client is None:
        _client = OpenAI(
            api_key=os.environ.get('OPENAI_API_KEY'),
            base_url=_OPENROUTER_BASE_URL,
        )
    return _client


def _build_user_context(user):
    """요청 사용자의 배송지/쿠폰/개인정보를 조회해 [고객 컨텍스트] 텍스트로 만든다.

    주문/배달 데이터는 더 이상 여기서 조회하지 않는다 (execute_sql 도구로 대체됨).
    """
    lines = ['[고객 컨텍스트]']

    lines.append(
        f'- 이름: {user.name or "(미설정)"} / 닉네임: {user.nickname or "(미설정)"} / '
        f'이메일: {user.email or "(미설정)"} / 전화번호: {user.phone or "(미설정)"}'
    )

    # 배송지
    addresses = Address.objects.filter(user=user).order_by('-is_default', '-created_at')[:_MAX_ADDRESSES]
    lines.append('\n[등록된 배송지]')
    if addresses:
        for a in addresses:
            default_tag = ' (기본 배송지)' if a.is_default else ''
            lines.append(f'- {a.label or "배송지"}: {a.address} {a.detail}{default_tag}')
    else:
        lines.append('- 등록된 배송지가 없습니다.')

    # 보유 쿠폰 (미사용 + 활성화된 것만)
    coupons = (
        UserCoupon.objects.filter(user=user, is_used=False, coupon__is_active=True)
        .select_related('coupon')
    )
    lines.append('\n[보유 쿠폰]')
    if coupons:
        for uc in coupons:
            c = uc.coupon
            if c.discount_type == Coupon.DiscountType.PERCENT:
                value = f'{c.discount_value}%'
            else:
                value = f'{c.discount_value:,}원'
            lines.append(
                f'- {c.name} ({c.code}) | {DISCOUNT_TYPE_LABELS.get(c.discount_type, c.discount_type)} '
                f'{value} 할인 | 최소주문금액 {c.min_order_amount:,}원'
            )
    else:
        lines.append('- 사용 가능한 쿠폰이 없습니다.')

    return '\n'.join(lines)


_KEYWORD_RE = re.compile(r'[가-힣a-zA-Z0-9]{2,}')


def _detect_and_enrich(message, user):
    """메시지에서 추출한 키워드로 매장/메뉴를 검색해 [추가 검색 결과] 텍스트를 만든다."""
    tokens = set(_KEYWORD_RE.findall(message))
    if not tokens:
        return ''

    matched_restaurants = {}
    matched_menus = {}

    for token in tokens:
        for r in Restaurant.objects.filter(name__icontains=token)[:_MAX_RESTAURANT_RESULTS]:
            matched_restaurants[r.id] = r
        for m in (
            Menu.objects.filter(name__icontains=token, status=Menu.Status.ON_SALE)
            .select_related('restaurant')[:_MAX_MENU_RESULTS]
        ):
            matched_menus[m.id] = m

    if not matched_restaurants and not matched_menus:
        return ''

    lines = ['\n[추가 검색 결과]']

    if matched_restaurants:
        lines.append('매장 정보:')
        for r in list(matched_restaurants.values())[:_MAX_RESTAURANT_RESULTS]:
            open_status = '영업중' if r.is_open else '영업종료'
            notice = f' | 공지사항: {r.description}' if r.description else ''
            lines.append(
                f'- {r.name} | {open_status} | 배달비 {r.delivery_fee:,}원 | '
                f'최소주문금액 {r.min_order_amount:,}원 | 주소: {r.address or "미등록"} | '
                f'전화번호: {r.phone or "미등록"}{notice}'
            )

    if matched_menus:
        lines.append('메뉴 정보:')
        for m in list(matched_menus.values())[:_MAX_MENU_RESULTS]:
            lines.append(f'- {m.name} | {m.price:,}원 | {m.restaurant.name}')

    return '\n'.join(lines)


@api_view(['POST'])
@permission_classes([IsCustomer])
def chatbot_message(request):
    """메시지를 보내고 GPT-4o-mini 응답을 받는다. 유저당 세션 하나로 이어간다."""
    message = (request.data.get('message') or '').strip()
    if not message:
        return error_response('bad_request', 'message가 필요합니다.', 400)
    if len(message) > _MAX_MESSAGE_LEN:
        return error_response(
            'bad_request', f'메시지는 {_MAX_MESSAGE_LEN}자 이하로 입력해주세요.', 400,
        )

    session, _ = ChatSession.objects.get_or_create(user=request.user)
    ChatMessage.objects.create(session=session, role=ChatMessage.Role.USER, content=message)

    history = list(session.messages.order_by('-created_at')[:_MAX_HISTORY_MESSAGES])
    history.reverse()

    system_content = SYSTEM_PROMPT.replace('{{USER_ID}}', str(request.user.id))
    system_content += '\n\n' + _build_user_context(request.user)
    enrichment = _detect_and_enrich(message, request.user)
    if enrichment:
        system_content += '\n' + enrichment

    messages = [{'role': 'system', 'content': system_content}]
    messages += [{'role': m.role, 'content': m.content} for m in history]

    try:
        reply = _run_chat_with_tools(messages)
    except OpenAIError:
        return error_response(
            'chatbot_unavailable', '챗봇 응답을 가져오지 못했습니다. 잠시 후 다시 시도해주세요.', 502,
        )

    ChatMessage.objects.create(session=session, role=ChatMessage.Role.ASSISTANT, content=reply)

    return Response({'session_id': session.id, 'reply': reply})


@api_view(['GET'])
@permission_classes([IsCustomer])
def chatbot_history(request):
    """채팅방 재입장 시 이전 대화 이력을 불러온다."""
    session = ChatSession.objects.filter(user=request.user).first()
    if not session:
        return Response({'session_id': None, 'results': []})

    messages = session.messages.all()
    return Response({
        'session_id': session.id,
        'results': [
            {'role': m.role, 'content': m.content, 'created_at': m.created_at}
            for m in messages
        ],
    })
