#!/usr/bin/env python
"""web_bff(점주 웹 BFF) 전용 manage.py.

프로젝트 루트의 manage.py 는 config.settings(= API 서버) 를 가리킨다.
web_bff 이미지에는 config/ 와 apps/ 가 들어 있지 않으므로 진입점을 분리한다.

  로컬:   python manage_web.py runserver 0.0.0.0:8003
  컨테이너: python manage_web.py runserver 0.0.0.0:8000
"""

import os
import sys


def main():
    os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'web_bff.settings')
    try:
        from django.core.management import execute_from_command_line
    except ImportError as exc:
        raise ImportError(
            'Django 를 임포트하지 못했습니다. 가상환경이 활성화돼 있는지 확인하세요.'
        ) from exc
    execute_from_command_line(sys.argv)


if __name__ == '__main__':
    main()
