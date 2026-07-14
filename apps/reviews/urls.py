from django.urls import path

from . import views

app_name = 'reviews'

urlpatterns = [
    path('reviews', views.create_review, name='create'),
    path('reviews/<int:pk>', views.review_detail, name='detail'),
    path('reviews/<int:pk>/images', views.upload_review_image, name='images'),

    # /me/reviews (spec section 2)
    path('me/reviews', views.MyReviewListView.as_view(), name='my-reviews'),
]
