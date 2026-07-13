# HackMin — 배달 주문 서비스 백엔드 (초안)

Django + DRF 기반 배달 주문 서비스 REST API입니다. 웹·모바일 공통 백엔드구성
스펙에서 🎯 로 표시된 엔드포인트는 Vulnerable / Secure 두 모드로 구현되어
취약점 실습(SQLi, IDOR/BOLA, 파일 업로드/다운로드, SSTI, 가격 조작 등)에 사용가능

> 이메일/휴대폰/OTP 인증은 현재 범위에서 제외되어 있습니다.

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

## 전체 API 명세

- 모든 경로는 `/api/v1` 기준입니다. (예: `POST /auth/login` → `POST /api/v1/auth/login`)
- 🎯 = Vulnerable / Secure 이중 모드 엔드포인트
- 권한: 공개(인증 불필요) · 인증 · 고객 · 점주 · 관리자 · 기사
- 이메일/휴대폰/OTP 인증(`/auth/verify/*`, `/auth/otp/*`, `/auth/recovery-code`)은 현재 범위에서 제외됨

### 1. 인증 `/auth`
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| POST | `/auth/signup` | 고객 회원가입 | 공개 |
| POST | `/auth/login` 🎯 | 로그인(SQL Injection) | 공개 |
| POST | `/auth/logout` | 로그아웃 | 인증 |
| POST | `/auth/refresh` 🎯 | 액세스 토큰 갱신(서명 미검증) | 공개 |
| GET | `/auth/check-duplicate` | 아이디/이메일 중복 검사 | 공개 |
| POST | `/auth/password/reset-request` 🎯 | 비밀번호 재설정 요청(계정 열거) | 공개 |
| POST | `/auth/password/reset` | 비밀번호 재설정 실행 | 공개 |

### 2. 고객 — 마이페이지 `/me`
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | `/me` 🎯 | 내 정보 조회(IDOR) | 고객 |
| PUT | `/me` 🎯 | 개인정보 수정(Mass Assignment) | 고객 |
| DELETE | `/me` | 회원 탈퇴 | 고객 |
| PUT | `/me/password` | 비밀번호 변경 | 고객 |
| GET | `/me/addresses` | 배송지 목록 | 고객 |
| POST | `/me/addresses` | 배송지 등록 | 고객 |
| GET · PUT · DELETE | `/me/addresses/{id}` 🎯 | 배송지 상세/수정/삭제(IDOR) | 고객 |
| GET | `/me/orders` 🎯 | 주문 내역 조회(IDOR) | 고객 |
| GET | `/me/reviews` | 작성 리뷰 조회 | 고객 |
| GET | `/me/coupons` | 보유 쿠폰 조회 | 고객 |
| GET | `/me/membership` | 멤버십 상태 조회 | 고객 |

### 3. 고객 — 음식점 / 메뉴
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | `/restaurants` 🎯 | 검색(음식명·음식점명·종류·가격·정렬, SQL Injection) | 공개 |
| GET | `/restaurants/{id}` | 음식점 상세 | 공개 |
| GET | `/restaurants/{id}/menus` | 메뉴 목록 | 공개 |
| GET | `/restaurants/{id}/reviews` | 음식점 리뷰 목록 | 공개 |
| GET | `/menus/{id}` | 메뉴 상세(옵션 포함) | 공개 |

### 4. 고객 — 위치 `/locations`
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | `/locations/search` | 주소 검색 | 인증 |
| GET | `/locations/nearby` | 현재 위치 기반 주변 음식점 | 인증 |

### 5. 고객 — 장바구니 `/cart`
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | `/cart` | 장바구니 조회 | 고객 |
| POST | `/cart/items` 🎯 | 음식 추가(수량·옵션 검증 우회) | 고객 |
| PUT | `/cart/items/{id}` 🎯 | 수량·옵션 변경(IDOR) | 고객 |
| DELETE | `/cart/items/{id}` | 음식 삭제 | 고객 |
| POST | `/cart/coupon` 🎯 | 쿠폰 적용(보유·유효성 검증 우회) | 고객 |
| GET | `/cart/summary` | 총액·배달비 계산 | 고객 |

