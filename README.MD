# HackMin — 배달 주문 서비스 플랫폼

배달 주문 서비스를 위한 풀스택 프로젝트입니다. 
Django REST Framework 기반의 백엔드 API 서버, Django 템플릿 기반의 관리자/점주 웹, Android 네이티브 모바일 앱으로 구성

## 기술 스택

| 구분 | 기술 |
|---|---|
| 백엔드 | Python 3.11, Django 5.0, Django REST Framework 3.15 |
| 인증 | JWT (모바일, `Authorization: Bearer <token>`) + Session (점주/관리자 웹) |
| DB | SQLite (개발) / MySQL (운영, RDS 연동 가능) |
| 모바일 | Android (Java), Retrofit2 + OkHttp |
| 관리자/점주 웹 | Django Templates (서버 사이드 렌더링) |
| 배포 | Docker + docker-compose |

## 프로젝트 구조

```
HackMin/
├── apps/                          # Django 백엔드 앱
│   ├── accounts/                  # 인증(/auth), 마이페이지(/me)
│   │   ├── auth_urls.py           # 인증 관련 URL 라우팅
│   │   ├── auth_views.py          # 로그인, 회원가입, 중복확인, 비밀번호 재설정
│   │   ├── me_urls.py             # 마이페이지 URL 라우팅
│   │   ├── me_views.py            # 내 정보 조회/수정, 배송지 관리
│   │   ├── models.py              # User 모델 (AbstractUser 확장)
│   │   └── serializers.py         # 인증/유저 시리얼라이저
│   ├── adminpanel/                # 관리자 API (/admin)
│   │   ├── urls.py                # 관리자 URL 라우팅
│   │   └── views.py               # 사용자/점주/주문/결제 관리
│   ├── carts/                     # 장바구니 (/cart)
│   │   ├── models.py              # Cart, CartItem 모델
│   │   ├── services.py            # 장바구니 비즈니스 로직
│   │   └── views.py               # 장바구니 CRUD, 쿠폰 적용
│   ├── chatbot/                   # 챗봇 (/chatbot)
│   │   └── views.py               # 챗봇 메시지 처리
│   ├── common/                    # 공통 유틸리티
│   │   ├── exceptions.py          # 통합 에러 핸들러
│   │   ├── management/commands/   # seed_demo 커맨드
│   │   ├── mode.py                # 모드 판별 (is_vulnerable)
│   │   ├── pagination.py          # 표준 페이지네이션
│   │   └── permissions.py         # 역할 기반 권한 (IsOwner, IsAdmin 등)
│   ├── downloads/                 # 파일 다운로드 (/downloads)
│   │   └── views.py               # 영수증, 매출보고서, 사업자등록증, 첨부파일
│   ├── enrollment/                # 입점 신청
│   │   ├── models.py              # EnrollmentApplication 모델
│   │   └── views.py               # 입점 신청/승인 처리
│   ├── orders/                    # 주문 (/orders)
│   │   ├── models.py              # Order, OrderItem 모델
│   │   └── views.py               # 주문 생성/조회/취소/재주문
│   ├── owner/                     # 점주 API (/owner)
│   │   ├── account.py             # 점주 계정 관리
│   │   ├── orders.py              # 점주 주문 관리 (접수/거절/상태변경)
│   │   ├── products.py            # 상품/카테고리/옵션 CRUD
│   │   ├── reviews.py             # 리뷰 답변 관리
│   │   └── sales.py               # 매출 조회/통계/CSV 내보내기
│   ├── payments/                  # 결제 (/payments)
│   │   ├── models.py              # Payment, Refund 모델
│   │   └── views.py               # 결제 생성/취소/환불
│   ├── promotions/                # 쿠폰/찜/멤버십 (/coupons, /favorites, /membership)
│   │   ├── models.py              # Coupon, Favorite, Membership 모델
│   │   └── views.py               # 쿠폰 발급, 찜, 멤버십 가입/해지
│   ├── restaurants/               # 음식점 (/restaurants, /locations)
│   │   ├── location_views.py      # 주소 검색, 주변 음식점
│   │   ├── models.py              # Restaurant, Menu, MenuOption 모델
│   │   └── views.py               # 음식점/메뉴 조회
│   ├── reviews/                   # 리뷰 (/reviews)
│   │   └── views.py               # 리뷰 작성/수정/삭제, 이미지 업로드
│   ├── rider/                     # 배달기사 (/rider)
│   │   └── views.py               # 배달 조회/상태 변경
│   └── web/                       # 관리자/점주 웹 (Django Templates)
│       ├── decorators.py          # 로그인/역할 데코레이터
│       ├── templates/web/         # HTML 템플릿
│       │   ├── base.html          # 공통 레이아웃
│       │   ├── login.html         # 로그인 페이지
│       │   ├── signup.html        # 회원가입 페이지
│       │   ├── mypage.html        # 마이페이지
│       │   ├── my_restaurant.html # 내 음식점 관리
│       │   ├── admin/             # 관리자 페이지 (대시보드, 사용자, 주문, 결제 등)
│       │   └── owner/             # 점주 페이지 (대시보드, 상품, 주문, 매출, 리뷰 등)
│       ├── urls.py                # 웹 URL 라우팅
│       └── views/                 # 뷰 모듈
│           ├── auth.py            # 로그인/회원가입/로그아웃
│           ├── account.py         # 마이페이지, 비밀번호 변경
│           ├── owner.py           # 점주 대시보드, 상품/주문/매출/리뷰 관리
│           ├── restaurant.py      # 음식점 등록/관리
│           └── admin.py           # 관리자 대시보드, 사용자/점주/주문/결제 관리
├── config/                        # Django 프로젝트 설정
│   ├── settings.py                # DB, JWT, CORS, REST Framework 설정
│   ├── urls.py                    # 루트 URL 라우팅 (api/v1/, web/, admin/)
│   ├── wsgi.py                    # WSGI 엔트리포인트
│   └── asgi.py                    # ASGI 엔트리포인트
├── deliver_app/                   # Android 모바일 앱
│   ├── app/src/main/
│   │   ├── AndroidManifest.xml    # 앱 매니페스트 (Activity 등록)
│   │   ├── java/com/hackmin/app/
│   │   │   ├── data/
│   │   │   │   ├── api/           # Retrofit API 인터페이스 (14개)
│   │   │   │   │   ├── AuthApi.java       # 인증 (로그인, 회원가입, 중복확인)
│   │   │   │   │   ├── CartApi.java       # 장바구니
│   │   │   │   │   ├── OrderApi.java      # 주문
│   │   │   │   │   ├── RestaurantApi.java # 음식점/메뉴
│   │   │   │   │   ├── PaymentApi.java    # 결제
│   │   │   │   │   ├── ReviewApi.java     # 리뷰
│   │   │   │   │   ├── OwnerApi.java      # 점주
│   │   │   │   │   ├── AdminApi.java      # 관리자
│   │   │   │   │   ├── RiderApi.java      # 배달기사
│   │   │   │   │   ├── PromotionApi.java  # 쿠폰/멤버십
│   │   │   │   │   ├── DownloadApi.java   # 파일 다운로드
│   │   │   │   │   ├── ChatbotApi.java    # 챗봇
│   │   │   │   │   ├── LocationApi.java   # 위치/주소
│   │   │   │   │   └── UserApi.java       # 사용자 정보
│   │   │   │   └── model/         # DTO/Request/Response 모델
│   │   │   │       ├── auth/      # LoginRequest, SignupRequest, LoginResponse 등
│   │   │   │       ├── cart/      # CartDto, CartItemDto, AddCartItemRequest 등
│   │   │   │       ├── order/     # OrderDto, OrderCreateRequest 등
│   │   │   │       ├── owner/     # ProductDto, SalesSummaryDto 등
│   │   │   │       ├── payment/   # PaymentDto, RefundRequest 등
│   │   │   │       ├── promotion/ # CouponDto, MembershipDto 등
│   │   │   │       ├── restaurant/# RestaurantDetailDto, MenuDto 등
│   │   │   │       ├── review/    # ReviewDto, ReviewCreateRequest 등
│   │   │   │       ├── rider/     # DeliveryDto, DeliveryStatusRequest 등
│   │   │   │       ├── user/      # UserProfileDto, AddressDto 등
│   │   │   │       └── common/    # ApiErrorResponse, PagedResponse 등
│   │   │   ├── network/           # 네트워크 설정
│   │   │   │   ├── ApiClient.java             # Retrofit/OkHttp 클라이언트 설정
│   │   │   │   ├── HackminMode.java           # 모드 열거형
│   │   │   │   └── HackminModeInterceptor.java # X-Hackmin-Mode 헤더 인터셉터
│   │   │   └── ui/                # 화면 (Activity)
│   │   │       ├── auth/          # LoginActivity, SignupActivity, ForgotPasswordActivity
│   │   │       ├── home/          # HomeActivity (메인 화면)
│   │   │       ├── mypage/        # MyPageActivity
│   │   │       ├── cart/          # CartActivity, CartItemAdapter
│   │   │       └── order/         # OrderActivity, OrderHistoryActivity, OrderTrackingActivity
│   │   └── res/
│   │       ├── layout/            # XML 레이아웃 (12개)
│   │       ├── drawable/          # 아이콘, 체크박스 셀렉터
│   │       └── values/            # colors, strings, styles, themes
│   ├── build.gradle.kts           # 앱 빌드 설정
│   ├── gradle/                    # Gradle Wrapper
│   └── settings.gradle.kts        # 프로젝트 설정
├── docker/
│   └── entrypoint.sh              # DB 대기, 마이그레이션, 시드 데이터
├── docker-compose.yml             # api(8000), web(8001) 서비스 정의
├── Dockerfile                     # Python 3.11 기반 이미지
├── manage.py                      # Django 관리 명령어
├── requirements.txt               # Python 패키지 의존성
└── .gitignore                     # Python, Django, Android 빌드 산출물 무시
```

