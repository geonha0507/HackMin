#!/usr/bin/env python
"""admin_bff(관리자 웹 BFF) 전용 manage.py.

  로컬:    python manage_admin.py runserver 0.0.0.0:8002
  컨테이너: python manage_admin.py runserver 0.0.0.0:8000
"""

import os
import sys


def main():
    os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'admin_bff.settings')
    from django.core.management import execute_from_command_line
    execute_from_command_line(sys.argv)


if __name__ == '__main__':
    main()
