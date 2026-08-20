# 해킹커넥트 (rider_app)

해킹의 민족(deliver_app)과 같은 회사가 만든 **라이더 전용 앱**. 배민커넥트 포지션의
패밀리 앱으로, 디자인 언어(코랄 #FF6F61 브랜딩·컴포넌트 스타일)와 백엔드를
deliver_app과 공유한다.

- 패키지: `com.hackmin.connect`
- 백엔드: `https://hackmin.com/api/v1/` (deliver_app과 동일 서버·동일 페이로드 암호화)
- 사용 API: `/auth/signup|login|logout|refresh`, `/auth/check-duplicate`, `/me`,
  `/rider/deliveries*`, `/rider/location`(실시간 위치 relay, 신규)

### 서버 변경(배포 필요)

이 앱이 서버와 실제로 통신하려면 아래 백엔드 변경이 hackmin.com에 배포돼야 한다
(브랜치 머지 → CI/CD). 로컬 테스트는 통과(`manage.py test rider` 7/7).

- `apps/accounts/serializers.py` — 회원가입에서 `role=rider` 허용(admin은 계속 거부)
- `apps/rider/models.py` — `RiderLocation`(라이더별 현재 좌표, OneToOne) 신규 + 마이그레이션 `0002`
- `apps/rider/views.py`·`urls.py` — `GET/PUT /rider/location`(IsRider). PUT은 upsert, GET은 없으면 204
- `apps/rider/tests.py` — 가입/위치 relay 테스트

## 화면 구성 (배민커넥트 스타일 4탭)

| 탭 | 내용 |
|---|---|
| 홈 | 운행 시작/종료 토글, 오늘 완료 건수·배달 수입 요약, 신규 콜 안내 |
| 운행 | 콜 목록(전체/신규 콜/진행 중/완료 필터), 신규 콜 뱃지 |
| 수입 | 오늘/누적 수입 요약 + 완료 건 내역 (건당 3,500원 고정 단가, `DeliveryFee`) |
| 내정보 | 라이더 프로필(/me), 로그아웃 |

홈의 **내 위치** 카드는 운행 시작 시 실시간 GPS(`LocationTracker`, LocationManager
GPS+네트워크 2초/3m 구독)를 받아 라이브 좌표·주소를 표시한다. 화면을 벗어나면
(onPause) 구독을 멈춰 배터리를 아끼고, 운행 종료 시 추적을 끈다. 역지오코딩(Geocoder)
주소는 네트워크가 되는 실기기에서 채워지며, 에뮬레이터에선 좌표만 표시될 수 있다.

배달 상세에서 상태를 한 단계씩 진행한다:
`신규 콜(assigned) → 픽업 완료(picked_up) → 배달 시작(delivering) → 배달 완료(delivered)`
(미배정 콜은 첫 상태 변경 시 서버가 본인에게 배정)

## 계정

로그인 화면의 **라이더 지원(회원가입)** 으로 `role=rider` 계정을 만든다
(같은 `/auth/signup`에 `role: "rider"` 를 보냄 — `apps/accounts/serializers.py`
`validate_role`이 rider를 허용하도록 확장됨. **서버에 이 변경이 배포돼야 가입이 된다**).
앱은 로그인 응답의 role이 `rider`가 아니면 세션을 만들지 않고 거부한다.

## 빌드 & 배포

Java 21(Android Studio JBR) 필요 — 머신 기본 JAVA_HOME(25)로는 JdkImageTransform이 실패한다.

```powershell
# 빌드 + 디버그 키 서명 + LDPlayer 설치 + 실행
powershell -ExecutionPolicy Bypass -File rider_app\deploy_ldplayer.ps1 -Launch
```

서버가 `PAYLOAD_ENFORCE=1`(강제 모드)이면 gitignore된 `gradle.properties`에
`payloadHmacSecret=<서버 PAYLOAD_APP_HMAC_SECRET>` 를 넣고 빌드해야 X-Sig 서명이 붙는다.
듀얼 모드(기본)에서는 시크릿 없이도 통신된다.
