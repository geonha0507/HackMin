"""Hackmin dual-mode helper.

Inspects the X-Hackmin-Mode header to decide whether the current request
should follow the 'vulnerable' code path (for security training) or the
'secure' code path (production-like behaviour).
"""

from django.conf import settings


def is_vulnerable(request):
    """Return True when the request opts into vulnerable mode.

    Conditions:
    1. settings.HACKMIN_ALLOW_MODE_HEADER must be True (default False).
    2. The request must carry ``X-Hackmin-Mode: vulnerable``.
    """
    if not getattr(settings, 'HACKMIN_ALLOW_MODE_HEADER', False):
        return False
    return request.META.get('HTTP_X_HACKMIN_MODE', 'secure').lower() == 'vulnerable'
