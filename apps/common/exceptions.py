"""Unified error format: { "code": "...", "message": "..." }."""

from rest_framework.views import exception_handler as drf_exception_handler
from rest_framework.response import Response


# Map common DRF status codes to stable machine-readable error codes.
_STATUS_CODE_MAP = {
    400: 'bad_request',
    401: 'unauthenticated',
    403: 'forbidden',
    404: 'not_found',
    405: 'method_not_allowed',
    409: 'conflict',
    415: 'unsupported_media_type',
    429: 'too_many_requests',
    500: 'server_error',
}


def _extract_message(data):
    if isinstance(data, dict):
        if 'detail' in data:
            return str(data['detail'])
        # Field errors: surface the first one.
        for field, value in data.items():
            if isinstance(value, (list, tuple)) and value:
                return f'{field}: {value[0]}'
            return f'{field}: {value}'
    if isinstance(data, (list, tuple)) and data:
        return str(data[0])
    return str(data)


def hackmin_exception_handler(exc, context):
    response = drf_exception_handler(exc, context)
    if response is None:
        return None

    code = getattr(exc, 'default_code', None)
    if not code or code == 'error':
        code = _STATUS_CODE_MAP.get(response.status_code, 'error')

    message = _extract_message(response.data)
    response.data = {'code': code, 'message': message}
    return response


def error_response(code, message, status_code=400):
    """Helper to return the unified error shape from a view."""
    return Response({'code': code, 'message': message}, status=status_code)
