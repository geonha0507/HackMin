"""
Django settings for the HackMin delivery-order backend.

This is a security-training backend: endpoints marked with the 🎯 symbol in the
spec are implemented in two modes (Vulnerable / Secure). The active mode is
controlled by HACKMIN_MODE (see the "HackMin lab" section at the bottom).
"""

import os
import sys
from datetime import timedelta
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

# Make the `apps/` package importable as top-level app labels (e.g. `accounts`).
sys.path.insert(0, str(BASE_DIR / 'apps'))


SECRET_KEY = os.environ.get(
    'DJANGO_SECRET_KEY',
    'django-insecure-q02ar%@=o_+br_@sno7rlcrct&6f$7#1zblta0_yqckg9484_e',
)

DEBUG = os.environ.get('DJANGO_DEBUG', '1') == '1'

ALLOWED_HOSTS = ['*']


INSTALLED_APPS = [
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',

    # Third-party
    'rest_framework',
    'rest_framework_simplejwt',
    'corsheaders',
    'django_filters',
    'storages',

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
    'owner',
    'adminpanel',
    'rider',
    'enrollment',
    'downloads',
    'web',
]

# 세션 기반 관리자/점주 웹 로그인 경로
LOGIN_URL = '/web/login'

MIDDLEWARE = [
    'corsheaders.middleware.CorsMiddleware',
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'config.urls'

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
# S3를 사용하려면 환경변수 USE_S3=1 설정. 미설정 시 로컬 파일 시스템 사용.
if os.environ.get('USE_S3', '0') == '1':
    # 인증: 환경변수 또는 IAM Role (EC2)
    AWS_ACCESS_KEY_ID = os.environ.get('AWS_ACCESS_KEY_ID')
    AWS_SECRET_ACCESS_KEY = os.environ.get('AWS_SECRET_ACCESS_KEY')
    AWS_STORAGE_BUCKET_NAME = os.environ.get('AWS_S3_BUCKET_NAME', 'hackmin-media-bucket')
    AWS_S3_REGION_NAME = os.environ.get('AWS_S3_REGION_NAME', 'ap-northeast-2')

    AWS_S3_CUSTOM_DOMAIN = f'{AWS_STORAGE_BUCKET_NAME}.s3.{AWS_S3_REGION_NAME}.amazonaws.com'
    AWS_S3_FILE_OVERWRITE = False
    AWS_DEFAULT_ACL = None                     # ACL 비활성화 버킷 대응
    AWS_QUERYSTRING_AUTH = True                 # Pre-signed URL (퍼블릭 차단 버킷용)
    AWS_S3_OBJECT_PARAMETERS = {
        'CacheControl': 'max-age=86400',
    }

    # 미디어 파일(업로드) → S3
    DEFAULT_FILE_STORAGE = 'storages.backends.s3boto3.S3Boto3Storage'
    MEDIA_URL = f'https://{AWS_S3_CUSTOM_DOMAIN}/'

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
}

SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(hours=6),
    'REFRESH_TOKEN_LIFETIME': timedelta(days=7),
    'AUTH_HEADER_TYPES': ('Bearer',),
    'ROTATE_REFRESH_TOKENS': False,
}

# CORS – open for lab/dev convenience.
CORS_ALLOW_ALL_ORIGINS = True
CORS_ALLOW_CREDENTIALS = True


# --- HackMin lab -----------------------------------------------------------
# Default mode for 🎯 dual-mode endpoints. Either 'vulnerable' or 'secure'.
# Per-request override is allowed via the `X-Hackmin-Mode` header so a single
# running server can demonstrate both behaviours.
HACKMIN_MODE = os.environ.get('HACKMIN_MODE', 'vulnerable')
HACKMIN_ALLOW_MODE_HEADER = True