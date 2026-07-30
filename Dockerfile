FROM python:3.11

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# mysqlclient 빌드에 필요한 시스템 패키지 (+ 헬스체크용 netcat)
RUN apt-get update && apt-get install -y --no-install-recommends \
    default-libmysqlclient-dev \
    pkg-config \
    gcc \
    netcat-traditional \
    && rm -rf /var/lib/apt/lists/*

# 의존성 목록은 /app 에 남기지 않는다. 설치 후 바로 삭제해
# 컨테이너 침해 시 사용 중인 패키지·버전이 그대로 노출되지 않게 한다.
COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt \
    && rm -f /tmp/requirements.txt

# 엔트리포인트는 /app 밖에 둔다. 앱 디렉터리에 배포 스크립트를 남기지 않는다.
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# ---------------------------------------------------------------------------
# 런타임에 실제로 필요한 것만 복사한다. `COPY . .` 금지.
#   - Dockerfile / docker-compose*.yml / docker/ : 인프라 구성 노출
#   - README.MD / docs/ / .github/               : 내부 구조·배포 파이프라인 노출
#   - .env / *.pem / db.sqlite3                  : 시크릿·인증서·데이터 노출
#   - deliver_app/ / scripts/ / .venv/           : 런타임에 불필요
# 새 최상위 디렉터리를 추가했다면 여기에 COPY 를 한 줄 더해야 이미지에 들어간다.
# ---------------------------------------------------------------------------
COPY manage.py ./
COPY config/ ./config/
COPY apps/ ./apps/

EXPOSE 8000

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["python", "manage.py", "runserver", "0.0.0.0:8000"]
