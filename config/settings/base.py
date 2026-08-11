"""HackMin 공통(base) 설정.

환경별 설정(dev.py / prod.py)이 이 모듈을 가져와 DEBUG·SECRET_KEY·
ALLOWED_HOSTS·CORS 등을 재정의한다. 활성 프로파일은 DJANGO_ENV 로 선택하며
기본은 dev (config/settings/__init__.py 참고).
"""

import os
import sys
from datetime import timedelta
from pathlib import Path

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
        'rest_framework.parsers.MultiPartParser',
    ),
    'DEFAULT_RENDERER_CLASSES': (
        'common.enc.EncryptedJSONRenderer',
    ),
}

SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(hours=6),
    'REFRESH_TOKEN_LIFETIME': timedelta(days=7),
    'AUTH_HEADER_TYPES': ('Bearer',),
    'ROTATE_REFRESH_TOKENS': False,
}

# --- Payload encryption (hybrid RSA-OAEP + AES-256-GCM) --------------------
# 앱↔서버 본문 암호화용 서버 개인키(PEM). 우선순위: 환경변수 > dev 파일.
# 프로덕션에선 반드시 PAYLOAD_PRIVATE_KEY_PEM 환경변수로 주입하고 dev 키는 폐기할 것.
PAYLOAD_PRIVATE_KEY_PEM = os.environ.get('PAYLOAD_PRIVATE_KEY_PEM', '')
if not PAYLOAD_PRIVATE_KEY_PEM:
    _payload_key_file = BASE_DIR / 'keys' / 'payload_private_dev.pem'
    if _payload_key_file.exists():
        PAYLOAD_PRIVATE_KEY_PEM = _payload_key_file.read_text()

# 앱과 공유하는 HMAC 시크릿. **APK 에만** 존재해야 명분이 성립하므로(리포·브라우저엔
# 없어야 함), 랩/프로덕션에선 반드시 PAYLOAD_APP_HMAC_SECRET 를 새로 만들어 환경변수로
# 주입하고 앱도 같은 값으로 재빌드한다. 아래 기본값은 개발팀 로컬 실행용일 뿐이다.
PAYLOAD_APP_HMAC_SECRET = os.environ.get(
    'PAYLOAD_APP_HMAC_SECRET', 'VND7AfiCMSCTg9ZuHJW+JJLnEXzgq5uc4FhotbYRecg=')

# 서버측 BFF(web_bff/admin_bff)만 아는 내부 호출 키. 이 키가 유효하면 평문 호출을
# 허용한다(SSR 호환). **APK·브라우저에는 절대 넣지 말 것** — 넣으면 평문 우회로가
# 생긴다. 랩/프로덕션에선 환경변수로 주입.
PAYLOAD_INTERNAL_KEY = os.environ.get(
    'PAYLOAD_INTERNAL_KEY', 'Z90pA78Pyhwpd/ZRpfsuRV91Itg/yuD1Joulvp/IG0w=')

# 암호화 강제 여부. 기본 On. 앱↔서버 스택을 동시에 배포할 수 없는 개발 상황에선
# PAYLOAD_ENFORCE=0 으로 임시 비활성 가능(그동안 평문 폴백 우회로가 다시 열림에 유의).
PAYLOAD_ENFORCE = os.environ.get('PAYLOAD_ENFORCE', '1') == '1'

# 강제 대상에서 제외할 경로 프리픽스(암호화 헤더가 없는 정상 트래픽).
#   - /api/v1/health        : 헬스체크
#   - /api/v1/events        : 앱 WebView 가 로드하는 이벤트 HTML 페이지
#   - /media                : 공개 이미지 서빙
PAYLOAD_ENFORCE_EXEMPT_PREFIXES = (
    '/api/v1/health',
    '/api/v1/events',
    '/media',
)

# CORS 공통값. 오리진 허용 정책(CORS_ALLOW_ALL_ORIGINS 등)은 dev/prod 에서 지정.
CORS_ALLOW_CREDENTIALS = True
CORS_ALLOW_ALL_ORIGINS = False
