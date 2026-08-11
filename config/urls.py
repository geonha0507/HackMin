import os

from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.http import JsonResponse
from django.urls import include, path, re_path
from django.views.static import serve as static_serve

from config import events_poc  # 이벤트/프로모션 웹 페이지 (앱 EventWebActivity 가 로드)


def health(_request):
    return JsonResponse({'status': 'ok', 'service': 'hackmin-backend'})


api_v1 = [
    path('auth/', include('accounts.auth_urls')),
    path('', include('accounts.me_urls')),
    path('', include('restaurants.urls')),
    path('', include('carts.urls')),
    path('', include('chatbot.urls')),
    path('', include('inquiries.urls')),
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
    path('admin/', admin.site.urls),
    path('api/v1/health', health),
    # 이벤트/프로모션 상세 페이지 (앱 홈 이벤트 배너 → EventWebActivity 가 로드).
    # nginx 가 이미 프록시하는 /api/ 밑에 두어 별도 nginx 라우팅 없이 서빙한다.
    # include 보다 먼저 등록해야 /api/v1/ 로 흡수되지 않고 정확 매칭된다(health 와 동일 패턴).
    path('api/v1/events/', events_poc.events_page),
    path('api/v1/', include((api_v1, 'api_v1'))),
    # 점주 웹(/web/)은 web_bff 컨테이너가 담당한다. 이 프로세스에는 없다.
]

# 리뷰 이미지 공개 서빙(비민감). DEBUG 여부와 무관하게 reviews/ 하위만 노출한다.
# notice_files/attachments 등 인증 다운로드 전용 파일은 이 경로로 새지 않는다.
urlpatterns += [
    re_path(
        r'^media/reviews/(?P<path>.*)$',
        static_serve,
        {'document_root': os.path.join(settings.MEDIA_ROOT, 'reviews')},
    ),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
