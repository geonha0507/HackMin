"""라이더 회원가입 + 실시간 위치(GPS relay) 엔드포인트 테스트."""

from django.contrib.auth import get_user_model
from rest_framework.test import APITestCase

User = get_user_model()


class RiderSignupTest(APITestCase):
    def test_rider_signup_creates_rider_and_returns_tokens(self):
        """role=rider 로 가입되면 라이더 계정이 만들어지고 토큰이 발급된다."""
        resp = self.client.post('/api/v1/auth/signup', {
            'username': 'rider1@test.com',
            'email': 'rider1@test.com',
            'password': 'Rider1234!',
            'nickname': '테스트라이더',
            'name': '김라이더',
            'phone': '01011112222',
            'role': 'rider',
            'terms_agreed': True,
        }, format='json')
        self.assertEqual(resp.status_code, 201, resp.content)
        self.assertEqual(resp.data['user']['role'], 'rider')
        self.assertIn('access', resp.data)
        user = User.objects.get(username='rider1@test.com')
        self.assertEqual(user.role, User.Role.RIDER)

    def test_admin_role_signup_still_rejected(self):
        """rider 허용이 admin 가입까지 열어주지 않는지(권한 상승 방지) 확인."""
        resp = self.client.post('/api/v1/auth/signup', {
            'username': 'evil@test.com',
            'email': 'evil@test.com',
            'password': 'Evil1234!',
            'nickname': '해커',
            'name': '악의',
            'phone': '01099998888',
            'role': 'admin',
            'terms_agreed': True,
        }, format='json')
        self.assertEqual(resp.status_code, 400, resp.content)


class RiderLocationTest(APITestCase):
    def setUp(self):
        self.rider = User.objects.create_user(
            username='rider2@test.com', email='rider2@test.com',
            password='Rider1234!', nickname='라이더2', name='이라이더',
            phone='01033334444', role=User.Role.RIDER,
        )
        self.customer = User.objects.create_user(
            username='cust@test.com', email='cust@test.com',
            password='Cust1234!', nickname='손님', name='박손님',
            phone='01055556666', role=User.Role.CUSTOMER,
        )

    def test_get_location_empty_returns_204(self):
        self.client.force_authenticate(self.rider)
        resp = self.client.get('/api/v1/rider/location')
        self.assertEqual(resp.status_code, 204)

    def test_put_then_get_location_roundtrip(self):
        self.client.force_authenticate(self.rider)
        put = self.client.put('/api/v1/rider/location', {
            'latitude': 37.4979, 'longitude': 127.0276, 'accuracy': 5.0,
        }, format='json')
        self.assertEqual(put.status_code, 200, put.content)
        self.assertAlmostEqual(put.data['latitude'], 37.4979)

        get = self.client.get('/api/v1/rider/location')
        self.assertEqual(get.status_code, 200)
        self.assertAlmostEqual(get.data['longitude'], 127.0276)

    def test_put_upserts_single_row(self):
        """두 번 보내면 새 행이 아니라 기존 위치를 덮어쓴다."""
        self.client.force_authenticate(self.rider)
        self.client.put('/api/v1/rider/location',
                        {'latitude': 37.0, 'longitude': 127.0}, format='json')
        self.client.put('/api/v1/rider/location',
                        {'latitude': 38.0, 'longitude': 128.0}, format='json')
        from rider.models import RiderLocation
        self.assertEqual(RiderLocation.objects.filter(rider=self.rider).count(), 1)
        # self.rider.location 은 첫 PUT 때 세팅된 역참조 캐시라 stale — DB에서 새로 조회한다.
        self.assertAlmostEqual(
            RiderLocation.objects.get(rider=self.rider).latitude, 38.0)

    def test_invalid_latitude_rejected(self):
        self.client.force_authenticate(self.rider)
        resp = self.client.put('/api/v1/rider/location',
                               {'latitude': 999, 'longitude': 127.0}, format='json')
        self.assertEqual(resp.status_code, 400, resp.content)

    def test_customer_cannot_update_location(self):
        """라이더가 아닌 계정은 위치 엔드포인트에 접근 불가(IsRider)."""
        self.client.force_authenticate(self.customer)
        resp = self.client.put('/api/v1/rider/location',
                               {'latitude': 37.0, 'longitude': 127.0}, format='json')
        self.assertIn(resp.status_code, (403, 401))
