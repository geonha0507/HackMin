# HackMin 배포 가이드

GitHub Actions → GHCR → EC2 로 이어지는 CI/CD 파이프라인 셋업 문서.

```
 PR/push ──▶ validate ──▶ build ──▶ deploy ──▶ healthcheck
            Django 검증   GHCR push   EC2 SSH    3개 포트 확인
```

| 트리거 | validate | build | GHCR push | deploy |
|---|:--:|:--:|:--:|:--:|
| PR → main | O | O | X | X |
| push → main | O | O | O | O |
| 수동 실행 (태그 없이) | O | O | O | O |
| 수동 실행 (태그 지정 = 롤백) | O | 건너뜀 | X | O |

---

## 1. EC2 준비 (최초 1회)

### 1-1. 배포 전용 유저 생성

`ubuntu`/`ec2-user` 키를 그대로 CI에 넣지 말고, 배포만 가능한 유저를 따로 만든다.

```bash
# EC2 에 기존 방식으로 접속한 뒤
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy          # docker 소켓 접근 권한
sudo install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
sudo install -d -m 755 -o deploy -g deploy /opt/hackmin
```

> `docker` 그룹은 사실상 root 권한과 같다. 이 유저의 키가 유출되면 서버 전체가
> 노출되므로 아래 `command=` 제한과 키 분리를 반드시 함께 적용할 것.

### 1-2. 배포용 SSH 키 생성

**로컬 PC**에서 이 배포에만 쓸 키를 새로 만든다. 기존 개인 키를 재사용하지 않는다.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/hackmin_deploy -N "" -C "github-actions-deploy"
```

공개키를 서버의 `deploy` 유저에 등록한다.

```bash
# 로컬에서 공개키 내용 확인
cat ~/.ssh/hackmin_deploy.pub

# EC2 에서 (위 출력을 붙여넣기)
sudo -u deploy tee -a /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

### 1-3. 호스트 키 지문 확보

중간자 공격 방지를 위해 서버의 호스트 키를 GitHub Secrets 에 고정한다.

```bash
ssh-keyscan -H <EC2_PUBLIC_IP> 2>/dev/null
```

출력 전체를 `EC2_KNOWN_HOSTS` 시크릿에 넣는다. (설정하지 않으면 워크플로우가
경고를 남기고 매번 지문을 새로 수집한다 — 동작은 하지만 권장하지 않는다.)

### 1-4. 방화벽 / 보안 그룹

| 포트 | 용도 | 공개 범위 |
|---|---|---|
| 22 | SSH 배포 | GitHub Actions IP 대역 또는 전체 (키 인증만 허용) |
| 80 / 443 | nginx | 전체 |
| 8000 | api 컨테이너 | nginx 만 (외부 차단 권장) |
| 8001 | web 컨테이너 | nginx 만 |
| 8002 | admin-web | **외부 차단** — compose 가 `127.0.0.1` 에만 바인딩 |

---

## 2. GitHub 설정

### 2-1. Secrets (Settings → Secrets and variables → Actions → Secrets)

| 이름 | 필수 | 설명 |
|---|:--:|---|
| `EC2_SSH_KEY` | O | 1-2에서 만든 **개인키 전문** (`-----BEGIN...` 부터 끝까지) |
| `DJANGO_SECRET_KEY` | O | 운영 시크릿 키. 아래 명령으로 생성 |
| `EC2_KNOWN_HOSTS` | 권장 | 1-3의 `ssh-keyscan` 출력 |
| `DB_PASSWORD` | RDS 사용 시 | MySQL 비밀번호 |
| `OPENAI_API_KEY` | 챗봇 사용 시 | OpenRouter/OpenAI 키 |
| `AWS_ACCESS_KEY_ID` | S3 사용 시 | |
| `AWS_SECRET_ACCESS_KEY` | S3 사용 시 | |

```bash
# DJANGO_SECRET_KEY 생성 — URL-safe 문자만 나와 .env 에 안전하다
python -c "import secrets; print(secrets.token_urlsafe(64))"
```

> **`get_random_secret_key()` 를 쓰지 않는 이유:** Django 기본 생성기는
> `!@#$%^&*(-_=+)` 를 포함하는데, `#` 이 섞이면 docker compose 의 `env_file`
> 파서가 그 뒤를 주석으로 잘라낸다. 위 `token_urlsafe` 는 `A-Za-z0-9_-` 만
> 쓰므로 이 문제가 없다.

