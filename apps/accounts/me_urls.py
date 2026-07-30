from django.urls import path

from . import me_views

app_name = 'me'

urlpatterns = [
    path('me', me_views.me, name='detail'),
    path('me/password', me_views.change_password, name='password'),
    path('me/addresses', me_views.AddressListCreateView.as_view(), name='addresses'),
    path('me/addresses/<int:pk>', me_views.AddressDetailView.as_view(), name='address-detail'),
    path('me/payment-cards', me_views.payment_cards, name='payment-cards'),
    path('me/payment-cards/<int:pk>', me_views.payment_card_detail, name='payment-card-detail'),
    path('me/bank-accounts', me_views.bank_accounts, name='bank-accounts'),
    path('me/bank-accounts/<int:pk>', me_views.bank_account_detail, name='bank-account-detail'),
    path('me/payment-password', me_views.payment_password, name='payment-password'),
    path('me/payment-password/verify', me_views.payment_password_verify, name='payment-password-verify'),
    path('me/payment', me_views.payment_info, name='payment'),
]
