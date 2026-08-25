"""배달원 실시간 위치(고객용) 엔드포인트 테스트."""

from django.contrib.auth import get_user_model
from rest_framework.test import APITestCase

from orders.models import Order
from restaurants.models import Restaurant
from rider.models import Delivery, RiderLocation

User = get_user_model()


class OrderRiderLocationTest(APITestCase):
    def setUp(self):
        self.customer = User.objects.create_user(
            username='c@test.com', email='c@test.com', password='Cust1234!',
            nickname='손님', name='손', phone='01011112222', role=User.Role.CUSTOMER,
        )
        self.other = User.objects.create_user(
            username='c2@test.com', email='c2@test.com', password='Cust1234!',
            nickname='남', name='남', phone='01099998888', role=User.Role.CUSTOMER,
        )
        self.owner = User.objects.create_user(
            username='o@test.com', email='o@test.com', password='Owner1234!',
            nickname='점주', name='점', phone='01033334444', role=User.Role.OWNER,
        )
        self.rider = User.objects.create_user(
            username='r@test.com', email='r@test.com', password='Rider1234!',
            nickname='라이더', name='라', phone='01055556666', role=User.Role.RIDER,
        )
        rest = Restaurant.objects.create(owner=self.owner, name='교촌치킨', cuisine_type='치킨')
        self.order = Order.objects.create(user=self.customer, restaurant=rest, total=20000,
                                          status=Order.Status.DELIVERING)
        self.delivery = Delivery.objects.create(order=self.order, rider=self.rider,
                                                status=Delivery.Status.DELIVERING)

    def _url(self, pk):
        return f'/api/v1/orders/{pk}/rider-location'

    def test_no_location_returns_204(self):
        self.client.force_authenticate(self.customer)
        resp = self.client.get(self._url(self.order.id))
        self.assertEqual(resp.status_code, 204)

    def test_owner_sees_rider_location(self):
        RiderLocation.objects.create(rider=self.rider, latitude=37.4979,
                                     longitude=127.0276, accuracy=5.0)
        self.client.force_authenticate(self.customer)
        resp = self.client.get(self._url(self.order.id))
        self.assertEqual(resp.status_code, 200, resp.content)
        self.assertAlmostEqual(resp.data['latitude'], 37.4979)
        self.assertAlmostEqual(resp.data['longitude'], 127.0276)

    def test_spoofed_location_is_shown_as_is(self):
        """라이더가 조작한 좌표를 서버가 검증 없이 그대로 고객에게 전달한다(의도된 취약점)."""
        RiderLocation.objects.create(rider=self.rider, latitude=1.2345, longitude=103.9999)
        self.client.force_authenticate(self.customer)
        resp = self.client.get(self._url(self.order.id))
        self.assertEqual(resp.status_code, 200)
        self.assertAlmostEqual(resp.data['latitude'], 1.2345)   # 조작값 그대로

    def test_non_owner_cannot_see(self):
        RiderLocation.objects.create(rider=self.rider, latitude=37.5, longitude=127.0)
        self.client.force_authenticate(self.other)
        resp = self.client.get(self._url(self.order.id))
        self.assertEqual(resp.status_code, 404)
