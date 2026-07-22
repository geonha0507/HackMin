# 페이로드 하이브리드 암호화 (앱 ↔ 백엔드)

Burp 같은 네트워크 프록시가 요청/응답 본문을 읽지 못하도록, **전송계층(TLS)이 아니라
애플리케이션 계층에서** 본문을 암호화한다. 클라이언트가 소켓에 쓰기 전에 이미 암호문이
되므로, 사용자 CA로 TLS를 MITM 해도 본문은 암호문으로만 보인다.

## 스킴: RSA-OAEP-SHA256 + AES-256-GCM (하이브리드)

```
[요청]
  앱: 랜덤 AES-256 세션키 K 생성(요청마다 새로)
      본문(JSON)을 AES-256-GCM(K, 12B IV)로 암호화 → {iv, data}
      K를 서버 공개키로 RSA-OAEP 암호화 → 헤더 X-Enc-Key
  서버: 개인키로 K 복원 → 본문 복호화 → 뷰 처리

[응답]
  서버: 같은 K로 응답 JSON을 AES-256-GCM 암호화 → {iv, data}, 헤더 X-Enc: 1
  앱:   K(요청 때 그 K)로 복호화 → 평문 JSON을 Retrofit에 전달
```

네트워크에 나가는 것:
- 요청 헤더 `X-Enc-Key: base64(RSA-OAEP(K))`
- 요청/응답 본문 `{"iv":"…","data":"…"}` (base64, `data = ciphertext||tag`)

**Burp엔 왜 안 잡히나**: 서버 개인키가 없으면 `X-Enc-Key`에서 K를 복원할 수 없어
요청·응답 본문을 모두 복호화할 수 없다. 공개키가 노출돼도(원래 공개) 무의미.

## 파라미터 (앱·서버 정확히 일치)

| 항목 | 값 |
|---|---|
| 키 교환 | `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` (hash SHA-256, **MGF1 SHA-256**) |
| 본문 | `AES/GCM/NoPadding`, 256-bit key, **12-byte IV(랜덤, 재사용 금지)**, 128-bit tag |
| tag | ciphertext 뒤에 부착 (`data = ct||tag`) |
| base64 | 표준(비 URL-safe), 개행 없음 |

> ⚠️ Java 기본 OAEP는 MGF1이 SHA-1인 경우가 있어, 앱에서 `OAEPParameterSpec`으로
> MGF1=SHA-256을 **명시**한다. (양쪽 SHA-256로 통일)

## 구성요소

**백엔드**
- `apps/common/crypto.py` — RSA unwrap / AES-GCM enc·dec 헬퍼
- `apps/common/enc.py` — `EncryptedJSONParser`(요청 복호화) + `EncryptedJSONRenderer`(응답 암호화)
- `config/settings/base.py` — DRF 기본 parser/renderer 지정 + 개인키 로딩
- **듀얼 모드**: `X-Enc-Key` 헤더가 없으면 평범한 JSON으로 폴백(비암호화 클라이언트·테스트 호환)

**앱**
- `network/PayloadCrypto.java` — 서버 공개키 임베드 + RSA-OAEP/AES-GCM
- `network/CryptoInterceptor.java` — OkHttp 인터셉터(요청 암호화 / 응답 복호화)
- `network/ApiClient.java` — 인터셉터를 **체인 마지막**에 등록(소켓에 가장 가깝게)

## 키 관리

- **공개키**: `keys/payload_public.pem` (커밋됨) — 앱 `PayloadCrypto.SERVER_PUBLIC_KEY_B64`와 동일 키
- **개인키**: `keys/payload_private_dev.pem` — **gitignore(커밋 금지)**. 서버만 보관
- 서버는 다음 우선순위로 개인키를 읽는다:
  1. 환경변수 `PAYLOAD_PRIVATE_KEY_PEM` (PEM 전체 내용) ← **프로덕션 권장**
  2. 파일 `keys/payload_private_dev.pem` (로컬 개발용)

### 키 재생성
```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/payload_private_dev.pem
openssl rsa -in keys/payload_private_dev.pem -pubout -out keys/payload_public.pem
# 앱 상수 갱신용 단일라인 base64:
openssl pkey -pubin -in keys/payload_public.pem -inform PEM -outform DER | openssl base64 -A
```
> 개인키를 재생성하면 공개키도 바뀌므로 앱 `SERVER_PUBLIC_KEY_B64`도 반드시 갱신해야 한다.

## 배포

**앱·백엔드를 동시에 배포**해야 한다(한쪽만 켜면 통신이 깨진다).

- 백엔드(Docker/EC2): 개인키를 환경변수로 주입
  ```bash
  # docker run 예
  -e PAYLOAD_PRIVATE_KEY_PEM="$(cat keys/payload_private_dev.pem)"
  # docker-compose: environment 에 PAYLOAD_PRIVATE_KEY_PEM 추가
  ```
  (dev 키 파일은 gitignore라 이미지에 포함되지 않음 → 반드시 env 주입)
- 의존성: `requirements.txt`에 `cryptography` 추가됨 → `pip install -r requirements.txt`

## 켜고 끄기 (Burp A/B 테스트)

`CryptoInterceptor.ENABLED = false` 로 두고 재빌드하면 평문 통신이 된다.
`true`(기본)면 암호화. Burp에서 on/off를 비교해 효과를 확인할 수 있다.

## 검증 완료

- 크립토 라운드트립(동일 파라미터) ✓
- 임베드 공개키 = 키파일 (sha256 일치) ✓
- 실제 DRF `EncryptedJSONParser`/`Renderer` 통합: 요청 복호화 / 응답 암호화 / GET / 폴백 ✓
- 앱 `compileDebugJavaWithJavac` BUILD SUCCESSFUL ✓
- 남은 것: docker + 실기기(에뮬)로 전체 스택 스모크 테스트

## 알려진 한계 (향후 과제)

- **헤더·URL·요청 크기·타이밍은 여전히 노출**. `Authorization: Bearer` 토큰도 헤더라
  Burp에 보인다. 숨기려면 토큰/경로를 봉투 안으로 옮기고 단일 `/secure` 엔드포인트로 라우팅.
- **재전송 방지 미구현**. `timestamp`+`nonce` + 서버 시간창 검증 추가 권장.
- **리버싱 방어는 아님**. 루팅+Frida로 암호화 직전 평문을 후킹할 수 있다. 이 방식은
  네트워크 프록시(Burp) 대응이지 기기 장악 공격까지 막지는 못한다.
- **TLS(HTTPS)와 병행 권장**. 페이로드 암호화가 TLS를 대체하지 않는다(이중 방어).
- 릴리스 빌드에선 OkHttp `HttpLoggingInterceptor` 레벨을 낮출 것(logcat 평문 방지).
