"""web_bff URL 설정.

경로와 URL 이름을 기존 apps/web 과 동일하게 유지한다. 템플릿의
{% url 'web:owner_dashboard' %} 같은 태그를 그대로 쓰기 위해서다.
"""

from django.http import JsonResponse
from django.urls import include, path

from . import views

# 네임스페이스를 'web' 으로 고정한다. 템플릿의 {% url 'web:...' %} 가
# apps/web 과 동일하게 동작하도록 하기 위해서다.
web_patterns = ([
    path('login', views.login_view, name='login'),
    path('logout', views.logout_view, name='logout'),
    path('owner/', views.dashboard, name='owner_dashboard'),
], 'web')


def health(_request):
    return JsonResponse({'status': 'ok', 'service': 'hackmin-web-bff'})


urlpatterns = [
    path('web/health', health),
    path('web/', include(web_patterns)),
]
