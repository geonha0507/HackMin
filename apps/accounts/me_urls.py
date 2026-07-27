from django.urls import path

from . import me_views

app_name = 'me'

urlpatterns = [
    path('me', me_views.me, name='detail'),
    path('me/password', me_views.change_password, name='password'),
    path('me/addresses', me_views.AddressListCreateView.as_view(), name='addresses'),
    path('me/addresses/<int:pk>', me_views.AddressDetailView.as_view(), name='address-detail'),
    path('me/payment', me_views.payment_info, name='payment'),
]
