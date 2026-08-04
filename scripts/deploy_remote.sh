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
# .env  = compose 보간용 (HACKMIN_IMAGE, ADMIN_BIND)
# app.env = 컨테이너 전달용 앱 설정/시크릿
install -m 600 "$STAGE_DIR/compose.env" .env
install -m 600 "$STAGE_DIR/app.env" app.env
install -m 644 "$STAGE_DIR/$COMPOSE_FILE" "$COMPOSE_FILE"

IMAGE_REF="$(grep -E '^HACKMIN_IMAGE=' .env | cut -d= -f2-)"
log "배포 이미지: ${IMAGE_REF:-<미지정>}"

# GHCR 이 비공개 패키지인 경우에만 로그인한다. 토큰은 stdin 으로만 흘려보내
# 원격 프로세스 목록(ps)에 남지 않게 한다.
if [ -f "$STAGE_DIR/ghcr.token" ]; then
  log "GHCR 로그인"
  docker login ghcr.io -u "${GHCR_USER:-x-access-token}" --password-stdin < "$STAGE_DIR/ghcr.token"
fi

log "이미지 pull"
docker compose -f "$COMPOSE_FILE" pull

# Redis 를 먼저 올린다 (Channels channel layer + 세션).
log "Redis 기동"
docker compose -f "$COMPOSE_FILE" up -d redis

log "Redis 헬스 대기"
for i in $(seq 1 15); do
  if docker compose -f "$COMPOSE_FILE" exec -T redis redis-cli ping 2>/dev/null | grep -q PONG; then
    echo "  Redis 정상 (${i}회차)"
    break
  fi
  if [ "$i" -eq 15 ]; then
    echo "ERROR: Redis 헬스체크 실패." >&2
    docker compose -f "$COMPOSE_FILE" logs --tail=30 redis >&2
    exit 1
  fi
  sleep 2
done

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

log "web / admin-web 기동"
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans

# Splunk UF 가 서비스별로 컨테이너 로그를 구분하도록 심볼릭 링크를 건다.
# 컨테이너 ID 는 재배포마다 바뀌므로 매 배포 시 갱신한다.
# 관제 파이프라인 문제로 배포가 실패하면 안 되므로 오류는 무시한다(|| true).
log "Splunk 로그 심볼릭 링크 갱신"
sudo mkdir -p /var/log/docker-symlink || true
for svc in api web admin-web; do
  cid="$(docker inspect --format '{{.Id}}' "hackmin-${svc}-1" 2>/dev/null || true)"
  if [ -n "$cid" ]; then
    sudo ln -sf "/var/lib/docker/containers/$cid/$cid-json.log" \
      "/var/log/docker-symlink/$svc.log" || true
    echo "  linked: $svc -> ${cid:0:12}"
  else
    echo "  skip: hackmin-${svc}-1 컨테이너를 찾지 못함"
  fi
done

if [ -f "$STAGE_DIR/ghcr.token" ]; then
  docker logout ghcr.io >/dev/null 2>&1 || true
fi

log "미사용 이미지 정리"
docker image prune -f >/dev/null

log "현재 상태"
docker compose -f "$COMPOSE_FILE" ps
