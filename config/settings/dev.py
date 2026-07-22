"""개발(dev) 설정 — 로컬/랩 편의 위주. 기본 프로파일.

기존 단일 settings.py 의 개발 기본값을 그대로 재현한다(동작 변화 없음).
"""

import os

from .base import *  # noqa: F401,F403

SECRET_KEY = os.environ.get(
    'DJANGO_SECRET_KEY',
    'django-insecure-q02ar%@=o_+br_@sno7rlcrct&6f$7#1zblta0_yqckg9484_e',
)

DEBUG = os.environ.get('DJANGO_DEBUG', '1') == '1'

ALLOWED_HOSTS = ['*']

# CORS – open for lab/dev convenience.
CORS_ALLOW_ALL_ORIGINS = True
