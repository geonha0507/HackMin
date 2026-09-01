"""Rider delivery endpoints (/api/v1/rider/deliveries). Require rider role."""

import hashlib
import hmac
import math

from django.conf import settings
from django.contrib.auth import get_user_model
from django.db.models import Q
from django.utils import timezone
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsRider
from orders.models import Order
from promotions.services import award_order_points
from restaurants.models import Menu
from .models import DeliveryFeePolicy, Delivery, RiderLocation, RiderProfile
from .serializers import (
    DeliveryDetailSerializer,
    DeliveryListSerializer,
    RiderLocationSerializer,
    RiderMenuSerializer,
    RiderProfileSerializer,
)

User = get_user_model()

_STATUS_TO_ORDER = {
    Delivery.Status.DELIVERED: Order.Status.DELIVERED,
    Delivery.Status.DELIVERING: Order.Status.DELIVERING,
}

# [정산 fix①] 요금률(기본료·km당 요금)은 '코드 상수'로 고정한다. SQLi 로
# delivery_fee_policy 를 UPDATE 해도 이 두 값은 못 바꾼다 → 배달료를 키우는 유일한
# 레버가 '거리(km)'가 된다(= GPS 조작이 필수, 서버 DB 쓰기만으론 금액을 못 키움).
# DB(delivery_fee_policy)에서는 '상한(cap = max_fee_krw)'만 읽는다. 즉 SQLi 표적은
# 오직 상한뿐이고, 상한을 풀어도 거리(도장 검증됨)가 없으면 금액이 안 커진다.
BASE_FEE_KRW = 3000    # 기본료(코드 고정)
FEE_PER_KM = 1000      # km당 요금(코드 고정)


def compute_fee(distance_km):
    """이동 거리(km)로 배달료를 산정한다. 요금률은 코드 상수, 상한만 DB(fix①)."""
    try:
        km = max(0.0, float(distance_km))
    except (TypeError, ValueError):
        km = 0.0
    policy = DeliveryFeePolicy.get_solo()          # 상한(cap)만 DB 에서 읽는다
    fee = BASE_FEE_KRW + round(km * FEE_PER_KM)
    return min(fee, policy.max_fee_krw)


def seal_distance(delivery_id, distance_km):
    """[정산 fix⑤] 확정 이동거리 무결성 도장(서버 env 시크릿 HMAC).

    서버만 아는 DISTANCE_SEAL_SECRET 으로만 만들 수 있으므로, SQLi 로 distance_km 를
    바꿔 써도 이 도장과 불일치한다. 정상 경로(위치 핑 누적→배달완료 계산)로만 유효한
    도장이 생기고, 그 경로는 실제 GPS 궤적(=앱 내 좌표)에 의존한다.
    """
    msg = '{}:{:.3f}'.format(delivery_id, float(distance_km)).encode('utf-8')
    secret = (settings.DISTANCE_SEAL_SECRET or '').encode('utf-8')
    return hmac.new(secret, msg, hashlib.sha256).hexdigest()


def distance_ok(delivery):
    """저장된 거리 도장이 현재 distance_km 와 일치하는지(=SQLi 변조 안 됐는지)."""
    if not delivery.distance_seal:
        return False
    from django.utils.crypto import constant_time_compare
    return constant_time_compare(
        delivery.distance_seal, seal_distance(delivery.id, delivery.distance_km))


# 위치 무결성: 단일 위치 갱신이 이 거리(km)를 초과해 점프하면 순간이동으로 보고 거부한다.
# 실시간 추적 앱은 수 초 간격으로 갱신하므로 한 번에 2km를 넘을 수 없다.
# → 커스텀 클라이언트로 한 번에 먼 좌표를 찍는 방식이 막히고, 거리를 부풀리려면
#   '연속된 짧은 좌표들'을 계속 보내야 한다(= 앱 내 위치 제공자 후킹이 필요).
MAX_SEGMENT_KM = 2.0


