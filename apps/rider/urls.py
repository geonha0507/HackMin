from django.urls import path

from . import views

app_name = 'rider'

urlpatterns = [
    path('rider/deliveries', views.delivery_list, name='deliveries'),
    path('rider/deliveries/<int:pk>', views.delivery_detail, name='delivery-detail'),
    path('rider/deliveries/<int:pk>/status', views.delivery_status, name='delivery-status'),
    path('rider/location', views.location, name='location'),
    path('rider/menus', views.menus, name='menus'),
    path('rider/profile', views.profile, name='profile'),
    path('rider/earnings', views.earnings, name='earnings'),
    path('rider/payouts', views.payouts, name='payouts'),
    # [방어 ④] 거래서명 공개키 등록 (Keystore EC 공개키)
    path('rider/txn-key', views.register_txn_key, name='txn-key'),
    # [의도된 취약점] IDOR — rider pk 로 타인 프로필/계좌 접근(소유권 검증 없음)
    path('riders/<int:pk>/profile', views.rider_profile_by_id, name='rider-profile-by-id'),
    path('riders/<int:pk>/account', views.rider_account_by_id, name='rider-account-by-id'),
]
