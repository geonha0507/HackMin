"""점주(owner) 웹(SSR) 전용 URL 설정.

web(owner-web) 컨테이너에서만 사용한다 (DJANGO_ROOT_URLCONF=config.urls_web).
/api/v1/ 경로도, admin_web 관련 경로도 이 설정에는 없다 — 관리자 화면은
apps/admin_web 앱으로 물리적으로 분리되어 별도 컨테이너(config.urls_admin)에서만 뜬다.
"""

from django.conf import settings
from django.conf.urls.static import static
from django.urls import include, path

urlpatterns = [
    path('web/', include('web.urls')),  # HackMin owner(점주) SSR 콘솔 전용
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
