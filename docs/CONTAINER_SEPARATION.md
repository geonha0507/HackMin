# 컨테이너 분리 방향 비교 — A(하드닝) vs B(3-tier 분리)

작성일 2026-07-27. 대상 커밋 기준으로 실제 코드를 세어 산정했다.

## 0. 현재 구조 요약

| 항목 | 현황 |
|---|---|
| 이미지 | `hackmin-app` **하나**를 api / web / admin-web 세 서비스가 공유 |
| 서비스 구분 | `DJANGO_ROOT_URLCONF` 환경변수만 다름 (`config.urls_api` / `urls_web` / `urls_admin`) |
| DB 접근 | **세 컨테이너 모두 ORM으로 DB 직결**. 같은 계정·같은 권한 |
| web 인증 | `django.contrib.auth` 세션 (`authenticate()` / `auth_login()`), `request.user.role` 기반 |
| api 인증 | SimpleJWT (access 6h / refresh 7d) |
| 세션 저장소 | DB (`django_session` 테이블) |

바인드 마운트 제거는 2026-07-27에 이미 적용했다. 아래 두 방향은 그 다음 단계다.

### 코드 규모 (실측)

| 대상 | Python | ORM 호출 | 템플릿 | 라우트 |
|---|---|---|---|---|
| `apps/web` (점주 웹) | 1,365줄 | 120곳 | 16개 | 18개 |
| `apps/admin_web` (관리자 웹) | 563줄 | 57곳 | 13개 | 18개 |
| 합계 | **1,928줄** | **177곳** | **29개** | **36개** |

`apps/web` 과 `apps/admin_web` 에는 `models.py` 가 없다. 데이터는 전부 남의 앱 모델을 가져다 쓴다.

```
apps/web       → accounts, restaurants, orders, reviews, enrollment
apps/admin_web → accounts, restaurants, orders, payments, enrollment, adminpanel
```

---

## 1. 방향 A — 컨테이너·DB 하드닝 (코드 수정 없음)

구조는 그대로 두고, 컨테이너 하나가 뚫렸을 때 넘어가는 범위를 줄인다.

| # | 작업 | 내용 | 소요 | 코드 변경 |
|---|---|---|---|---|
| A1 | `DJANGO_DEBUG=0` 고정 | 현재 dev compose 기본값이 `1`. DEBUG 페이지로 소스·환경변수(SECRET_KEY, DB 비번)가 통째로 노출된다 | 10분 | 0줄 |
| A2 | 시크릿을 `env_file` 로 분리 | `environment:` 에 평문으로 있는 값들을 `app.env` 로. `docker inspect` 노출 축소 | 30분 | 0줄 |
| A3 | **DB 계정 3분할** | api = 전체 권한 / web·admin-web = 실제 접근 테이블에만 `SELECT`·`UPDATE`. 권한 매핑 표를 먼저 뽑아야 함 | 2~3시간 | 0줄 |
| A4 | `read_only: true` + `tmpfs` + `cap_drop: [ALL]` | 컨테이너 내부 쓰기 차단 → 웹셸 업로드·바이너리 투입 봉쇄. media 볼륨만 쓰기 허용 | 1~2시간 | 0줄 |
| A5 | `ADMIN_BIND=127.0.0.1` + nginx IP 제한 검증 | 관리자 웹 외부 직접 접근 차단 | 30분 | 0줄 |
| A6 | 컨테이너 네트워크 분리 | web ↔ admin-web 간 통신 차단 (서로 부를 일이 없음) | 1시간 | 0줄 |

**합계: 1일 이내, 코드 변경 0줄.**

### 얻는 것

- web 컨테이너에서 RCE·SQLi가 나도 결제·회원 테이블 전체 덤프는 막힘 (A3)
- 웹셸을 떨궈 지속성을 확보하는 경로가 막힘 (A4)
- DEBUG 페이지를 통한 시크릿 유출 봉쇄 (A1)

### 한계

- web 컨테이너는 **여전히 DB에 직결**한다. 권한이 좁아질 뿐 연결 자체는 살아 있다.
- 애플리케이션 레이어 취약점(권한 우회, IDOR 등)은 그대로다. 이건 어차피 코드로 고칠 문제.

### 주의

A1(DEBUG=0)은 "정보 노출" 계열 모의해킹 시나리오를 없앤다. 그 시나리오를 훈련용으로 남겨둘 생각이면
web/admin-web만 0으로 두고 api는 유지하는 식으로 선택 적용할 것.

---

## 2. 방향 B — 3-tier 분리 (web은 API만 호출)

`apps/web` 만 담긴 이미지를 따로 만들고, 모든 데이터 접근을 `/api/v1` HTTP 호출로 바꾼다.

### API 커버리지 조사 결과

먼저 확인한 좋은 소식: **기존 API가 이미 상당 부분을 덮는다.**

| 화면 그룹 | API 상태 |
|---|---|
| 로그인 / 로그아웃 / 회원가입 | `auth/login`, `auth/logout`, `owner/signup` — **있음** |
| 마이페이지 / 비밀번호 변경 | `me`, `owner/profile`, `owner/password` — **있음** |
| 상품·카테고리 CRUD | `owner/products*`, `owner/categories*` — **있음** |
| 주문 목록·상세·상태변경 | `owner/orders*` — **있음** |
| 매출 집계 | `owner/sales*` — **있음** |
| 리뷰 목록·답글 | `owner/reviews*` — **있음** |
| 관리자 회원·점주·주문·결제 | `admin/users*`, `admin/owners*`, `admin/orders`, `admin/payments` — **있음** |
| 관리자 공지 CRUD | `admin/notices*` — **있음** |
| 입점 승인 | `enrollment/list`, `enrollment/<id>/review` — **있음** |

