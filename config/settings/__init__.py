"""HackMin 설정 패키지.

DJANGO_ENV(dev|prod)로 설정 프로파일을 선택한다. 기본값은 dev.
DJANGO_SETTINGS_MODULE 은 계속 'config.settings' 를 가리키면 된다
(manage.py / wsgi.py / asgi.py 수정 불필요).
"""

import os

_env = os.environ.get('DJANGO_ENV', 'dev').strip().lower()

if _env in ('prod', 'production'):
    from .prod import *  # noqa: F401,F403
else:
    from .dev import *  # noqa: F401,F403