> **주의:** 시크릿 값에 줄바꿈이나 `공백+#` 이 들어가면 조용히 잘린 채 배포된다.
> 워크플로우가 배포 전에 이를 검사해 명시적으로 실패시키지만, 붙여넣을 때 앞뒤
> 공백이 섞이지 않도록 확인할 것. (`EC2_SSH_KEY` 는 예외 — 여러 줄이 정상이며
> 이 검사 대상이 아니다.)

### 환경 파일이 두 개인 이유

서버 `/opt/hackmin` 에는 환경 파일이 두 개 생성된다.

| 파일 | 용도 | 시크릿 |
|---|---|:--:|
| `.env` | compose 파일의 `${HACKMIN_IMAGE}`, `${ADMIN_BIND}` 치환 | X |
| `app.env` | `env_file:` 로 컨테이너에 그대로 전달 | O |

compose 는 `.env` 값에 대해 변수 보간을 수행하므로 `$` 가 든 시크릿이 깨진다.
그래서 시크릿은 보간을 거치지 않는 `app.env` 로 분리했다. 수동으로 값을 고칠
때도 이 구분을 지켜야 한다.

### 2-2. Variables (같은 화면의 Variables 탭)

시크릿이 아닌 설정값. 없으면 괄호 안 기본값이 적용된다.

| 이름 | 기본값 | 설명 |
|---|---|---|
| `EC2_HOST` | **(필수)** | EC2 퍼블릭 IP 또는 도메인 |
| `EC2_USER` | `deploy` | SSH 유저 |
| `EC2_PORT` | `22` | SSH 포트 |
| `APP_DIR` | `/opt/hackmin` | 서버 배포 경로 |
| `DJANGO_ENV` | `dev` | `prod` 전환은 4장 참고 |
| `DJANGO_DEBUG` | `1` | |
| `DJANGO_CSRF_ORIGINS` | (빈값) | `https://1.2.3.4,http://1.2.3.4` |
| `DJANGO_ALLOWED_HOSTS` | (빈값) | prod 프로파일에서만 사용 |
| `DJANGO_CORS_ORIGINS` | (빈값) | prod 프로파일에서만 사용 |
| `DJANGO_SSL_REDIRECT` | `0` | |
| `DB_ENGINE` | `sqlite` | RDS 쓰면 `mysql` |
| `DB_NAME` / `DB_USER` / `DB_HOST` / `DB_PORT` | `hackmin` / `hackmin` / (빈값) / `3306` | |
| `USE_S3` | `0` | |
| `AWS_S3_BUCKET_NAME` | `hackmin-media-bucket` | |
| `AWS_S3_REGION_NAME` | `ap-northeast-2` | |
| `SEED_DEMO` | `0` | 데모 계정 재생성이 필요할 때만 `1` |
| `ADMIN_BIND` | `127.0.0.1` | admin-web 바인딩 주소 |

### 2-3. Environment (선택)

Settings → Environments → `production` 생성 후 **Required reviewers** 를 지정하면
배포 직전에 수동 승인 단계가 생긴다. main 에 실수로 머지해도 서버에 바로 나가지
않으므로 설정을 권장한다.

### 2-4. 패키지 공개 여부

첫 배포 후 `https://github.com/geonha0507/HackMin/pkgs/container/hackmin` 에
이미지가 생긴다. 기본은 private 이며 워크플로우가 `GITHUB_TOKEN` 으로 로그인해
pull 하므로 그대로 두어도 된다.

---

## 3. 첫 배포

1. 위 Secrets / Variables 를 모두 등록한다.
2. 이 브랜치를 `main` 에 머지한다.
3. Actions 탭에서 `CI/CD` 실행을 확인한다.

첫 실행은 이미지 캐시가 없어 5~10분 정도 걸린다. 이후에는 레이어 캐시 덕에
2~3분 수준으로 줄어든다.

### 기존 배포에서 넘어오는 경우

지금까지 서버에서 `git pull` + `docker compose up` 으로 운영했다면, 기존
`db_data` 볼륨은 그대로 재사용된다. 다만 **media 파일 위치가 바뀐다.**
기존에는 바인드마운트(`.:/app`)라 호스트 경로에 있었지만, 이제 `media_data`
named volume 을 쓴다. 첫 배포 전에 옮겨둘 것:

```bash
# EC2 에서 — 기존 코드 디렉터리 경로에 맞춰 수정
docker volume create hackmin_media_data
docker run --rm -v /path/to/old/media:/from -v hackmin_media_data:/to \
  alpine sh -c "cp -a /from/. /to/"
```

---

## 4. dev → prod 프로파일 전환

현재 기본값은 `DJANGO_ENV=dev` 다. 지금 동작을 그대로 유지하기 위한 선택이며,
`prod` 로 올리면 다음이 함께 바뀐다.

