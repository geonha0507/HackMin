# 백엔드 변경 요약 — QA 발견 버그 5건 (백엔드 필요 항목)

지난 QA 테스트에서 나온 15개 항목 중 Android 단독으로 고칠 수 없었던 5개(백엔드 모델/로직 변경 필요)를 처리했습니다.
브랜치: `feature/app` (아직 push 전, 리뷰 후 병합 여부 상의 예정)

각 항목마다 **무엇을(What) / 어떻게(How) / 왜(Why)** 순서로 정리했습니다. 신규 마이그레이션 3개가 생겼습니다
(`adminpanel.0001_initial`, `promotions.0004_...`, `restaurants.0005_menu_is_membership_only`) — 병합 시 `migrate` 필요합니다.

---

## 1. 장바구니 동일 메뉴 중복 담기 (QA #12)

**What**: 같은 메뉴 + 같은 옵션 조합을 두 번 담으면 장바구니에 행이 2개로 쌓이던 것을 하나로 합치도록 수정.

**How**: `apps/carts/views.py::add_item` — 새 `CartItem`을 만들기 전에 같은 `menu` + 같은 `options`(정렬 후 비교) 조합의 기존 행이 있는지 먼저 찾고, 있으면 `quantity`만 더함. 모델/마이그레이션 변경 없음, 뷰 로직만 수정.

**Why**: 기존 로직이 `CartItem.objects.create()`를 무조건 호출해서 매번 새 행을 만들었습니다. 사용자가 같은 메뉴를 "수량 추가"로 두 번 누르면 장바구니에 같은 메뉴가 줄줄이 나열되는 버그였습니다.

**하위호환성**: 응답 스키마(`CartSerializer`) 변화 없음. 클라이언트는 그대로 사용 가능하며, 오히려 항목 수가 줄어드는 방향(버그 수정)이라 기존 클라이언트 로직과 충돌 없습니다.

---

## 2. 다른 음식점 메뉴 담을 때 확인 없이 막힘 (QA #13)

**What**: 기존에는 장바구니에 다른 음식점 메뉴를 담으려 하면 `409 restaurant_conflict`로 그냥 막혔습니다(사용자 입장에선 "왜 안 담기지?"). "장바구니를 비우고 새로 담기" 플로우를 지원하도록 **`DELETE /api/v1/cart`** 엔드포인트를 추가했습니다.

**How**: `apps/carts/views.py::cart_detail`이 기존 `GET`에 더해 `DELETE`도 받도록 확장 (`@api_view(['GET', 'DELETE'])`). `DELETE` 시 장바구니 항목 전체 삭제 + `restaurant`/`coupon` 초기화. URL 변경 없음(`path('cart', ...)` 그대로, 메서드만 추가).

**Why**: 409 자체는 의도된 정책(단일 음식점 제약)이라 유지했고, 다만 그 이후 "비우고 다시 담기"를 클라이언트가 API 호출 2번(`DELETE /cart` → `POST /cart/items`)으로 처리할 수 있는 수단이 없었습니다.

**⚠️ Android 후속 작업 필요**: 이번 변경은 엔드포인트만 추가한 것이고, Android 쪽에서 409 응답을 받았을 때 "기존 장바구니를 비우고 새로 담으시겠습니까?" 확인창 → 확인 시 `DELETE /cart` 후 재시도하는 로직은 아직 안 붙였습니다. 이건 제가 다음으로 처리하겠습니다.

---

## 3. 멤버십 포인트 적립/보유 표시 없음 (QA #10)

**What**: 멤버십 화면에 "포인트 적립 3%"라는 안내 문구는 있었지만 실제로 적립되는 로직 자체가 없었습니다. 실제 적립 로직 + 잔액 조회 API를 추가했습니다.

