# HackMin — 배달 주문 서비스 백엔드 (초안)

Django + DRF 기반 배달 주문 서비스 REST API입니다. 웹·모바일 공통 백엔드구성
스펙에서 🎯 로 표시된 엔드포인트는 Vulnerable / Secure 두 모드로 구현되어
취약점 실습(SQLi, IDOR/BOLA, 파일 업로드/다운로드, SSTI, 가격 조작 등)에 사용가능

> 이메일/휴대폰/OTP 인증은 현재 범위에서 제외되어 있음

## 기술 스택
- Python 3.11, Django 5.0, Django REST Framework
- 인증: JWT(모바일, `Authorization: Bearer <token>`) + Session(점주 웹)
- DB: SQLite(테스트 환경에서 사용) -> MYSQL로 전환 해서 사용하면됨

## 설치 및 실행

```bash
python -m venv .venv
.venv/Scripts/activate           # Windows (bash: source .venv/Scripts/activate)
pip install -r requirements.txt

python manage.py migrate
python manage.py seed_demo        # 데모 계정/데이터 생성 (모든 비밀번호: pw1234)
python manage.py runserver
```

기본 계정(seed_demo): `admin` / `alice`, `bob` / `owner` / `rider` (비밀번호 `pw1234`).

## 공통 규약
| 항목 | 규칙 |
| --- | --- |
| Base URL | `/api/v1` |
| 리스트 응답 | `{ count, next, previous, results }` (일부 단순 목록은 `{ results }`) |
| 에러 포맷 | `{ "code": "...", "message": "..." }` |
| 헬스체크 | `GET /api/v1/health` |

## Vulnerable / Secure 모드 토글
🎯 엔드포인트는 요청 시점에 모드가 결정됩니다.

- 전역 기본값: 환경변수 `HACKMIN_MODE=vulnerable|secure` (기본 `vulnerable`)
- 요청별 오버라이드: 헤더 `X-Hackmin-Mode: vulnerable|secure`

```bash
# 보안 모드로 로그인
curl -X POST localhost:8000/api/v1/auth/login \
  -H 'Content-Type: application/json' -H 'X-Hackmin-Mode: secure' \
  -d '{"username":"alice","password":"pw1234"}'

# 취약 모드 — SQL Injection 인증 우회
curl -X POST localhost:8000/api/v1/auth/login \
  -H 'Content-Type: application/json' -H 'X-Hackmin-Mode: vulnerable' \
  -d "{\"username\":\"alice' -- \",\"password\":\"x\"}"
```

## 앱 구조
```
apps/
  common/       공통 유틸(에러 포맷, 페이지네이션, 역할 권한, 모드 토글, seed_demo)
  accounts/     인증(/auth), 마이페이지(/me)
  restaurants/  음식점·메뉴 조회, 위치(/locations)
  carts/        장바구니(/cart)
  orders/       주문(/orders), /me/orders
  payments/     모의 결제(/payments)
  reviews/      리뷰(/reviews), /me/reviews
  promotions/   쿠폰·찜·멤버십, /me/coupons, /me/membership
  chatbot/      챗봇(/chatbot)
  owner/        점주(/owner)
  adminpanel/   관리자(/admin)
  rider/        배달기사(/rider)  # 나중에 라이더 추가시 사용
  downloads/    파일 다운로드(/downloads)
```

## 참고
- `HACKMIN_MODE`, `HACKMIN_ALLOW_MODE_HEADER` 는 `config/settings.py` 에서 설정합니다.
- 개발 편의를 위해 `DEBUG=True`, 비밀번호 검증기 비활성, CORS 전체 허용한 상태입니다.