## 설치 및 실행

### 방법 1: 로컬 실행

```bash
# 가상환경 생성 및 활성화
python -m venv .venv
source .venv/Scripts/activate      # Windows Git Bash
# .venv\Scripts\activate           # Windows CMD
# source .venv/bin/activate        # macOS/Linux

# 패키지 설치
pip install -r requirements.txt

# DB 마이그레이션 및 데모 데이터 생성
python manage.py migrate
python manage.py seed_demo

# 서버 실행
python manage.py runserver
```

### 방법 2: Docker 실행

```bash
# 빌드 및 실행
docker-compose up --build

# 백그라운드 실행
docker-compose up -d --build

# 로그 확인
docker-compose logs -f api

# 중지
docker-compose down
```

Docker 실행 시 두 개의 서비스가 기동됩니다:

| 서비스 | 포트 | 역할 |
|---|---|---|
| `api` | `localhost:8000` | REST API 서버 (마이그레이션/시드 담당) |
| `web` | `localhost:8001` | 관리자/점주 웹 (동일 DB 공유) |

### Android 앱 실행

1. Android Studio에서 `deliver_app/` 폴더를 프로젝트로 열기
2. `network/ApiClient.java`에서 BASE_URL 확인
   - 에뮬레이터: `http://10.0.2.2:8000/api/v1/` (호스트의 localhost로 연결)
   - 실기기: `http://<PC_IP>:8000/api/v1/`
