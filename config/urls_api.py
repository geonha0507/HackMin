"""API 전용 URL 설정.

api 컨테이너에서만 사용한다 (DJANGO_ROOT_URLCONF=config.urls_api).
/web/, /admin/ 경로가 이 설정에는 아예 없으므로, api 컨테이너에서는
해당 경로 요청 시 항상 404가 떨어진다.

기존 config/urls.py 와 내용은 100% 동일하되, 'web/' 경로 include만 뺐다.
"""
import os

from django.conf import settings
from django.conf.urls.static import static
from django.http import JsonResponse
from django.urls import include, path, re_path
from django.views.static import serve as static_serve


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
    path('api/v1/health', health),
    path('api/v1/', include((api_v1, 'api_v1'))),
]


# 리뷰 이미지 공개 서빙. 리뷰 이미지는 비민감이라 공개로 열어도 되며,
# 업로드 단계(reviews.views.upload_review_image)에서 이미지 확장자만 허용하므로
# 스크립트 파일이 올라올 수 없다. DEBUG 여부와 무관하게 항상 서빙하되,
# reviews/ 하위만 노출한다 → notice_files/attachments 등 인증 다운로드 전용
# 파일은 이 경로로 절대 새지 않는다.
urlpatterns += [
    re_path(
        r'^media/reviews/(?P<path>.*)$',
        static_serve,
        {'document_root': settings.MEDIA_ROOT},
    ),
]


if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
