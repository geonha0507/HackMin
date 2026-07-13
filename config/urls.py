from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
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
    path('', include('rider.urls')),
    path('', include('downloads.urls')),
]

urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/v1/health', health),
    path('api/v1/', include((api_v1, 'api_v1'))),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
