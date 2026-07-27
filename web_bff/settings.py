"""web_bff — 점주 웹 전용 Django 설정 (3-tier 분리 PoC).

이 프로세스의 핵심 제약: **DB에 접속하지 않는다.**

  - `DATABASES = {}`  → DB 커넥션 자체가 없다. SQLi·RCE가 나도 DB로 못 간다.
  - 모델을 가진 로컬 앱이 INSTALLED_APPS 에 하나도 없다.
  - `django.contrib.auth` 를 쓰지 않는다. 인증은 /api/v1/auth/login 이 발급한
    JWT 를 세션에 보관하는 방식으로 대체한다 (web_bff/auth.py).
  - 세션은 Redis 캐시에 저장한다. Django 기본 세션 백엔드는 `django_session`
    테이블이라 그대로 두면 DB 의존이 되살아난다.

데이터는 전부 web_bff/api_client.py 를 통해 HTTP 로 가져온다.
"""

import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

def _env(name, default=''):
    """WEB_BFF_<name> 을 우선 읽고, 없거나 비어 있으면 DJANGO_<name> 으로 떨어진다.

    빈 문자열을 '미설정'으로 취급하는 게 핵심이다. os.environ.get(k, default) 은
    변수가 '존재하지만 비어 있는' 경우 default 를 쓰지 않고 '' 를 돌려주는데,
    CI 가 값 없는 항목도 `KEY=` 형태로 env 파일에 써 넣기 때문에 이 구분이 없으면
    ALLOWED_HOSTS 가 빈 리스트가 되어 DEBUG=False 에서 부팅이 실패한다.

    운영에서는 api 와 같은 app.env 를 공유하므로, 접두사가 붙은 값이 있으면
    그쪽을 써서 api 와 키·오리진을 분리한다.
    """
    for key in (f'WEB_BFF_{name}', f'DJANGO_{name}'):
        value = os.environ.get(key)
        if value:
            return value
    return default


def _env_list(name, default=''):
    return [v.strip() for v in _env(name, default).split(',') if v.strip()]


# api 와 다른 키를 쓰는 것이 원칙이다. WEB_BFF_SECRET_KEY 를 설정할 것.
SECRET_KEY = _env('SECRET_KEY', 'django-insecure-web-bff-dev-key')
DEBUG = _env('DEBUG', '0') == '1'
ALLOWED_HOSTS = _env_list('ALLOWED_HOSTS', '*')

# Django 4+ 는 스킴까지 포함한 정확한 값을 요구한다.
# nginx 로 HTTPS 종단을 하면 브라우저가 보내는 Origin 은 https:// 이므로
# http:// 만 넣어두면 로그인 POST 가 403 으로 막힌다. 둘 다 넣을 것.
#   WEB_BFF_CSRF_ORIGINS=https://54.116.95.188,http://54.116.95.188
CSRF_TRUSTED_ORIGINS = _env_list('CSRF_ORIGINS')

# 리버스 프록시 뒤에서 HTTPS 를 종단하면 Django 는 요청을 http 로 인식한다.
# 프록시가 X-Forwarded-Proto 를 붙여준다는 전제에서만 켠다(기본 꺼짐).
# 프록시 없이 켜면 클라이언트가 헤더를 위조해 secure 판정을 우회할 수 있다.
if os.environ.get('USE_X_FORWARDED_PROTO', '0') == '1':
    SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')


# --- 앱 -------------------------------------------------------------------
# 모델을 가진 앱은 하나도 없다. contenttypes / auth / admin 전부 제외.
INSTALLED_APPS = [
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'django.contrib.humanize',   # 템플릿의 intcomma 필터
]

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    # AuthenticationMiddleware 대신. request.web_user 를 세팅한다.
    'web_bff.auth.ApiSessionMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'web_bff.urls'
WSGI_APPLICATION = 'web_bff.wsgi.application'

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
                # auth context processor 는 없다 ({{ user }} 대신 {{ request.web_user }})
            ],
        },
    },
]


# --- DB 없음 ---------------------------------------------------------------
# 빈 dict 로 두면 Django 는 DB 백엔드를 초기화하지 않는다.
# 실수로 ORM 을 호출하면 ImproperlyConfigured 로 즉시 터진다 (의도한 동작).
DATABASES = {}


# --- 세션: Redis ------------------------------------------------------------
REDIS_URL = os.environ.get('REDIS_URL', 'redis://redis:6379/0')

CACHES = {
    'default': {
        'BACKEND': 'django.core.cache.backends.redis.RedisCache',
        'LOCATION': REDIS_URL,
    }
}

SESSION_ENGINE = 'django.contrib.sessions.backends.cache'
SESSION_CACHE_ALIAS = 'default'
SESSION_COOKIE_NAME = 'hackmin_web_sid'
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SAMESITE = 'Lax'
SESSION_COOKIE_SECURE = os.environ.get('SESSION_COOKIE_SECURE', '0') == '1'
# JWT refresh 수명(7일)보다 짧게 잡는다.
SESSION_COOKIE_AGE = int(os.environ.get('SESSION_COOKIE_AGE', 60 * 60 * 12))
SESSION_SAVE_EVERY_REQUEST = True   # access 토큰 갱신분을 매 요청 반영


# --- 백엔드 API -------------------------------------------------------------
API_BASE_URL = os.environ.get('HACKMIN_API_BASE', 'http://api:8000/api/v1').rstrip('/')
API_TIMEOUT = float(os.environ.get('HACKMIN_API_TIMEOUT', '10'))


LOGIN_URL = '/web/login'

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