없어서 새로 만들어야 하는 것:

| # | 신규 엔드포인트 | 대상 모델 |
|---|---|---|
| 1 | `POST /me/withdraw` (탈퇴 신청) | `accounts.WithdrawalRequest` |
| 2 | `GET·POST /owner/restaurant` (내 가게 조회·등록) | `restaurants.Restaurant` |
| 3 | `POST·DELETE /owner/restaurant/closed-dates` | `RestaurantClosedDate` |
| 4 | `PUT /owner/restaurant/regular-closed-days` | `RestaurantRegularClosedDay` |
| 5 | `POST /owner/restaurant/image` | `restaurants.Restaurant` |
| 6 | `POST·DELETE /owner/restaurant/notices` | `RestaurantNotice` |
| 7 | `GET /admin/withdrawals`, `POST /admin/withdrawals/<pk>/decide` | `WithdrawalRequest` |
| 8 | `GET /admin/restaurant-edits`, `POST .../decide` | `RestaurantEditRequest` |
| 9 | `GET /admin/dashboard` (집계) | 여러 앱 |
| 10 | 점주의 리뷰 삭제 권한 확인·보강 | `reviews.Review` |

**약 8~10개 엔드포인트 신규 개발** (serializer + 권한 포함).

### 단계별 작업

| Phase | 내용 | 소요 |
|---|---|---|
| 0 | web 전용 settings·Dockerfile·이미지·compose 서비스 정의 | 0.5일 |
| 1 | **인증 교체** (최대 난관, 아래 상세) | 2~3일 |
| 2 | 신규 API 8~10개 개발 | 2~3일 |
| 3 | 뷰 재작성 — ORM 177곳 → HTTP 클라이언트, API 클라이언트 레이어 신규 작성 | 3~5일 |
| 4 | 템플릿 29개 수정 — 중첩 객체 접근, 페이지네이션 객체 | 1~2일 |
| 5 | 파일 업로드 프록시 (사업자등록증, 가게·메뉴 이미지 multipart 중계) | 0.5~1일 |
| 6 | 회귀 검증 + 기존 모의해킹 시나리오 재검증 | 1~2일 |

**합계: 10~16 영업일 (1인 기준). 2인 병렬이면 5~8일.**

### Phase 1이 어려운 이유

1. `apps/web/views/auth.py` 가 `authenticate()` / `auth_login()` / `auth_logout()` 을 쓴다. 이건 DB의 사용자
   테이블을 직접 읽는다. `/api/v1/auth/login` 호출로 바꾸고 JWT를 받아야 한다.
2. JWT 보관 위치가 문제다. Django 기본 세션은 **DB 백엔드**라 그대로 두면 web이 DB에 붙어야 해서 분리 의미가
   없다. `signed_cookies` 로 바꾸면 서명만 되고 암호화는 안 되므로 브라우저에서 JWT를 읽을 수 있다.
   → **Redis 컨테이너 추가**가 사실상 필수.
3. `django.contrib.auth` 를 빼면 `request.user` 가 사라진다. `apps/web/decorators.py`(37줄),
   `apps/admin_web/decorators.py`(30줄)의 `role_required` 를 세션에 담긴 JWT 클레임 기반으로 전면 재작성해야 한다.
4. `AUTH_USER_MODEL = 'accounts.User'` 도 web settings 에서 빠져야 하므로 `accounts` 앱을 안 넣는 게 가능해진다.
5. access 6시간 / refresh 7일 만료 처리, 재발급 실패 시 로그아웃 흐름을 새로 짜야 한다.

### 리스크

- **인증을 새로 짜는 과정에서 의도치 않은 권한 우회 버그가 생길 수 있다.** 모의해킹 프로젝트에서는 "심어둔
  취약점"과 "실수로 생긴 취약점"이 섞여 시나리오 검증이 어려워진다.
- 기존 취약점 시나리오가 ORM·세션 기반이면 재설계가 필요하다.
- Redis라는 운영 요소가 하나 늘어난다.

---

## 3. 비교

| | A (하드닝) | B (3-tier 분리) |
|---|---|---|
| 소요 | 1일 이내 | 10~16일 (1인) |
| 코드 변경 | 0줄 | 약 1,900줄 재작성 + API 8~10개 신규 |
| 신규 인프라 | 없음 | Redis, 이미지 2개 |
| web 침해 시 DB | 제한된 계정으로 접근 가능 | **접근 불가** (연결 자체가 없음) |
| 컨테이너 내부 쓰기 | 차단 (A4) | 별도 적용 필요 |
| 기존 시나리오 영향 | 거의 없음 (A1 제외) | 큼 — 재검증 필수 |
| 아키텍처 현실성 | 그대로 | 실제 3-tier 구성에 가까워짐 |

---

## 4. 제안

**보안 강화가 목적이면 A만으로 충분하다.** 투입 대비 효과가 압도적이고, 지금 프로젝트 일정(CI/CD 보류 중)에
부담을 주지 않는다.

**3-tier 구조 재현 자체가 목적이면 B를 하되, 다음 순서를 권한다.**

1. 먼저 A를 적용한다 (1일). B가 끝날 때까지 그냥 노출된 채로 둘 이유가 없다.
2. **PoC 먼저**: 로그인 + 점주 대시보드 화면 **하나만** API 경유로 바꿔본다 (1~2일). 여기서 인증·세션 설계가
   실제로 굴러가는지 확인한 뒤 나머지를 결정한다.
3. `apps/web`(점주 웹)만 분리하고 `apps/admin_web` 은 뒤로 미룬다 (B-lite, 6~9일). 관리자 웹은 어차피
   nginx IP 제한 뒤에 있어 노출면이 작다.
4. 한 번에 둘 다 건드리면 롤백 지점이 사라진다.
