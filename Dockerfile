# API 이미지 — 공통 베이스(hackmin/base-api)에 앱 코드만 얹는다.
#
# 시스템 패키지와 파이썬 의존성은 docker/Dockerfile.base 로 옮겼다.
# requirements.txt 를 고쳤다면 베이스 이미지를 먼저 갱신해야 반영된다.
FROM 593519865637.dkr.ecr.ap-northeast-2.amazonaws.com/hackmin/base-api:python3.11

WORKDIR /app

COPY . .

RUN chmod +x /app/docker/entrypoint.sh

EXPOSE 8000

ENTRYPOINT ["/app/docker/entrypoint.sh"]
CMD ["daphne", "-b", "0.0.0.0", "-p", "8000", "config.asgi:application"]
