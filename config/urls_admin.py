"""관리자 웹 전용 URL 설정.

admin-web 컨테이너에서만 사용한다 (DJANGO_ROOT_URLCONF=config.urls_admin).
api/owner-web 컨테이너에는 이 경로들이 전혀 없으므로, 관리자 화면·
Django 기본 admin은 오직 이 컨테이너(및 nginx에서 IP 제한 걸 /admin-web/ 경로)를
통해서만 접근 가능하다.
"""

from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.urls import include, path

urlpatterns = [
    path('admin/', admin.site.urls),               # Django 기본 관리자(비상용 DB 직접 조작 도구)
    path('admin-web/', include('admin_web.urls')),  # HackMin 관리자 콘솔
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