### 6. 고객 — 주문 `/orders`
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| POST | `/orders` 🎯 | 주문 생성(가격 조작) | 고객 |
| GET | `/orders/{id}` 🎯 | 주문 상세(IDOR) | 고객 |
| GET | `/orders/{id}/status` | 주문 상태 확인 | 고객 |
| POST | `/orders/{id}/cancel` 🎯 | 주문 취소(IDOR·상태우회) | 고객 |
| POST | `/orders/{id}/reorder` | 동일 메뉴 재주문 | 고객 |

### 7. 고객 — 결제 `/payments` (모의 결제)
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| POST | `/payments` 🎯 | 결제 생성(IDOR·금액 조작) | 고객 |
| GET | `/payments/{id}` | 결제 상세 조회 | 고객 |
| POST | `/payments/{id}/cancel` 🎯 | 결제 취소(IDOR) | 고객 |
| POST | `/payments/{id}/refund` 🎯 | 환불 요청(금액 검증 누락) | 고객 |

### 8. 고객 — 리뷰 `/reviews`
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| POST | `/reviews` 🎯 | 리뷰 작성(주문검증 우회·Stored XSS) | 고객 |
| PUT | `/reviews/{id}` 🎯 | 리뷰 수정(IDOR) | 고객 |
| DELETE | `/reviews/{id}` | 리뷰 삭제 | 고객 |
| POST | `/reviews/{id}/images` 🎯 | 리뷰 이미지 업로드(무제한 업로드) | 고객 |

### 9. 고객 — 쿠폰 / 찜 / 챗봇 / 멤버십
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | `/coupons` 🎯 | 다운로드 가능 쿠폰 목록(코드 노출) | 고객 |
| POST | `/coupons/{id}/download` 🎯 | 쿠폰 다운로드(중복 발급) | 고객 |
| POST | `/coupons/register` | 쿠폰 코드 등록 | 고객 |
| GET | `/favorites` | 찜 목록 조회 | 고객 |
| POST | `/favorites` | 음식점 찜 등록 | 고객 |
| DELETE | `/favorites/{id}` | 찜 취소 | 고객 |
| POST | `/chatbot/message` 🎯 | 챗봇 메시지(SSTI) | 고객 |
| POST | `/membership/subscribe` 🎯 | 멤버십 가입(결제 우회) | 고객 |
| POST | `/membership/cancel` | 멤버십 해지 | 고객 |
| GET | `/membership/benefits` | 혜택 조회 | 고객 |
| GET | `/membership/payments` | 멤버십 결제 내역 | 고객 |

### 10. 점주 — 계정 / 상품 `/owner`
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| POST | `/owner/signup` | 점주 회원가입 | 공개 |
| GET | `/owner/profile` | 점주 정보 조회 | 점주 |
| PUT | `/owner/profile` | 점주 정보 수정 | 점주 |
| POST | `/owner/business-license` 🎯 | 사업자등록증 업로드(무제한 업로드) | 점주 |
| GET | `/owner/products` | 상품 목록 | 점주 |
| POST | `/owner/products` 🎯 | 음식 등록(BOLA) | 점주 |
| GET · PUT · DELETE | `/owner/products/{id}` 🎯 | 상품 상세/수정/삭제(BOLA) | 점주 |
| POST | `/owner/products/{id}/image` 🎯 | 음식 이미지 업로드(무제한 업로드) | 점주 |
| PUT | `/owner/products/{id}/status` | 판매/품절 상태 변경 | 점주 |
| GET · POST | `/owner/categories` | 카테고리 목록/생성 | 점주 |
| PUT · DELETE | `/owner/categories/{id}` | 카테고리 수정/삭제 | 점주 |
| GET · POST | `/owner/products/{id}/options` | 메뉴 옵션 목록/생성 | 점주 |
| PUT · DELETE | `/owner/options/{id}` | 메뉴 옵션 수정/삭제 | 점주 |

