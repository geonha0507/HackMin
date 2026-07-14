from django.urls import path

from . import auth_views

app_name = 'auth'

urlpatterns = [
    path('signup', auth_views.signup, name='signup'),
    path('login', auth_views.login, name='login'),
    path('logout', auth_views.logout, name='logout'),
    path('refresh', auth_views.refresh_token, name='refresh'),
    path('check-duplicate', auth_views.check_duplicate, name='check-duplicate'),
    path('password/reset-request', auth_views.password_reset_request, name='password-reset-request'),
    path('password/reset', auth_views.password_reset, name='password-reset'),
]
