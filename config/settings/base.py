"""HackMin 공통(base) 설정.

환경별 설정(dev.py / prod.py)이 이 모듈을 가져와 DEBUG·SECRET_KEY·
ALLOWED_HOSTS·CORS 등을 재정의한다. 활성 프로파일은 DJANGO_ENV 로 선택하며
기본은 dev (config/settings/__init__.py 참고).
"""

import os
import sys
from datetime import timedelta
from pathlib import Path

from django.core.exceptions import ImproperlyConfigured
from dotenv import load_dotenv

# 이 파일은 config/settings/base.py 이므로 프로젝트 루트는 세 단계 위.
BASE_DIR = Path(__file__).resolve().parent.parent.parent

load_dotenv(BASE_DIR / '.env')

# Make the `apps/` package importable as top-level app labels (e.g. `accounts`).
sys.path.insert(0, str(BASE_DIR / 'apps'))


# 아래 3개는 안전한 기본값이며, dev.py / prod.py 에서 재정의한다.
SECRET_KEY = os.environ.get('DJANGO_SECRET_KEY', '')
DEBUG = False
ALLOWED_HOSTS = []


INSTALLED_APPS = [
    'daphne',  # must be before django.contrib.staticfiles

    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'django.contrib.humanize',

    # Third-party
    'rest_framework',
    'rest_framework_simplejwt',
    'corsheaders',
    'django_filters',
    'storages',
    'channels',

    # Local apps
    'common',
    'accounts',
    'restaurants',
    'carts',
    'orders',
    'payments',
    'reviews',
    'promotions',
    'chatbot',
    'inquiries',
    'owner',
    'adminpanel',
    'rider',
    'enrollment',
    'downloads',
]

# 세션 기반 관리자/점주 웹 로그인 경로
LOGIN_URL = '/web/login'

MIDDLEWARE = [
    'corsheaders.middleware.CorsMiddleware',
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    # 페이로드 암호화 강제: 비신뢰(앱) 요청은 X-Enc-Key + 유효 X-Sig 없으면 400.
    # 서버측 BFF 는 X-Internal-Key 로 통과. 헤더만 보고 본문 스트림은 건드리지 않는다.
    'common.enc.PayloadEnforcementMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = os.environ.get('DJANGO_ROOT_URLCONF', 'config.urls')

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

WSGI_APPLICATION = 'config.wsgi.application'
ASGI_APPLICATION = 'config.asgi.application'

# --- Django Channels (WebSocket) -------------------------------------------
CHANNEL_LAYERS = {
    'default': {
        'BACKEND': 'channels_redis.pubsub.RedisPubSubChannelLayer',
        'CONFIG': {
            'hosts': [(os.environ.get('REDIS_HOST', '127.0.0.1'),
                       int(os.environ.get('REDIS_PORT', 6379)))],
        },
    },
}


# DB_ENGINE=mysql 이면 MySQL(RDS) 사용, 그 외(기본값)는 로컬 SQLite 사용.
if os.environ.get('DB_ENGINE', 'sqlite') == 'mysql':
    _mysql_options = {'charset': 'utf8mb4'}
    # RDS 강제 SSL 인스턴스거나 SSL 연결을 원하면 DB_SSL_CA에 CA 번들 경로를 지정
    # (예: AWS 글로벌 번들을 컨테이너에 마운트한 경로 /app/certs/rds-ca.pem)
    _db_ssl_ca = os.environ.get('DB_SSL_CA')
    if _db_ssl_ca:
        _mysql_options['ssl'] = {'ca': _db_ssl_ca}

    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.mysql',
            'NAME': os.environ.get('DB_NAME', 'hackmin'),
            'USER': os.environ.get('DB_USER', 'hackmin'),
            'PASSWORD': os.environ.get('DB_PASSWORD', 'hackmin'),
            'HOST': os.environ.get('DB_HOST', ''),  # RDS 엔드포인트, 예: hackmin.xxxxxx.ap-northeast-2.rds.amazonaws.com
            'PORT': os.environ.get('DB_PORT', '3306'),
            'OPTIONS': _mysql_options,
            'CONN_MAX_AGE': int(os.environ.get('DB_CONN_MAX_AGE', '60')),
        }
    }
else:
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.sqlite3',
            'NAME': os.environ.get('DB_SQLITE_PATH', BASE_DIR / 'db.sqlite3'),
        }
    }

AUTH_USER_MODEL = 'accounts.User'

# Relaxed for a training lab; do not use in production.
AUTH_PASSWORD_VALIDATORS = []


