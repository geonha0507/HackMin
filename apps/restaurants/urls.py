from django.urls import path

from . import location_views, views

app_name = 'restaurants'

urlpatterns = [
    # /api/v1/restaurants ...
    path('restaurants', views.restaurant_search, name='search'),
    path('restaurants/<int:pk>', views.RestaurantDetailView.as_view(), name='detail'),
    path('restaurants/<int:pk>/menus', views.RestaurantMenuListView.as_view(), name='menus'),
    path('restaurants/<int:pk>/reviews', views.RestaurantReviewListView.as_view(), name='reviews'),


    # /api/v1/menus/{id}
    path('menus/<int:pk>', views.MenuDetailView.as_view(), name='menu-detail'),

    # /api/v1/locations ...
    path('locations/search', location_views.location_search, name='location-search'),
    path('locations/nearby', location_views.nearby_restaurants, name='location-nearby'),
]
