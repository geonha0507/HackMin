"""API 전용 URL 설정.

api 컨테이너에서만 사용한다 (DJANGO_ROOT_URLCONF=config.urls_api).
/web/, /admin/ 경로가 이 설정에는 아예 없으므로, api 컨테이너에서는
해당 경로 요청 시 항상 404가 떨어진다.

기존 config/urls.py 와 내용은 100% 동일하되, 'web/' 경로 include만 뺐다.
"""

from django.conf import settings
from django.conf.urls.static import static
from django.http import JsonResponse
from django.urls import include, path


def health(_request):
    return JsonResponse({'status': 'ok', 'service': 'hackmin-backend'})


api_v1 = [
    path('auth/', include('accounts.auth_urls')),
    path('', include('accounts.me_urls')),
    path('', include('restaurants.urls')),
    path('', include('carts.urls')),
    path('', include('orders.urls')),
    path('', include('payments.urls')),
    path('', include('reviews.urls')),
    path('', include('promotions.urls')),
    path('', include('owner.urls')),
    path('', include('adminpanel.urls')),
    path('', include('enrollment.urls')),
    path('', include('rider.urls')),
    path('', include('downloads.urls')),
]

urlpatterns = [
    path('api/v1/health', health),
    path('api/v1/', include((api_v1, 'api_v1'))),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
