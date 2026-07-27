#!/usr/bin/env bash
#
# EC2 위에서 실행되는 배포 스크립트. GitHub Actions 가 이 파일과 함께
# docker-compose.prod.yml / app.env / compose.env / ghcr.token 을 스테이징
# 디렉터리로 전송한 뒤 호출한다.
#
#   compose.env → .env   로 설치. compose 의 ${...} 보간에만 쓴다 (시크릿 없음).
#   app.env     → app.env 로 설치. env_file 로 컨테이너에 그대로 전달된다.
#
# 수동 실행 예:
#   APP_DIR=~/hackmin STAGE_DIR=/tmp/hackmin-deploy bash deploy_remote.sh
#
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/hackmin}"
STAGE_DIR="${STAGE_DIR:-/tmp/hackmin-deploy}"
COMPOSE_FILE="docker-compose.prod.yml"
RDS_CA_URL="https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem"

log() { printf '\n\033[1m▶ %s\033[0m\n' "$*"; }

# 전송된 파일이 모두 도착했는지 먼저 확인한다.
for f in "$COMPOSE_FILE" app.env compose.env; do
  [ -f "$STAGE_DIR/$f" ] || { echo "ERROR: $STAGE_DIR/$f 가 없습니다." >&2; exit 1; }
done

# 스테이징 파일은 성공/실패와 무관하게 반드시 지운다 (토큰·시크릿 포함).
cleanup() { rm -rf "$STAGE_DIR"; }
trap cleanup EXIT

log "배포 디렉터리 준비: $APP_DIR"
mkdir -p "$APP_DIR"
cd "$APP_DIR"

# 이전 세대를 한 벌 보관해 두면 수동 롤백이 쉬워진다.
[ -f "$COMPOSE_FILE" ] && cp "$COMPOSE_FILE" "$COMPOSE_FILE.bak"
[ -f .env ] && cp .env .env.bak
[ -f app.env ] && cp app.env app.env.bak

umask 077
# .env  = compose 보간용 (HACKMIN_IMAGE, HACKMIN_WEB_IMAGE, ADMIN_BIND)
# app.env = 컨테이너 전달용 앱 설정/시크릿
install -m 600 "$STAGE_DIR/compose.env" .env
install -m 600 "$STAGE_DIR/app.env" app.env
install -m 644 "$STAGE_DIR/$COMPOSE_FILE" "$COMPOSE_FILE"

# RDS 강제 SSL 을 쓰면 CA 번들이 컨테이너에 마운트돼야 한다. AWS 공개 번들이라
# 시크릿이 아니므로 없으면 여기서 내려받는다. 파일이 없는 채로 compose 가 뜨면
# 도커가 같은 이름의 '빈 디렉터리'를 만들어 원인 찾기 어려운 TLS 오류가 난다.
if grep -q '^DB_SSL_CA=..*' app.env; then
  if [ ! -f global-bundle.pem ]; then
    log "RDS CA 번들 내려받기"
    curl -fsS -o global-bundle.pem "$RDS_CA_URL"
  fi
  [ -f global-bundle.pem ] || { echo "ERROR: global-bundle.pem 준비 실패" >&2; exit 1; }
  chmod 644 global-bundle.pem
fi

IMAGE_REF="$(grep -E '^HACKMIN_IMAGE=' .env | cut -d= -f2-)"
WEB_IMAGE_REF="$(grep -E '^HACKMIN_WEB_IMAGE=' .env | cut -d= -f2-)"
log "배포 이미지"
echo "  api/admin-web: ${IMAGE_REF:-<미지정>}"
echo "  점주 웹      : ${WEB_IMAGE_REF:-<미지정>}"
[ -n "$WEB_IMAGE_REF" ] || { echo "ERROR: HACKMIN_WEB_IMAGE 가 .env 에 없습니다." >&2; exit 1; }

# GHCR 이 비공개 패키지인 경우에만 로그인한다. 토큰은 stdin 으로만 흘려보내
# 원격 프로세스 목록(ps)에 남지 않게 한다.
if [ -f "$STAGE_DIR/ghcr.token" ]; then
  log "GHCR 로그인"
  docker login ghcr.io -u "${GHCR_USER:-x-access-token}" --password-stdin < "$STAGE_DIR/ghcr.token"
fi

log "이미지 pull"
docker compose -f "$COMPOSE_FILE" pull

# api 컨테이너만 RUN_MIGRATIONS=1 이라 마이그레이션을 담당한다.
# 먼저 띄워서 스키마를 확정한 뒤 나머지를 올린다.
log "api 기동 (마이그레이션 수행)"
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans api

log "api 헬스 대기"
for i in $(seq 1 30); do
  if curl -fsS --max-time 3 http://127.0.0.1:8000/api/v1/health 2>/dev/null | grep -q '"status"'; then
    echo "  api 정상 (${i}회차)"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERROR: api 헬스체크 실패. 최근 로그:" >&2
    docker compose -f "$COMPOSE_FILE" logs --tail=80 api >&2
    exit 1
  fi
  sleep 4
done

log "redis / web / admin-web 기동"
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans

log "점주 웹 헬스 대기"
for i in $(seq 1 15); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 \
           http://127.0.0.1:8001/web/health || echo 000)
  if [ "$code" = "200" ]; then
    echo "  점주 웹 정상 (${i}회차)"
    break
  fi
  if [ "$i" -eq 15 ]; then
    echo "ERROR: 점주 웹 헬스체크 실패 (HTTP $code). 최근 로그:" >&2
    docker compose -f "$COMPOSE_FILE" logs --tail=80 web >&2
    exit 1
  fi
  sleep 3
done

if [ -f "$STAGE_DIR/ghcr.token" ]; then
  docker logout ghcr.io >/dev/null 2>&1 || true
fi

log "미사용 이미지 정리"
docker image prune -f >/dev/null

log "현재 상태"
docker compose -f "$COMPOSE_FILE" ps
