# HackMin CI/CD (GitHub Actions)

작성 2026-07-27 · `.github/workflows/ci-cd.yml`

main 브랜치에 푸시하면 검증 → 이미지 빌드 → EC2 배포까지 자동으로 진행된다. 수동 `docker compose` 배포는 더 이상 쓰지 않는다.

---

## 1. 전체 흐름

```
git push (main)
   ↓
[1] Django 검증        ── manage.py check / 마이그레이션 정합성
[2] 점주 웹 검증        ── manage_web.py check (DB 없이 뜨는지)
   ↓ 둘 다 통과해야
[3] 이미지 빌드         ── GHCR 로 push (이미지 2개)
   ↓
[4] EC2 배포           ── SSH 접속 → /opt/hackmin 에 교체
```

PR에서는 [1][2][3]까지만 돈다. 레지스트리 push도 배포도 하지 않는다.

---

## 2. 트리거

| 이벤트 | 동작 |
|---|---|
| `push` → main | 검증 + 빌드 + 푸시 + 배포 |
| `pull_request` → main | 검증 + 빌드만 (푸시·배포 없음) |
| `workflow_dispatch` | 수동 실행. `image_tag` 입력 시 재빌드 없이 롤백 |

동시 실행은 브랜치 단위로 직렬화된다. PR은 새 커밋이 오면 이전 실행을 취소하지만, main 배포 중에는 취소하지 않는다.

---

## 3. Job 상세

### [1] Django 검증

api 이미지에 들어갈 코드를 검사한다.

- `manage.py check` (dev 프로파일)
- `makemigrations --check --dry-run` — 모델은 고쳤는데 마이그레이션을 안 만든 경우를 잡는다
- `migrate --noinput` — 빈 SQLite에 실제로 적용되는지. 충돌·순환 의존성·깨진 RunPython을 여기서 거른다
- `check --deploy --fail-level ERROR` (prod 프로파일) — 경고는 통과, 에러만 실패

### [2] 점주 웹 검증

`requirements-web.txt`(Django·httpx·redis)만 설치하고 `manage_web.py check`를 돌린다.

**api 검증과 분리한 이유**: 같이 돌리면 `requirements.txt`가 함께 설치돼서 "DB 없이도 뜨는지"를 확인할 수 없다. `DATABASES = {}` 상태로 부팅되는지 검사하는 게 목적이라, ORM을 실수로 호출하는 코드가 들어가면 여기서 걸린다.

### [3] 이미지 빌드

이미지를 두 개 만든다.

| 이미지 | Dockerfile | 쓰는 곳 |
|---|---|---|
| `ghcr.io/geonha0507/hackmin` | `./Dockerfile` | api, admin-web |
| `ghcr.io/geonha0507/hackmin-web` | `./docker/Dockerfile.web` | 점주 웹 |

태그는 커밋 SHA와 `latest` 두 개가 붙는다. buildx 캐시는 `scope`로 분리해서 서로 간섭하지 않는다.

### [4] EC2 배포

리포지토리 변수 `DEPLOY_ENABLED=true` 일 때만 실행된다. 없으면 실패가 아니라 **skipped**로 지나간다.

순서:

1. 필수 설정값 확인 — 시크릿 누락을 먼저 걸러낸다
2. SSH 준비 — 개인키와 known_hosts 배치
3. 배포용 env 파일 생성 — `app.env`(컨테이너 전달용), `compose.env`(compose 보간용)
4. 배포 파일 전송 — scp로 스테이징 디렉터리에
5. `deploy_remote.sh` 실행
6. 헬스체크
7. 자격증명 정리

실패하면 컨테이너 로그 100줄을 자동으로 찍는다.

---

## 4. GitHub 설정값

`Settings → Secrets and variables → Actions`

### Secrets (값이 안 보임)

| 이름 | 내용 |
|---|---|
| `EC2_SSH_KEY` | 배포 전용 SSH 개인키 전문. passphrase 없어야 함 |
| `EC2_KNOWN_HOSTS` | `ssh-keyscan -H <IP>` 출력. 없으면 호스트 키 검증 없이 진행(경고) |
| `DJANGO_SECRET_KEY` | api·admin-web용 |
| `WEB_BFF_SECRET_KEY` | 점주 웹용. **api와 다른 값** |
| `DB_PASSWORD` | RDS 비밀번호 |
| `OPENAI_API_KEY` | 챗봇용 (선택) |

**AWS 자격증명은 넣지 않는다.** EC2 인스턴스에 IAM 역할이 붙어 있어 boto3가 메타데이터에서 임시 자격증명을 받는다.

### Variables (값이 보임)

| 이름 | 값 |
|---|---|
| `DEPLOY_ENABLED` | `true` — 배포 스위치 |
| `EC2_HOST` | `54.116.95.188` |
| `EC2_USER` | `ec2-user` (기본값이 `deploy`라 반드시 지정) |
| `DJANGO_ALLOWED_HOSTS` | `54.116.95.188` |
| `DJANGO_CSRF_ORIGINS` | `https://54.116.95.188,http://54.116.95.188` |
| `WEB_BFF_CSRF_ORIGINS` | 위와 동일 |
| `DB_ENGINE` | `mysql` |
| `DB_HOST` | RDS 엔드포인트 |
| `DB_NAME` / `DB_USER` / `DB_PORT` | `hackmin` / `hackmin` / `3306` |
| `DB_SSL_CA` | `/app/certs/rds-ca.pem` |
| `DJANGO_DEBUG` | `0` |
| `SEED_DEMO` | `1` |
| `USE_S3` | `1` |
| `AWS_S3_BUCKET_NAME` | `hackmin-media-bucket` |
| `AWS_S3_REGION_NAME` | `ap-northeast-2` |

