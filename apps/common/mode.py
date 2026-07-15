"""Hackmin mode helper.

Vulnerable mode has been removed. This function is kept as a stub
so that existing imports (e.g. downloads/views.py) do not break.
It always returns False — all requests follow the secure code path.
"""


def is_vulnerable(request):
    """Always returns False — vulnerable mode is disabled."""
    return False
