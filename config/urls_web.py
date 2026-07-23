"""웹 전용 URL 설정 — web 컨테이너에서만 사용."""

from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.http import JsonResponse
from django.urls import include, path


def health(_request):
    return JsonResponse({'status': 'ok', 'service': 'hackmin-web'})


urlpatterns = [
    path('admin/', admin.site.urls),
    path('web/health', health),
    path('web/', include('web.urls')),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