def _haversine_km(lat1, lon1, lat2, lon2):
    """두 좌표 사이 대원거리(km)."""
    radius = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(a))


def _provision_deliveries():
    """배달 요청(delivering) 상태인데 Delivery가 없는 주문에 대해 배차 풀 생성."""
    pending = Order.objects.filter(status=Order.Status.DELIVERING, delivery__isnull=True)
    for order in pending:
        Delivery.objects.get_or_create(order=order)


@api_view(['GET'])
@permission_classes([IsRider])
def delivery_list(request):
    """배달 주문 조회. 본인 배정 건 + 미배정(가용) 건만 조회한다."""
    _provision_deliveries()
    deliveries = Delivery.objects.filter(Q(rider=request.user) | Q(rider__isnull=True))
    deliveries = deliveries.select_related('order', 'order__restaurant').order_by('-assigned_at')
    # 배달중 건은 '현재까지 이동거리·예상 배달료'를 실시간 계산해 내려준다(홈 카드 폴링용).
    loc = RiderLocation.objects.filter(rider=request.user).first()
    deliveries = list(deliveries)
    for d in deliveries:
        _apply_live_distance(d, request, loc=loc)
    return Response({'results': DeliveryListSerializer(deliveries, many=True).data})


def _get_delivery(request, pk):
    return Delivery.objects.filter(Q(rider=request.user) | Q(rider__isnull=True), pk=pk).first()


def _apply_live_distance(delivery, request, loc=None):
    """배달중(delivering) 건이면 '현재까지 이동거리·예상 배달료'를 완료와 동일한 방식
    (observed = total_distance_km - start_distance_km)으로 in-memory 세팅한다(저장 안 함).
    → 앱이 이 값을 폴링해 표시하면 완료 팝업·배달료와 정확히 일치한다."""
    if delivery.status == Delivery.Status.DELIVERING:
        if loc is None:
            loc = RiderLocation.objects.filter(rider=request.user).first()
        observed = (loc.total_distance_km - delivery.start_distance_km) if loc else 0.0
        delivery.distance_km = max(0.0, round(observed, 3))
        delivery.fee = compute_fee(delivery.distance_km)
    return delivery


@api_view(['GET'])
@permission_classes([IsRider])
def delivery_detail(request, pk):
    """배달 상세(주소·연락처).

    배달중(delivering)이면 '현재까지의 이동거리·예상 배달료'를 실시간으로 계산해 응답한다.
    완료 시점 계산(observed = total_distance_km - start_distance_km)과 '동일한 방식'이라,
    앱이 이 값을 폴링해 표시하면 완료 팝업·배달료와 정확히 일치한다. (in-memory 값만 세팅, 저장 안 함)
    """
    delivery = _get_delivery(request, pk)
    if not delivery:
        return error_response('not_found', '배달 정보를 찾을 수 없습니다.', 404)
    _apply_live_distance(delivery, request)
    return Response(DeliveryDetailSerializer(delivery).data)