시크릿·변수 이름은 워크플로가 그대로 참조하므로 **대소문자와 언더바까지 정확히** 맞아야 한다.

---

## 5. EC2 준비 사항

| 항목 | 확인 |
|---|---|
| 배포 계정 | `EC2_SSH_KEY`의 공개키가 `~/.ssh/authorized_keys`에 등록 |
| docker 권한 | `sudo` 없이 `docker ps` 가능 (docker 그룹) |
| 배포 디렉터리 | `/opt/hackmin` 존재, 배포 계정 소유 |
| 포트 | 8000·8001·8002를 다른 스택이 쓰지 않을 것 |

RDS CA 번들(`global-bundle.pem`)은 `deploy_remote.sh`가 없으면 자동으로 내려받는다.

---

## 6. 배포 후 상태

```
/opt/hackmin/                 ← 운영 스택 (여기가 진짜)
  docker-compose.prod.yml
  .env                        ← 이미지 태그 등 (시크릿 없음)
  app.env                     ← 앱 설정·시크릿
  global-bundle.pem
```

`~/HackMin` 은 소스 체크아웃일 뿐이다. **여기서 `docker compose up` 하면 포트가 충돌한다.** 항상 내려둘 것.

컨테이너 4개가 뜬다.

| 컨테이너 | 이미지 | 포트 |
|---|---|---|
| `hackmin-api-1` | hackmin | `0.0.0.0:8000` |
| `hackmin-web-1` | hackmin-web | `0.0.0.0:8001` |
| `hackmin-admin-web-1` | hackmin | `127.0.0.1:8002` |
| `hackmin-redis-1` | redis:7-alpine | 내부 전용 |

---

## 7. 운영

### 롤백

Actions → CI/CD → Run workflow → `image_tag`에 이전 커밋 SHA 입력. 재빌드 없이 그 태그로 교체된다.

### 배포 끄기

Variables의 `DEPLOY_ENABLED`를 `false`로 바꾸거나 삭제. 검증·빌드는 계속 돌고 배포만 skipped.

### 상태 확인

```bash
cd /opt/hackmin
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f web
```

---

## 8. 구축하면서 막혔던 것들

같은 함정을 다시 밟지 않기 위한 기록.

### 빈 문자열은 "미설정"이 아니다

워크플로는 값이 없는 항목도 `KEY=` 형태로 `app.env`에 써 넣는다. 그러면 컨테이너 안에서 그 변수는 **존재하지만 비어 있는** 상태가 된다.

`os.environ.get('X', '기본값')` 은 이 경우 기본값을 쓰지 않고 빈 문자열을 돌려준다. 그래서 `ALLOWED_HOSTS`가 빈 리스트가 되고 `DEBUG=False`에서 부팅이 실패했다.

→ `web_bff/settings.py`의 `_env()` 헬퍼가 빈 값을 미설정으로 취급한다.

### AWS 자격증명을 빈 값으로 넘기면 IAM 역할이 무시된다

botocore는 `AWS_ACCESS_KEY_ID` 변수가 **존재하기만 하면** 그 값을 쓰려 하고, 비어 있으면 에러를 낸다. 인스턴스 메타데이터로 넘어가지 않는다.

→ `APP_KEYS`에서 AWS 키 두 개를 제외했다.

### 운영 compose는 `env_file`만 읽는다

개발 compose는 `DJANGO_SECRET_KEY: ${WEB_BFF_SECRET_KEY}` 로 이름을 바꿔 넘겨줬는데, 운영 compose에는 그 매핑이 없었다. 결과적으로 점주 웹이 **api의 SECRET_KEY와 CSRF 오리진을 그대로 쓰고 있었다.** 컨테이너를 분리한 의미가 사라진다.

→ settings가 `WEB_BFF_*`를 먼저 읽고 없으면 `DJANGO_*`로 떨어지도록 변경.

### CSRF_TRUSTED_ORIGINS는 스킴까지 맞아야 한다

nginx가 HTTPS를 종단하므로 브라우저가 보내는 Origin은 `https://54.116.95.188`이다. `http://...:8001`만 등록해두면 로그인 POST가 403으로 막힌다. 포트도 붙이면 안 된다(443이라 생략되므로).

### 같은 경로를 두 번 등록하면 뒤엣것이 죽는다

`urls.py`에 `owner/restaurants`를 GET용·POST용으로 각각 등록했더니 Django가 앞의 것만 매칭해 POST가 405로 떨어졌다. 한 뷰에서 메서드로 분기해야 한다.

---

## 9. 남은 과제

- `apps/admin_web`이 아직 ORM으로 DB에 직결한다. 현재 구조에서 가장 큰 노출면
- api와 admin-web이 같은 DB 계정을 쓴다. 서비스별 권한 분리 필요
- 8000·8001 포트가 외부에 직접 열려 있다. nginx만 거치도록 닫으면 `USE_X_FORWARDED_PROTO`도 안전하게 켤 수 있다
- `SEED_DEMO=1` — 배포마다 데모 계정(`pw1234`)이 RDS에 생성된다
