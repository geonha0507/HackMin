from rest_framework.pagination import PageNumberPagination


class StandardPagination(PageNumberPagination):
    """List responses follow { count, next, previous, results }."""

    page_size = 20
    page_size_query_param = 'page_size'
    max_page_size = 100