@api_view(['PUT'])
@permission_classes([IsRider])
def delivery_status(request, pk):
    """배달 상태 변경(배달 중/완료). 본인 배정 건만 변경 가능(미배정 건은 수령 시 본인에게 배정)."""
    delivery = _get_delivery(request, pk)
    if not delivery:
        return error_response('not_found', '배달 정보를 찾을 수 없습니다.', 404)

    new_status = request.data.get('status')
    if new_status not in Delivery.Status.values:
        return error_response('bad_request', '유효하지 않은 상태입니다.', 400)

    if delivery.rider is None:
        delivery.rider = request.user

    delivery.status = new_status
    if new_status == Delivery.Status.DELIVERING:
        # 배달 시작 시점의 누적 이동거리를 스냅샷 — 완료까지의 증가분이 '이 배달의
        # 실제 이동거리'가 된다.
        loc = RiderLocation.objects.filter(rider=request.user).first()
        delivery.start_distance_km = loc.total_distance_km if loc else 0.0
    if new_status == Delivery.Status.DELIVERED:
        delivery.completed_at = timezone.now()
        # 클라가 보고한 distance_km 는 신뢰하지 않는다. 서버가 위치 트랙으로 관측한
        # 실제 이동거리(속도상한을 통과한 연속 좌표들의 누적 증가분)로 산정한다.
        loc = RiderLocation.objects.filter(rider=request.user).first()
        observed = (loc.total_distance_km - delivery.start_distance_km) if loc else 0.0
        delivery.distance_km = max(0.0, round(observed, 3))
        delivery.fee = compute_fee(delivery.distance_km)
        # [fix⑤] 확정 거리에 무결성 도장을 찍는다(정산이 이 도장 맞는 거리만 인정).
        delivery.distance_seal = seal_distance(delivery.id, delivery.distance_km)
    delivery.save()

    if new_status in _STATUS_TO_ORDER:
        # MySQL(mysqlclient)은 UPDATE 시 '값이 실제로 바뀐 행 수'를 리턴한다. 이미 같은
        # 상태면 0행이 되어 update_fields save 가 이를 실패로 보고 DatabaseError('Save with
        # update_fields did not affect any rows')를 낸다. 배달이 붙는 주문은 provisioning
        # 시 이미 DELIVERING 상태라, →delivering 전이가 항상 no-op UPDATE 가 되어 500 이 났다.
        # (SQLite 는 matched 행을 세어 로컬에선 재현 안 됨.) → 값이 바뀔 때만 저장한다.
        new_order_status = _STATUS_TO_ORDER[new_status]
        if delivery.order.status != new_order_status:
            delivery.order.status = new_order_status
            delivery.order.save(update_fields=['status'])
        if new_status == Delivery.Status.DELIVERED:
            award_order_points(delivery.order)

    return Response(DeliveryDetailSerializer(delivery).data)


@api_view(['GET'])
@permission_classes([IsRider])
def menus(request):
    """해킹의 민족 전체 메뉴 목록(홈 노출용). 숨김(hidden) 메뉴는 제외하고,
    매장명·가격·사진과 함께 내려준다. 사진 있는 메뉴를 우선 노출한다.

    쿼리: ?limit=N (기본 30). 이미지 URL은 상대경로일 수 있고, 앱이 절대 URL로 만든다.
    """
    try:
        limit = int(request.query_params.get('limit', 30))
    except (TypeError, ValueError):
        limit = 30
    limit = max(1, min(limit, 100))

    qs = (
        Menu.objects.exclude(status=Menu.Status.HIDDEN)
        .select_related('restaurant')
        .order_by('-id')
    )
    # 사진 있는 메뉴를 앞으로(빈 이미지는 뒤로) 정렬 — DB 종류와 무관하게 파이썬에서 처리.
    items = sorted(qs[: limit * 2], key=lambda m: (not bool(m.image), -m.id))[:limit]
    return Response({'results': RiderMenuSerializer(items, many=True).data})