**How**:
- `Membership` 모델에 `points`(보유 잔액) 필드 추가.
- `MembershipPointTransaction` 원장(ledger) 모델 신규 추가 (`membership`, `order`, `type`(earn/spend), `amount`, `balance_after`, `created_at`) — `MembershipPayment`와 동일한 패턴으로, 적립 내역을 감사(audit) 가능하게 남깁니다.
- `apps/promotions/services.py::award_order_points(order)` — 주문 총액의 3%(`POINT_EARN_RATE`, 안내 문구와 동일 값)를 활성 멤버십 회원에게만 적립. 동일 주문 중복 적립 방지 로직 포함.
- 적립 트리거 위치: `apps/rider/views.py::delivery_status` — 배달원이 상태를 `delivered`로 바꾸는 시점(주문이 실제로 완료되는 유일한 지점)에서 호출.
- 신규 API: `GET /api/v1/membership/points` → `{balance, results: [...]}`. 기존 `GET /me/membership` 응답(`MembershipSerializer`)에도 `points` 필드가 추가되어 자동으로 노출됩니다.

**Why**: 스펙 3.9절에 "포인트 적립"이 명시된 기능인데 실제 구현이 없었고, 사양서 5.2절에 "주문 취소 후 포인트 유지"라는 엣지케이스가 명시돼 있어서 — 취소/거절 주문에서는 절대 적립되지 않고 오직 배달완료 시점에만 적립되도록 설계했습니다(별도 차감 로직 불필요).

**⚠️ 확인 필요**: 3%라는 요율은 기존 `membership_benefits()` 안내 문구를 그대로 따른 것으로, 실제 정책 수치가 다르다면 `apps/promotions/services.py`의 `POINT_EARN_RATE` 하나만 바꾸면 됩니다. 포인트를 실제 결제에 사용(차감)하는 기능은 이번 범위에 없습니다 — 필요하면 별도 논의 부탁드립니다.

---

## 4. 멤버십 전용 쿠폰/상품 개수 표시 없음 (QA #11)

**What**: "멤버십 전용" 쿠폰/상품이라는 개념 자체가 DB에 없어서 개수를 보여줄 수 없었습니다. 전용 여부를 표시하는 플래그와, 실제로 비회원은 받을 수 없도록 하는 검증을 추가했습니다.

**How**:
- `Coupon.is_membership_only` (BooleanField, default False) 추가.
- `Menu.is_membership_only` (BooleanField, default False) 추가 (restaurants 앱).
- `GET /membership/benefits` 응답에 `membership_only_coupon_count`, `membership_only_product_count` 추가.
- 검증: 비활성 멤버십 사용자가 전용 쿠폰을 `POST /coupons/{id}/download` 또는 `POST /coupons/register`로 받으려 하면 `403 membership_required`. 전용 메뉴를 `POST /cart/items`로 담으려 해도 동일하게 `403`.
- `CouponPublicSerializer`/`CouponFullSerializer`/`MenuListSerializer`/`MenuDetailSerializer`에 `is_membership_only` 필드 노출.

**Why**: 단순히 "개수만 보여주자"로 끝내면 "전용"이라는 라벨이 사실상 의미 없는 텍스트에 불과해서, 실제 접근 제한까지 같이 넣었습니다. (개수만 필요하고 접근 제한은 원치 않으시면 `carts/views.py`의 403 체크와 `promotions/views.py`의 403 체크만 제거하면 됩니다 — 필드/카운트 로직은 그대로 둬도 됩니다.)

**⚠️ 점주 웹 후속 작업 필요**: 지금은 이 플래그를 켤 수 있는 UI가 없습니다(Django admin에도 미등록). 점주가 상품 등록 시 "멤버십 전용" 체크박스를 킬 수 있으려면 점주 웹의 상품 등록/수정 화면(`apps/owner/products.py` 관련 프론트)에 필드 추가가 필요합니다. 쿠폰 쪽도 관리자 쿠폰 발급 화면에 동일하게 필요합니다.

---

## 5. 공지사항 벨 아이콘 무반응 (QA #9)

