"""Vulnerable / Secure mode switching for 🎯 dual-mode endpoints.

The active mode comes from settings.HACKMIN_MODE, but can be overridden per
request with the `X-Hackmin-Mode: vulnerable|secure` header (when
settings.HACKMIN_ALLOW_MODE_HEADER is True) so a single running server can
demonstrate both behaviours side by side.
"""

from django.conf import settings

VULNERABLE = 'vulnerable'
SECURE = 'secure'


def get_mode(request):
    default = getattr(settings, 'HACKMIN_MODE', VULNERABLE)
    if getattr(settings, 'HACKMIN_ALLOW_MODE_HEADER', False) and request is not None:
        header = request.headers.get('X-Hackmin-Mode')
        if header:
            header = header.strip().lower()
            if header in (VULNERABLE, SECURE):
                return header
    return default


def is_vulnerable(request):
    return get_mode(request) == VULNERABLE
