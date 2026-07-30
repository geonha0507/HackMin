"""admin_bff — 관리자 웹 전용 Django 설정.

web_bff 와 같은 원칙이다. **DB에 접속하지 않는다.**

  - `DATABASES = {}`  → DB 커넥션 자체가 없다.
  - 모델을 가진 로컬 앱이 INSTALLED_APPS 에 하나도 없다.
  - `django.contrib.auth` 를 쓰지 않는다. 인증은 /api/v1/auth/login 이 발급한
    JWT 를 세션에 보관하는 방식으로 대체한다 (bff_core/auth.py).
  - 세션은 Redis 캐시에 저장한다. 점주 웹과 섞이지 않도록 DB 인덱스를 나눈다.

관리자 웹은 VPN 뒤에 두더라도 DB 직결을 없애는 편이 낫다. 관리자 계정이
탈취되거나 화면에 취약점이 생겨도 DB 로 직접 넘어갈 수단이 없어진다.
"""

import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent


def _env(name, default=''):
    """ADMIN_BFF_<name> 을 우선 읽고, 없거나 비어 있으면 DJANGO_<name> 으로 떨어진다.

    빈 문자열을 '미설정'으로 취급하는 게 핵심이다. CI 가 값 없는 항목도 `KEY=`
    형태로 env 파일에 써 넣기 때문에, 이 구분이 없으면 os.environ.get 의
    default 가 영영 쓰이지 않는다.
    """
    for key in (f'ADMIN_BFF_{name}', f'DJANGO_{name}'):
        value = os.environ.get(key)
        if value:
            return value
    return default


def _env_list(name, default=''):
    return [v.strip() for v in _env(name, default).split(',') if v.strip()]


# api / 점주 웹과 각각 다른 키를 쓴다. 한쪽이 뚫려도 다른 쪽 서명을 위조할 수 없다.
SECRET_KEY = _env('SECRET_KEY', 'django-insecure-admin-bff-dev-key')
DEBUG = _env('DEBUG', '0') == '1'
ALLOWED_HOSTS = _env_list('ALLOWED_HOSTS', '*')
CSRF_TRUSTED_ORIGINS = _env_list('CSRF_ORIGINS')

if os.environ.get('USE_X_FORWARDED_PROTO', '0') == '1':
    SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')


INSTALLED_APPS = [
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'django.contrib.humanize',   # 템플릿의 intcomma
]

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'bff_core.auth.ApiSessionMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'admin_bff.urls'
WSGI_APPLICATION = 'admin_bff.wsgi.application'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [BASE_DIR / 'templates'],
        'APP_DIRS': False,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

DATABASES = {}

# 점주 웹과 같은 Redis 를 쓰되 DB 인덱스를 나눠 세션이 섞이지 않게 한다.
REDIS_URL = os.environ.get('REDIS_URL', 'redis://redis:6379/1')

CACHES = {
    'default': {
        'BACKEND': 'django.core.cache.backends.redis.RedisCache',
        'LOCATION': REDIS_URL,
    }
}

SESSION_ENGINE = 'django.contrib.sessions.backends.cache'
SESSION_CACHE_ALIAS = 'default'
# 점주 웹과 다른 쿠키 이름을 쓴다. 같은 도메인에서 서로 세션을 덮어쓰지 않도록.
SESSION_COOKIE_NAME = 'hackmin_admin_sid'
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SAMESITE = 'Lax'
SESSION_COOKIE_SECURE = os.environ.get('SESSION_COOKIE_SECURE', '0') == '1'
# 관리자 세션은 점주보다 짧게 잡는다.
SESSION_COOKIE_AGE = int(os.environ.get('ADMIN_SESSION_AGE', 60 * 60 * 4))
SESSION_SAVE_EVERY_REQUEST = True

API_BASE_URL = os.environ.get('HACKMIN_API_BASE', 'http://api:8000/api/v1').rstrip('/')
API_TIMEOUT = float(os.environ.get('HACKMIN_API_TIMEOUT', '10'))

LOGIN_URL = '/admin-web/login'

LANGUAGE_CODE = 'ko-kr'
TIME_ZONE = 'Asia/Seoul'
USE_I18N = True
USE_TZ = True

STATIC_URL = 'static/'
STATIC_ROOT = BASE_DIR / 'staticfiles'

DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'handlers': {'console': {'class': 'logging.StreamHandler'}},
    'root': {'handlers': ['console'], 'level': os.environ.get('LOG_LEVEL', 'INFO')},
}