LANGUAGE_CODE = 'ko-kr'
TIME_ZONE = 'Asia/Seoul'
USE_I18N = True
USE_TZ = True

STATIC_URL = 'static/'
STATIC_ROOT = BASE_DIR / 'staticfiles'

MEDIA_URL = '/media/'
MEDIA_ROOT = BASE_DIR / 'media'

# --- AWS S3 Storage --------------------------------------------------------
# USE_S3=1 이면 파일을 S3 버킷에 저장, 아니면 로컬 /media 사용.
if os.environ.get('USE_S3', '0') == '1':
    AWS_STORAGE_BUCKET_NAME = os.environ.get('AWS_S3_BUCKET_NAME', 'hackmin-media-bucket')
    AWS_S3_REGION_NAME = os.environ.get('AWS_S3_REGION_NAME', 'ap-northeast-2')
    AWS_S3_FILE_OVERWRITE = False
    AWS_DEFAULT_ACL = None
    AWS_QUERYSTRING_AUTH = True                 # Pre-signed URL (퍼블릭 차단 버킷용)
    AWS_S3_OBJECT_PARAMETERS = {'CacheControl': 'max-age=86400'}
    DEFAULT_FILE_STORAGE = 'storages.backends.s3boto3.S3Boto3Storage'

# --- 리뷰 이미지 전용 스토리지 (민감정보 분리) ------------------------------
REVIEW_IMAGE_ROOT = os.environ.get('REVIEW_IMAGE_ROOT', str(MEDIA_ROOT))
REVIEW_IMAGE_URL = '/media/'

DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'


# --- Django REST Framework -------------------------------------------------
REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': (
        'rest_framework_simplejwt.authentication.JWTAuthentication',
        'rest_framework.authentication.SessionAuthentication',
    ),
    'DEFAULT_PERMISSION_CLASSES': (
        'rest_framework.permissions.AllowAny',
    ),
    'DEFAULT_PAGINATION_CLASS': 'common.pagination.StandardPagination',
    'PAGE_SIZE': 20,
    'DEFAULT_FILTER_BACKENDS': (
        'django_filters.rest_framework.DjangoFilterBackend',
        'rest_framework.filters.SearchFilter',
        'rest_framework.filters.OrderingFilter',
    ),
    'EXCEPTION_HANDLER': 'common.exceptions.hackmin_exception_handler',
    'DEFAULT_THROTTLE_CLASSES': (),
    # 페이로드 하이브리드 암호화(듀얼 모드): X-Enc-Key 헤더가 있으면 요청/응답 본문을
    # 복호화/암호화하고, 없으면 평범한 JSON으로 폴백. 누가 평문으로 호출해도 되는지는
    # PayloadEnforcementMiddleware 가 통제한다(BFF 는 허용, 앱/공격자는 강제).
    'DEFAULT_PARSER_CLASSES': (
        'common.enc.EncryptedJSONParser',
        'rest_framework.parsers.FormParser',
        'common.enc.SignedMultiPartParser',
    ),
    'DEFAULT_RENDERER_CLASSES': (
        'common.enc.EncryptedJSONRenderer',
        # 브라우저로 /api/v1 을 열었을 때 406 대신 DRF 브라우저블 API 가 뜨도록 유지.
        'rest_framework.renderers.BrowsableAPIRenderer',
    ),
}

SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(hours=6),
    'REFRESH_TOKEN_LIFETIME': timedelta(days=7),
    'AUTH_HEADER_TYPES': ('Bearer',),
    'ROTATE_REFRESH_TOKENS': False,
}

# --- Payload encryption (hybrid RSA-OAEP + AES-256-GCM) --------------------
# 암호화 강제 여부. **기본 Off** — env 를 빠뜨렸을 때 조용히 켜져서 앱이 전면 400 이
# 되는 사고를 막는다. 앱(APK)에 CryptoInterceptor 가 들어간 빌드를 배포한 뒤에 켠다.
PAYLOAD_ENFORCE = os.environ.get('PAYLOAD_ENFORCE', '0') == '1'

# 서버 RSA 개인키(PEM). 우선순위: base64 env > raw PEM env > dev 파일.
#   PEM 은 여러 줄이라 docker compose 의 env_file 파서와 CI 검증을 통과하지 못한다.
#   그래서 배포에는 base64 한 줄(PAYLOAD_PRIVATE_KEY_B64)을 쓴다:
#     base64 -w0 keys/payload_private.pem
_payload_key_b64 = os.environ.get('PAYLOAD_PRIVATE_KEY_B64', '')
if _payload_key_b64:
    import base64 as _b64
    PAYLOAD_PRIVATE_KEY_PEM = _b64.b64decode(_payload_key_b64).decode('utf-8')