3. 빌드 후 실행

## 데모 계정

`python manage.py seed_demo` 실행 시 아래 계정이 생성됩니다. 모든 비밀번호는 `pw1234`입니다.

| 계정 | 역할 | 용도 |
|---|---|---|
| `admin` | 관리자 | 관리자 패널 접근 |
| `alice` | 고객 | 일반 고객 기능 테스트 |
| `bob` | 고객 | 일반 고객 기능 테스트 |
| `owner` | 점주 | 점주 대시보드, 상품/주문/매출 관리 |
| `rider` | 배달기사 | 배달 조회/상태 변경 |

## API 공통 규약

| 항목 | 규칙 |
|---|---|
| Base URL | `/api/v1` |
| 리스트 응답 | `{ "count", "next", "previous", "results" }` |
| 에러 포맷 | `{ "code": "...", "message": "..." }` |
| 헬스체크 | `GET /api/v1/health` → `{ "status": "ok" }` |
| 인증 헤더 | `Authorization: Bearer <access_token>` |
| 토큰 갱신 | `POST /api/v1/auth/refresh` (body: `{ "refresh": "<token>" }`) |

## API 엔드포인트 목록

모든 경로는 `/api/v1` 기준입니다.

### 1. 인증 `/auth`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/auth/signup` | 고객 회원가입 | 공개 |
| POST | `/auth/login` | 로그인 | 공개 |
| POST | `/auth/logout` | 로그아웃 | 인증 |
| POST | `/auth/refresh` | 액세스 토큰 갱신 | 공개 |
| GET | `/auth/check-duplicate` | 아이디/닉네임/이메일 중복 검사 | 공개 |
| POST | `/auth/password/reset-request` | 비밀번호 재설정 요청 | 공개 |
| POST | `/auth/password/reset` | 비밀번호 재설정 실행 | 공개 |