@api_view(['GET', 'PUT'])
@permission_classes([IsRider])
def profile(request):
    """배달 전 정보(정산 계좌·면허·차량·희망지역·배달수단).

    - GET: 내 프로필(없으면 빈 값들).
    - PUT: 부분 수정(upsert). account_number 는 암호화 저장, 응답은 마스킹.
    """
    obj, _ = RiderProfile.objects.get_or_create(rider=request.user)
    if request.method == 'GET':
        return Response(RiderProfileSerializer(obj).data)

    serializer = RiderProfileSerializer(obj, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response(RiderProfileSerializer(obj).data)


@api_view(['GET', 'PUT'])
@permission_classes([IsRider])
def location(request):
    """라이더 본인의 실시간 위치.

    - GET: 마지막으로 저장된 내 위치(없으면 204).
    - PUT: {latitude, longitude, accuracy?} 로 내 위치를 갱신(upsert).
      해킹커넥트(라이더 앱)가 운행 중 주기적으로 호출한다.
    """
    if request.method == 'GET':
        loc = RiderLocation.objects.filter(rider=request.user).first()
        if not loc:
            return Response(status=204)
        return Response(RiderLocationSerializer(loc).data)

    serializer = RiderLocationSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    data = serializer.validated_data

    # 직전 위치와 비교해 이동속도를 검증하고, 통과한 구간거리만 누적한다.
    prev = RiderLocation.objects.filter(rider=request.user).first()
    total = 0.0
    if prev:
        # [취약] 원래는 직전 위치 대비 이동거리(2km 초과 점프)를 검증해 순간이동을 거부했으나,
        # 앱이 보낸 위치를 전부 신뢰하도록 검증을 제거한다 → 어떤 점프(순간이동)든 그대로 누적된다.
        # GPS 위조 앱이 좌표를 마음대로 찍어도 서버가 그 거리를 배달료로 인정하게 되는 취약점.
        seg = _haversine_km(prev.latitude, prev.longitude,
                            data['latitude'], data['longitude'])
        total = prev.total_distance_km + seg

    defaults = dict(data)
    defaults['total_distance_km'] = total
    loc, _ = RiderLocation.objects.update_or_create(
        rider=request.user, defaults=defaults,
    )
    return Response(RiderLocationSerializer(loc).data)


def _receipt_verified(delivery):
    """[정산 fix③] 저장된 고객 수령확인 서명을 '등록 공개키'로 재검증한다.

    수령확인 시점에 한 번 검증했더라도, 정산 집계 시점에 다시 암호검증한다.
    SQLi 로 receipt_proof/settled 를 조작해도 TEE 개인키가 만든 유효 서명이
    아니면(또는 공개키 봉인이 깨졌으면) 여기서 탈락한다.
    """
    from common.txnsig import verify_sig_stored
    proof = delivery.receipt_proof
    if not proof:
        return False
    customer = getattr(delivery.order, 'user', None)
    if customer is None:
        return False
    return verify_sig_stored(
        customer, 'POST', proof.get('path', ''), proof.get('ts'),
        proof.get('nonce'), proof.get('sig'), proof.get('key_id'), b'')


@api_view(['GET'])
@permission_classes([IsRider])
def earnings(request):
    """내 배달비 정산 현황.

    [정산 fix②③⑤] settled/fee/distance 원시 컬럼을 신뢰하지 않는다. 배달완료 건마다:
      1) 거리 도장(fix⑤)이 유효할 때만 그 거리로 배달료를 '재계산'(fix①)한다.
         도장이 없거나 어긋나면(=SQLi 로 거리 변조) 금액을 0 으로 본다.
      2) 고객 수령확인 서명(fix③)이 등록 공개키로 재검증되면 '지급 확정',
         아니면(아직 미확인 or 위조) '지급 대기'로 잡는다.
    → SQLi 로 fee/settled/distance_km 를 직접 써도 도장·서명이 없으면 정산에 안 잡힌다.
    """
    settled_total = 0
    pending_total = 0
    qs = (Delivery.objects
          .filter(rider=request.user, status=Delivery.Status.DELIVERED)
          .select_related('order'))
    for d in qs:
        amount = compute_fee(d.distance_km) if distance_ok(d) else 0
        if amount and _receipt_verified(d):
            settled_total += amount
        else:
            pending_total += amount
    return Response({'settled_total': settled_total, 'pending_total': pending_total})


def _dest_account(profile):
    """지급 대상 계좌 스냅샷(은행, 마스킹된 번호, 예금주). RiderProfile 저장값을 읽는다.
    IDOR 로 이 값이 공격자 것으로 바뀌어 있으면, 지급이 그 계좌로 나간다."""
    from accounts.crypto_utils import decrypt_aes128
    if not profile:
        return '', '', ''
    masked = ''
    enc = profile.account_number_encrypted
    if enc:
        try:
            plain = decrypt_aes128(enc)
            masked = (('*' * max(0, len(plain) - 4)) + plain[-4:]) if plain else ''
        except Exception:
            masked = ''
    return profile.bank_name or '', masked, profile.account_holder or ''


def run_payout_batch():
    """[정산 배치] 미지급 배달 중 5-fix 재검증(서명 재검증 + 거리 도장)을 통과한 것을
    라이더별로 합산해, 각 라이더의 '현재 저장된 정산계좌'로 지급하고 지급완료 처리한다.

    · 금액은 재검증된 배달만 합산 → SQLi 로 부풀린 위조 정산은 지급되지 않는다(fix②③⑤).
    · 대상 계좌는 지급 시점 RiderProfile 값 → IDOR 로 바뀐 계좌면 그리로 지급된다(절도).
    반환: [{rider_id, amount, bank, account, holder}] 지급 내역.
    """
    from .models import RiderPayout, RiderProfile
    results = []
    rider_ids = list(Delivery.objects.filter(
        status=Delivery.Status.DELIVERED, paid_out=False, rider__isnull=False,
    ).values_list('rider_id', flat=True).distinct())
    for rid in rider_ids:
        qs = Delivery.objects.filter(
            rider_id=rid, status=Delivery.Status.DELIVERED, paid_out=False,
        ).select_related('order')
        total = 0
        paid_ids = []
        for d in qs:
            if distance_ok(d) and _receipt_verified(d):
                total += compute_fee(d.distance_km)
                paid_ids.append(d.id)
        if total <= 0:
            continue
        profile = RiderProfile.objects.filter(rider_id=rid).first()
        bank, masked, holder = _dest_account(profile)
        RiderPayout.objects.create(
            rider_id=rid, amount=total,
            dest_bank_name=bank, dest_account_masked=masked, dest_account_holder=holder)
        Delivery.objects.filter(id__in=paid_ids).update(paid_out=True)
        results.append({'rider_id': rid, 'amount': total,
                        'bank': bank, 'account': masked, 'holder': holder})
    return results


@api_view(['GET'])
@permission_classes([IsRider])
def payouts(request):
    """내 지급 내역(정산 배치가 내 계좌로 지급한 기록)."""
    from .models import RiderPayout
    rows = RiderPayout.objects.filter(rider=request.user)
    data = [{'amount': p.amount, 'bank': p.dest_bank_name,
             'account': p.dest_account_masked, 'holder': p.dest_account_holder,
             'created_at': p.created_at} for p in rows]
    return Response({'total_paid': sum(r['amount'] for r in data), 'payouts': data})


# ─────────────────────────────────────────────────────────────
#  [의도된 취약점] IDOR / BOLA (Broken Object Level Authorization)
#
#  대조군: 위 profile()·location() 은 대상을 request.user 로 고정한다(정상).
#  아래 두 엔드포인트는 URL 의 rider pk 로 대상 객체를 찾고, 요청자가 그
#  라이더 본인인지 검증하지 않는다. 로그인한 라이더면 누구나 순차 id 를
#  갈아끼워 타 라이더의 정산 정보를 열람(GET)·변조(PUT)할 수 있다.
#  → 공격자가 남의 계좌번호를 자기 것으로 바꿔 정산금을 가로챈다(교육용).
# ─────────────────────────────────────────────────────────────

@api_view(['GET'])
@permission_classes([IsRider])
def rider_profile_by_id(request, pk):
    """라이더 pk 의 프로필 조회. 소유권 검증 없음(IDOR)."""
    obj = RiderProfile.objects.filter(rider_id=pk).first()
    if not obj:
        return error_response('not_found', '프로필을 찾을 수 없습니다.', 404)
    data = RiderProfileSerializer(obj).data
    # id↔사람 매핑을 쉽게 해 열거(enumeration)를 돕는다 — 취약점 데모용.
    data['rider_id'] = obj.rider_id
    # 닉네임만 컬럼 조회한다. select_related/obj.rider 로 User 전체를 로드하면 일부
    # 계정의 불량 컬럼값(예: MySQL zero-date)에서 모델 인스턴스화가 깨져 500이 난다.
    data['nickname'] = (User.objects.filter(pk=obj.rider_id)
                        .values_list('nickname', flat=True).first() or '')
    return Response(data)


@api_view(['PUT'])
@permission_classes([IsRider])
def rider_account_by_id(request, pk):
    """라이더 pk 의 정산 계좌 변경. 소유권 검증 없음(IDOR).

    body: {"account_number": "...", "bank_name"?, "account_holder"?}

    로그인한 라이더면 누구나 URL 의 pk 를 갈아끼워 **타 라이더 계좌**를 자기
    계좌로 바꿀 수 있다(=IDOR). 별도의 거래 서명 검증은 두지 않는다(토큰만 요구).
    """
    if not User.objects.filter(pk=pk).exists():
        return error_response('not_found', '라이더를 찾을 수 없습니다.', 404)
    obj, _ = RiderProfile.objects.get_or_create(rider_id=pk)
    serializer = RiderProfileSerializer(obj, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response(RiderProfileSerializer(obj).data)


@api_view(['POST'])
@permission_classes([IsRider])
def register_txn_key(request):
    """[방어 ④] 거래서명 공개키 등록.

    라이더 앱이 Android Keystore(EC P-256)에서 개인키를 만들고, 공개키(PEM)만
    올린다. 서버는 이 공개키로 계좌변경 서명을 검증한다(개인키는 저장하지 않음).
    """
    from .models import TxnKey
    from common.txnsig import reg_seal
    key_id = request.data.get('key_id')
    pem = request.data.get('public_key_pem')
    if not key_id or not pem:
        return error_response('bad_request', 'key_id/public_key_pem 이 필요합니다.', 400)
    # [fix③ 앵커] 등록과 동시에 봉인값을 계산·저장한다(SQLi 공개키 스왑 차단).
    TxnKey.objects.update_or_create(
        user=request.user, key_id=key_id,
        defaults={'public_key_pem': pem,
                  'reg_seal': reg_seal(request.user.id, key_id, pem)})
    return Response({'key_id': key_id}, status=201)


@api_view(['GET', 'POST'])
@permission_classes([IsRider])   # [의도된 취약점] 관리자 전용이어야 할 상한을 라이더가 변경 가능
def update_fee_policy(request):
    """배달비 상한(max_fee_krw) 조회/변경.

    [의도된 취약점] 배달료 상한은 관리자만 바꿀 수 있어야 하지만 IsRider 로 열려 있다.
    IDOR 응답(get_db_info)으로 현재 상한값이 노출 → 발견 → POST 로 상한을 폭증시키면,
    GPS 위조로 부풀린 거리에 곱해져 배달료가 무제한으로 커진다. (파라미터 조작만으로 가능)
    """
    policy = DeliveryFeePolicy.get_solo()
    if request.method == 'GET':
        return Response({
            'base_fee_krw': policy.base_fee_krw,
            'fee_per_km': policy.fee_per_km,
            'max_fee_krw': policy.max_fee_krw,
        })
    max_fee = request.data.get('max_fee_krw')
    if max_fee is None:
        return error_response('bad_request', 'max_fee_krw 가 필요합니다.', 400)
    try:
        policy.max_fee_krw = int(max_fee)
    except (TypeError, ValueError):
        return error_response('bad_request', 'max_fee_krw 는 정수여야 합니다.', 400)
    policy.save()
    return Response({
        'max_fee_krw': policy.max_fee_krw,
        'message': '배달비 상한이 변경되었습니다.',
    })