**What**: 공지사항 기능 자체가 백엔드에 전혀 없어서 벨 아이콘을 눌러도 보여줄 데이터가 없었습니다. `Notice` 모델과 조회/관리 API를 신규로 추가했습니다.

**How**:
- `apps/adminpanel/models.py`에 `Notice` 모델 신규 (title, content, is_pinned, created_by, created_at, updated_at).
- 공개 조회(로그인한 사용자 누구나): `GET /api/v1/notices`, `GET /api/v1/notices/{id}`.
- 관리자 CRUD: `GET/POST /api/v1/admin/notices`, `PUT/DELETE /api/v1/admin/notices/{id}` (`IsAdminRole` 권한).
- 정렬은 고정(`is_pinned`) 우선, 최신순.

**Why**: 스펙 3.7~4절 어딘가에 공지사항이 언급되지만 실제 모델이 없었고, QA에서 지적된 "벨 아이콘 무반응"의 근본 원인이었습니다.

**🔴 보안 관련 참고사항 (중요)**: 스펙 5.1절에 공지사항이 XSS 실습 대상 중 하나(`* 공지사항`)로 명시돼 있습니다. 이번 구현은 `content`를 순수 텍스트로 저장/반환만 하고(DRF가 JSON으로 이스케이프 없이 그대로 내려줌), 별도의 취약/보안 모드 분기는 넣지 않았습니다 — `apps/common/mode.py`의 `is_vulnerable()`가 이미 "Vulnerable mode has been removed"로 하드코딩되어 있어서, 다른 앱들처럼 죽은 분기를 새로 추가하지 않는 게 낫다고 판단했습니다. XSS 실습을 이 공지사항에서 다시 살리고 싶으시면, 웹 관리자 페이지(`apps/web`)에서 `content`를 렌더링하는 방식(이스케이프 여부)을 정책적으로 정해주시면 그에 맞춰 반영하겠습니다. Android 클라이언트는 TextView에 그대로 넣으면 HTML이 해석되지 않으므로 네이티브 앱에서는 실질적 위험이 없습니다.

---

## 종합 체크리스트

| # | 항목 | 마이그레이션 | 신규 API | 기존 API 응답 변경 | Android 후속 작업 |
|---|---|---|---|---|---|
| 12 | 장바구니 중복 병합 | 없음 | 없음 | 없음 (행 수만 줄어듦) | 없음 |
| 13 | 장바구니 비우기 | 없음 | `DELETE /cart` | 없음 | 409 시 확인창 → DELETE 후 재시도 (미구현) |
| 10 | 멤버십 포인트 | O (promotions) | `GET /membership/points` | `MembershipSerializer`에 `points` 추가 | 마이페이지/멤버십 화면에 포인트 표시 (미구현) |
| 11 | 멤버십 전용 쿠폰/상품 | O (promotions, restaurants) | 없음 | 쿠폰/메뉴 serializer에 `is_membership_only`, benefits에 카운트 2종 추가 | UI에 "전용" 배지 표시 (미구현) |
| 9 | 공지사항 | O (adminpanel, 신규) | `GET/POST/PUT/DELETE /notices`, `/admin/notices` | — | 벨 아이콘 → 목록 화면 신규 제작 필요 (미구현) |

**검증**: `manage.py check` 통과, Django 테스트 클라이언트로 5개 항목 각각의 정상/실패 케이스를 수동 스모크 테스트하여 전부 통과 확인했습니다(기존 저장소에 pytest/tests.py 구조가 없어 별도 테스트 파일은 추가하지 않았습니다 — 필요하시면 말씀해주세요).

이번 커밋은 백엔드 API 계약(응답 스키마 확장, 신규 엔드포인트)만 추가/확장하는 방향으로 최소화했고, 기존 필드를 제거하거나 타입을 바꾼 곳은 없습니다. Android 쪽 후속 UI 작업은 별도로 진행하겠습니다.