### 2. 마이페이지 `/me`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/me` | 내 정보 조회 | 고객 |
| PUT | `/me` | 개인정보 수정 | 고객 |
| DELETE | `/me` | 회원 탈퇴 | 고객 |
| PUT | `/me/password` | 비밀번호 변경 | 고객 |
| GET | `/me/addresses` | 배송지 목록 | 고객 |
| POST | `/me/addresses` | 배송지 등록 | 고객 |
| GET/PUT/DELETE | `/me/addresses/{id}` | 배송지 상세/수정/삭제 | 고객 |
| GET | `/me/orders` | 주문 내역 조회 | 고객 |
| GET | `/me/reviews` | 작성 리뷰 조회 | 고객 |
| GET | `/me/coupons` | 보유 쿠폰 조회 | 고객 |
| GET | `/me/membership` | 멤버십 상태 조회 | 고객 |

### 3. 음식점/메뉴

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/restaurants` | 음식점 검색 (음식명/종류/가격/정렬) | 공개 |
| GET | `/restaurants/{id}` | 음식점 상세 | 공개 |
| GET | `/restaurants/{id}/menus` | 메뉴 목록 | 공개 |
| GET | `/restaurants/{id}/reviews` | 음식점 리뷰 목록 | 공개 |
| GET | `/menus/{id}` | 메뉴 상세 (옵션 포함) | 공개 |

### 4. 위치 `/locations`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/locations/search` | 주소 검색 | 인증 |
| GET | `/locations/nearby` | 주변 음식점 조회 | 인증 |

### 5. 장바구니 `/cart`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/cart` | 장바구니 조회 | 고객 |
| POST | `/cart/items` | 음식 추가 | 고객 |
| PUT | `/cart/items/{id}` | 수량/옵션 변경 | 고객 |
| DELETE | `/cart/items/{id}` | 음식 삭제 | 고객 |
| POST | `/cart/coupon` | 쿠폰 적용 | 고객 |
| GET | `/cart/summary` | 총액/배달비 계산 | 고객 |

### 6. 주문 `/orders`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/orders` | 주문 생성 | 고객 |
| GET | `/orders/{id}` | 주문 상세 | 고객 |
| GET | `/orders/{id}/status` | 주문 상태 확인 | 고객 |
| POST | `/orders/{id}/cancel` | 주문 취소 | 고객 |
| POST | `/orders/{id}/reorder` | 동일 메뉴 재주문 | 고객 |

### 7. 결제 `/payments`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/payments` | 결제 생성 | 고객 |
| GET | `/payments/{id}` | 결제 상세 조회 | 고객 |
| POST | `/payments/{id}/cancel` | 결제 취소 | 고객 |
| POST | `/payments/{id}/refund` | 환불 요청 | 고객 |

### 8. 리뷰 `/reviews`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/reviews` | 리뷰 작성 | 고객 |
| PUT | `/reviews/{id}` | 리뷰 수정 | 고객 |
| DELETE | `/reviews/{id}` | 리뷰 삭제 | 고객 |
| POST | `/reviews/{id}/images` | 리뷰 이미지 업로드 | 고객 |