else:
    PAYLOAD_PRIVATE_KEY_PEM = os.environ.get('PAYLOAD_PRIVATE_KEY_PEM', '')
    if not PAYLOAD_PRIVATE_KEY_PEM:
        _payload_key_file = BASE_DIR / 'keys' / 'payload_private_dev.pem'
        if _payload_key_file.exists():
            PAYLOAD_PRIVATE_KEY_PEM = _payload_key_file.read_text()

# 앱과 공유하는 HMAC 시크릿. **APK 와 서버 env 에만** 존재해야 한다 —
# 기본값을 두지 않는다(리포에 값이 있으면 우회로가 그대로 열린다).
PAYLOAD_APP_HMAC_SECRET = os.environ.get('PAYLOAD_APP_HMAC_SECRET', '')

# 서버측 BFF(web_bff/admin_bff)만 아는 내부 호출 키. 이 키가 유효하면 평문 호출을
# 허용한다(SSR 호환 + curl 로 운영/디버깅). **APK·브라우저·리포에 절대 넣지 말 것** —
# 넣으면 헤더 한 줄로 강제 미들웨어를 통째로 우회할 수 있다.
PAYLOAD_INTERNAL_KEY = os.environ.get('PAYLOAD_INTERNAL_KEY', '')

# 필수값 검증 — 없는 값이 런타임에 조용한 장애로 터지는 대신 배포가 실패하게 한다.
#
# 개인키는 PAYLOAD_ENFORCE 와 무관하게 필요하다. 강제를 꺼도(듀얼 모드) 앱은
# X-Enc-Key 를 붙여 암호문을 보내고, 서버는 그걸 개인키로 풀어야 하기 때문이다.
# 키가 없으면 '앱 요청만' 500 이 되는데 헬스체크·웹은 멀쩡해서 발견이 늦는다.
# 기본값은 0(검사 안 함) — 아직 개인키를 주입하지 않은 현재 배포가 깨지지 않도록.
# 개인키를 GitHub Secrets 에 넣은 뒤 리포지토리 변수 PAYLOAD_APP_REQUIRED=1 로 올린다.
PAYLOAD_APP_REQUIRED = os.environ.get('PAYLOAD_APP_REQUIRED', '0') == '1'

if PAYLOAD_APP_REQUIRED and not PAYLOAD_PRIVATE_KEY_PEM:
    raise ImproperlyConfigured(
        'PAYLOAD_PRIVATE_KEY_B64 미설정 — 앱이 보낸 암호문을 복호화할 수 없습니다. '
        'base64 -w0 keys/payload_private.pem 결과를 주입하거나, '
        '앱 배포 전이라면 PAYLOAD_APP_REQUIRED=0 으로 두세요.'
    )

# HMAC 시크릿과 내부키는 '강제 모드'에서만 의미가 있다.
#   - HMAC 이 비면 verify_app_signature 가 항상 False → 앱 전면 400
#   - 내부키가 비면 _is_internal 이 항상 False → BFF 도 전면 400 (웹 전체 다운)
if PAYLOAD_ENFORCE:
    _missing = [
        name for name, value in (
            ('PAYLOAD_APP_HMAC_SECRET', PAYLOAD_APP_HMAC_SECRET),
            ('PAYLOAD_INTERNAL_KEY', PAYLOAD_INTERNAL_KEY),
        ) if not value
    ]
    if _missing:
        raise ImproperlyConfigured(
            'PAYLOAD_ENFORCE=1 인데 다음 값이 비어 있습니다: %s. '
            '환경변수를 주입하거나 PAYLOAD_ENFORCE=0 으로 두세요.' % ', '.join(_missing)
        )

# 강제 대상에서 제외할 경로 프리픽스(암호화 헤더가 없는 정상 트래픽).
#   - /api/v1/health : 헬스체크
#   - /api/v1/events : 앱 WebView 가 로드하는 이벤트 HTML 페이지
# (/media 는 _should_enforce 가 /api/ 로 시작하지 않는 경로를 이미 통과시켜 무의미)
PAYLOAD_ENFORCE_EXEMPT_PREFIXES = (
    '/api/v1/health',
    '/api/v1/events',
)

# CORS 공통값. 오리진 허용 정책(CORS_ALLOW_ALL_ORIGINS 등)은 dev/prod 에서 지정.
CORS_ALLOW_CREDENTIALS = True
CORS_ALLOW_ALL_ORIGINS = False
