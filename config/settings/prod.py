"""운영(prod) 설정 — 보안 하드닝. DJANGO_ENV=prod 로 활성화한다.

랩용 느슨한 기본값(디버그/와일드카드 호스트/전체 CORS/하드코딩 시크릿)을
운영 안전값으로 교체하고, 민감 값은 환경변수로 주입받는다.
"""

import os

from .base import *  # noqa: F401,F403

# 시크릿 키는 필수. 미설정 시 부팅을 실패시켜 조기에 차단한다.
SECRET_KEY = os.environ.get('DJANGO_SECRET_KEY')
if not SECRET_KEY:
    from django.core.exceptions import ImproperlyConfigured
    raise ImproperlyConfigured('운영 환경에서는 DJANGO_SECRET_KEY 환경변수가 필요합니다.')

DEBUG = False

# 콤마로 구분한 허용 호스트. 예: "api.hackmin.com,hackmin.com"
ALLOWED_HOSTS = [h.strip() for h in os.environ.get('DJANGO_ALLOWED_HOSTS', '').split(',') if h.strip()]

# CORS 화이트리스트. 예: "https://hackmin.com,https://admin.hackmin.com"
CORS_ALLOW_ALL_ORIGINS = False
CORS_ALLOWED_ORIGINS = [o.strip() for o in os.environ.get('DJANGO_CORS_ORIGINS', '').split(',') if o.strip()]

# 전송/쿠키 보안 (HTTPS 종단 기준).
SESSION_COOKIE_SECURE = True
CSRF_COOKIE_SECURE = True
SECURE_SSL_REDIRECT = os.environ.get('DJANGO_SSL_REDIRECT', '1') == '1'
SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')
SECURE_HSTS_SECONDS = int(os.environ.get('DJANGO_HSTS_SECONDS', '31536000'))
SECURE_HSTS_INCLUDE_SUBDOMAINS = True
SECURE_HSTS_PRELOAD = True
