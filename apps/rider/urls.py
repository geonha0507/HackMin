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
]