| 항목 | dev | prod |
|---|---|---|
| `DEBUG` | 1 | 강제 0 |
| `ALLOWED_HOSTS` | `['*']` | `DJANGO_ALLOWED_HOSTS` 값만 |
| CORS | 전체 허용 | `DJANGO_CORS_ORIGINS` 화이트리스트 |
| 쿠키 | 평문 허용 | `Secure` 강제 |
| HSTS | 없음 | 1년 |
| 정적 파일 | runserver 가 서빙 | **서빙 안 함** |

전환 전 체크리스트:

- [ ] HTTPS 종단이 준비되어 있다 (nginx + 인증서)
- [ ] `DJANGO_ALLOWED_HOSTS` 에 도메인/IP 와 **`127.0.0.1` 을 포함**했다
      (compose healthcheck 가 루프백으로 접근하므로 빠지면 400 이 뜬다)
- [ ] `DJANGO_CSRF_ORIGINS` / `DJANGO_CORS_ORIGINS` 를 채웠다
- [ ] 정적 파일 서빙 대책이 있다 (아래 참고)

### 정적 파일 · WSGI 관련 남은 과제

현재 컨테이너는 `manage.py runserver` 로 뜬다. 개발 서버라 단일 스레드에
자동 리로더가 붙어 있어 운영 부하를 감당하지 못한다. `DEBUG=0` 이 되면
정적 파일도 서빙하지 않는다. 다음 단계로 권장:

```
requirements.txt 에  gunicorn==22.0.0  추가
Dockerfile 에        RUN python manage.py collectstatic --noinput  추가
compose command 를   gunicorn config.wsgi:application -b 0.0.0.0:8000 -w 3  로 교체
nginx 에             /static/ → staticfiles 볼륨 alias 추가
```

이번 파이프라인 작업 범위에는 포함하지 않았다. 런타임 동작이 바뀌어
별도로 검증이 필요하기 때문이다.

---

## 5. 롤백

### 방법 A — Actions 에서 이전 태그 재배포 (권장)

1. Actions → `CI/CD` → **Run workflow**
2. `image_tag` 에 되돌릴 커밋 SHA 입력 (예: `a1b2c3d...`, 40자 전체)
3. 실행 — 재빌드 없이 해당 이미지로 컨테이너만 교체된다

사용 가능한 태그는 리포지토리 Packages 탭에서 확인할 수 있다.

### 방법 B — 서버에서 직접

```bash
cd /opt/hackmin
# 이미지만 되돌리면 되므로 .env 의 HACKMIN_IMAGE 만 바꾼다
sed -i "s|^HACKMIN_IMAGE=.*|HACKMIN_IMAGE=ghcr.io/geonha0507/hackmin:<SHA>|" .env
docker compose -f docker-compose.prod.yml up -d

# 설정까지 통째로 되돌리려면
cp .env.bak .env && cp app.env.bak app.env
docker compose -f docker-compose.prod.yml up -d
```

배포 스크립트가 매번 `.env.bak` / `app.env.bak` / `docker-compose.prod.yml.bak`
으로 직전 세대를 한 벌 남긴다.

---

## 6. 트러블슈팅

**`Permission denied (publickey)`**
`EC2_SSH_KEY` 에 개인키 전문이 들어갔는지(`.pub` 아님), `authorized_keys` 권한이
600 인지 확인.

**`denied: permission_denied` (GHCR push)**
워크플로우의 `permissions: packages: write` 가 있는지, 조직 설정에서 Actions 의
패키지 쓰기가 막혀있지 않은지 확인.

**헬스체크는 통과하는데 브라우저에서 502**
nginx 가 컨테이너 포트로 프록시하는지 확인. compose 포트 매핑(8000/8001/8002)은
그대로 유지했으므로 기존 nginx 설정을 바꿀 필요는 없다.

**`admin-web` 에 접근이 안 됨**
의도된 동작이다. `127.0.0.1:8002` 에만 바인딩되어 nginx 경유로만 접근된다.
일시적으로 열려면 Variables 에 `ADMIN_BIND=0.0.0.0` 을 두고 재배포.

**마이그레이션 실패로 배포가 멈춤**
`api` 컨테이너만 마이그레이션을 돌리므로 실패 시 `web`/`admin-web` 은 아예
기동하지 않는다(이전 버전이 계속 서비스 중). 로그 확인:

```bash
cd /opt/hackmin && docker compose -f docker-compose.prod.yml logs --tail=100 api
```

**로컬에서 배포 스크립트만 다시 돌리고 싶을 때**

```bash
cd /opt/hackmin
STAGE_DIR=/tmp/hackmin-deploy APP_DIR=/opt/hackmin bash scripts/deploy_remote.sh
```