### 11. 점주 — 주문 / 결제 / 매출 / 리뷰
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | `/owner/orders` 🎯 | 신규·전체 주문 조회(BOLA) | 점주 |
| GET | `/owner/orders/{id}` | 주문 상세·요청사항 확인 | 점주 |
| POST | `/owner/orders/{id}/accept` | 주문 접수 | 점주 |
| POST | `/owner/orders/{id}/reject` | 주문 거절 | 점주 |
| PUT | `/owner/orders/{id}/status` 🎯 | 조리/배달 상태 변경(BOLA·상태우회) | 점주 |
| POST | `/owner/orders/{id}/cancel` | 주문 취소 | 점주 |
| GET | `/owner/payments` | 결제 내역 조회 | 점주 |
| GET | `/owner/payments/{id}` | 결제 상세 | 점주 |
| POST | `/owner/payments/{id}/cancel` | 결제 취소 | 점주 |
| POST | `/owner/payments/{id}/refund` 🎯 | 전체/부분 환불(금액 검증 누락) | 점주 |
| GET | `/owner/sales` 🎯 | 기간별 매출(SQL Injection) | 점주 |
| GET | `/owner/sales/by-menu` | 메뉴별 매출 | 점주 |
| GET | `/owner/sales/stats` | 매출 통계(그래프용) | 점주 |
| GET | `/owner/sales/export` 🎯 | 매출 CSV 다운로드(CSV Injection) | 점주 |
| GET | `/owner/reviews` | 리뷰 목록 | 점주 |
| POST | `/owner/reviews/{id}/reply` 🎯 | 리뷰 답변 작성(BOLA·Stored XSS) | 점주 |

### 12. 관리자 `/admin`
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | `/admin/users` 🎯 | 사용자 목록 조회(SQLi·해시 노출) | 관리자 |
| GET | `/admin/users/{id}` 🎯 | 사용자 상세 조회(민감정보 노출) | 관리자 |
| DELETE | `/admin/users/{id}` | 회원 탈퇴 처리 | 관리자 |
| PUT | `/admin/users/{id}/status` | 회원 상태 변경 | 관리자 |
| GET | `/admin/owners` | 점주 목록 조회 | 관리자 |
| PUT | `/admin/owners/{id}/status` | 점주 계정 활성/비활성 | 관리자 |
| GET | `/admin/orders` | 전체 주문 조회 | 관리자 |
| GET | `/admin/payments` | 전체 결제 조회 | 관리자 |

### 13. 배달기사 `/rider`
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | `/rider/deliveries` 🎯 | 배달 주문 조회(과다 노출) | 기사 |
| GET | `/rider/deliveries/{id}` 🎯 | 배달 상세·주소·연락처(IDOR·PII) | 기사 |
| PUT | `/rider/deliveries/{id}/status` 🎯 | 배달 상태 변경(IDOR) | 기사 |

### 14. 파일 다운로드 `/downloads`
| Method | Endpoint | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | `/downloads/receipt/{order_id}` 🎯 | 영수증 다운로드(IDOR) | 고객/점주 |
| GET | `/downloads/sales-report/{id}` 🎯 | 매출 보고서 다운로드(IDOR) | 점주 |
| GET | `/downloads/business-license/{id}` 🎯 | 사업자등록증 다운로드(IDOR) | 점주/관리자 |
| GET | `/downloads/order-history/{id}` 🎯 | 주문 내역 다운로드(IDOR) | 고객 |
| GET | `/downloads/attachment/{id}` 🎯 | 첨부파일 다운로드(Path Traversal) | 인증 |

## 참고
- `HACKMIN_MODE`, `HACKMIN_ALLOW_MODE_HEADER` 는 `config/settings.py` 에서 설정합니다.
- 개발 편의를 위해 `DEBUG=True`, 비밀번호 검증기 비활성, CORS 전체 허용한 상태입니다.