### 9. 쿠폰/찜/챗봇/멤버십

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/coupons` | 다운로드 가능 쿠폰 목록 | 고객 |
| POST | `/coupons/{id}/download` | 쿠폰 다운로드 | 고객 |
| POST | `/coupons/register` | 쿠폰 코드 등록 | 고객 |
| GET | `/favorites` | 찜 목록 조회 | 고객 |
| POST | `/favorites` | 음식점 찜 등록 | 고객 |
| DELETE | `/favorites/{id}` | 찜 취소 | 고객 |
| POST | `/chatbot/message` | 챗봇 메시지 | 고객 |
| POST | `/membership/subscribe` | 멤버십 가입 | 고객 |
| POST | `/membership/cancel` | 멤버십 해지 | 고객 |
| GET | `/membership/benefits` | 혜택 조회 | 고객 |
| GET | `/membership/payments` | 멤버십 결제 내역 | 고객 |

### 10. 점주 — 계정/상품 `/owner`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/owner/signup` | 점주 회원가입 | 공개 |
| GET | `/owner/profile` | 점주 정보 조회 | 점주 |
| PUT | `/owner/profile` | 점주 정보 수정 | 점주 |
| POST | `/owner/business-license` | 사업자등록증 업로드 | 점주 |
| GET | `/owner/products` | 상품 목록 | 점주 |
| POST | `/owner/products` | 음식 등록 | 점주 |
| GET/PUT/DELETE | `/owner/products/{id}` | 상품 상세/수정/삭제 | 점주 |
| POST | `/owner/products/{id}/image` | 음식 이미지 업로드 | 점주 |
| PUT | `/owner/products/{id}/status` | 판매/품절 상태 변경 | 점주 |
| GET/POST | `/owner/categories` | 카테고리 목록/생성 | 점주 |
| PUT/DELETE | `/owner/categories/{id}` | 카테고리 수정/삭제 | 점주 |
| GET/POST | `/owner/products/{id}/options` | 메뉴 옵션 목록/생성 | 점주 |
| PUT/DELETE | `/owner/options/{id}` | 메뉴 옵션 수정/삭제 | 점주 |

### 11. 점주 — 주문/결제/매출/리뷰

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/owner/orders` | 주문 조회 | 점주 |
| GET | `/owner/orders/{id}` | 주문 상세 | 점주 |
| POST | `/owner/orders/{id}/accept` | 주문 접수 | 점주 |
| POST | `/owner/orders/{id}/reject` | 주문 거절 | 점주 |
| PUT | `/owner/orders/{id}/status` | 조리/배달 상태 변경 | 점주 |
| POST | `/owner/orders/{id}/cancel` | 주문 취소 | 점주 |
| GET | `/owner/payments` | 결제 내역 조회 | 점주 |
| GET | `/owner/payments/{id}` | 결제 상세 | 점주 |
| POST | `/owner/payments/{id}/cancel` | 결제 취소 | 점주 |
| POST | `/owner/payments/{id}/refund` | 환불 처리 | 점주 |
| GET | `/owner/sales` | 기간별 매출 조회 | 점주 |
| GET | `/owner/sales/by-menu` | 메뉴별 매출 | 점주 |
| GET | `/owner/sales/stats` | 매출 통계 | 점주 |
| GET | `/owner/sales/export` | 매출 CSV 다운로드 | 점주 |
| GET | `/owner/reviews` | 리뷰 목록 | 점주 |
| POST | `/owner/reviews/{id}/reply` | 리뷰 답변 작성 | 점주 |

### 12. 관리자 `/admin`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/admin/users` | 사용자 목록 조회 | 관리자 |
| GET | `/admin/users/{id}` | 사용자 상세 조회 | 관리자 |
| DELETE | `/admin/users/{id}` | 회원 탈퇴 처리 | 관리자 |
| PUT | `/admin/users/{id}/status` | 회원 상태 변경 | 관리자 |
| GET | `/admin/owners` | 점주 목록 조회 | 관리자 |
| PUT | `/admin/owners/{id}/status` | 점주 계정 활성/비활성 | 관리자 |
| GET | `/admin/orders` | 전체 주문 조회 | 관리자 |
| GET | `/admin/payments` | 전체 결제 조회 | 관리자 |

### 13. 배달기사 `/rider`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/rider/deliveries` | 배달 주문 조회 | 기사 |
| GET | `/rider/deliveries/{id}` | 배달 상세 | 기사 |
| PUT | `/rider/deliveries/{id}/status` | 배달 상태 변경 | 기사 |

### 14. 파일 다운로드 `/downloads`

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/downloads/receipt/{order_id}` | 영수증 다운로드 | 고객/점주 |
| GET | `/downloads/sales-report/{id}` | 매출 보고서 다운로드 | 점주 |
| GET | `/downloads/business-license/{id}` | 사업자등록증 다운로드 | 점주/관리자 |
| GET | `/downloads/order-history/{id}` | 주문 내역 다운로드 | 고객 |
| GET | `/downloads/attachment/{id}` | 첨부파일 다운로드 | 인증 |

## 관리자/점주 웹 페이지

`localhost:8001/web/` (Docker) 또는 `localhost:8000/web/` (로컬)에서 접근합니다.

| 경로 | 페이지 | 대상 |
|---|---|---|
| `/web/login` | 로그인 | 공통 |
| `/web/signup` | 회원가입 | 공통 |
| `/web/me` | 마이페이지 | 공통 |
| `/web/me/restaurant` | 내 음식점 관리 | 점주 |
| `/web/owner/` | 점주 대시보드 | 점주 |
| `/web/owner/orders` | 주문 관리 | 점주 |
| `/web/owner/products` | 상품 관리 | 점주 |
| `/web/owner/categories` | 카테고리 관리 | 점주 |
| `/web/owner/sales` | 매출 현황 | 점주 |
| `/web/owner/reviews` | 리뷰 관리 | 점주 |
| `/web/admin/` | 관리자 대시보드 | 관리자 |
| `/web/admin/users` | 사용자 관리 | 관리자 |
| `/web/admin/owners` | 점주 관리 | 관리자 |
| `/web/admin/orders` | 주문 조회 | 관리자 |
| `/web/admin/payments` | 결제 조회 | 관리자 |
| `/web/admin/withdrawals` | 출금 요청 관리 | 관리자 |
| `/web/admin/restaurant-edits` | 음식점 정보 수정 요청 | 관리자 |

## 브랜치 전략

| 브랜치 | 담당 영역 | 비고 |
|---|---|---|
| `main` | 통합 브랜치 | 직접 커밋 금지 (문서 제외), PR 머지만 허용 |
| `feature/api` | Django 백엔드 (apps/, config/, Docker) | 모델/마이그레이션 변경은 여기서 시작 |
| `feature/web` | 관리자/점주 웹 (apps/web/) | 모델 변경 시 api 먼저 머지 후 rebase |
| `feature/app` | Android 앱 (deliver_app/) | API 인터페이스만 맞으면 독립 작업 가능 |

머지 순서: 모델 변경 시 `feature/api → main → feature/web rebase → main`

## 환경 변수 (.env)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_ENGINE` | `sqlite` | `sqlite` 또는 `mysql` |
| `DB_SQLITE_PATH` | `db.sqlite3` | SQLite 파일 경로 |
| `DB_NAME` | `hackmin` | MySQL 데이터베이스 이름 |
| `DB_USER` | `hackmin` | MySQL 사용자 |
| `DB_PASSWORD` | `hackmin` | MySQL 비밀번호 |
| `DB_HOST` | (빈 문자열) | MySQL 호스트 (RDS 엔드포인트) |
| `DB_PORT` | `3306` | MySQL 포트 |
| `DB_SSL_CA` | (빈 문자열) | RDS SSL CA 번들 경로 |
| `DJANGO_DEBUG` | `1` | 디버그 모드 (0: 비활성) |
| `DJANGO_SECRET_KEY` | (개발용 기본값) | Django 시크릿 키 |
| `SEED_DEMO` | `1` | 데모 데이터 자동 생성 (0: 비활성) |

## 패키지 의존성

```
Django==5.0.6
djangorestframework==3.15.1
djangorestframework-simplejwt==5.3.1
django-cors-headers==4.4.0
django-filter==24.2
Pillow==10.3.0
mysqlclient==2.2.4
bleach==6.1.0
```