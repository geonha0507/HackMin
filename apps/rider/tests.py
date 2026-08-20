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


class RiderSignupResidentTest(APITestCase):
    def test_signup_stores_resident_number_encrypted(self):
        """라이더 가입 시 주민번호는 암호화 컬럼에 저장되고 평문은 남지 않는다."""
        resp = self.client.post('/api/v1/auth/signup', {
            'username': 'rr@test.com', 'email': 'rr@test.com', 'password': 'Rider1234!',
            'nickname': '라이더RR', 'name': '주민', 'phone': '01012349999',
            'role': 'rider', 'resident_number': '900101-1234567', 'terms_agreed': True,
        }, format='json')
        self.assertEqual(resp.status_code, 201, resp.content)
        user = User.objects.get(username='rr@test.com')
        self.assertTrue(user.resident_number_encrypted)
        self.assertNotIn('9001011234567', user.resident_number_encrypted)
        from accounts.crypto_utils import decrypt_aes128
        self.assertEqual(decrypt_aes128(user.resident_number_encrypted), '9001011234567')

    def test_signup_rejects_bad_resident_number(self):
        resp = self.client.post('/api/v1/auth/signup', {
            'username': 'rr2@test.com', 'email': 'rr2@test.com', 'password': 'Rider1234!',
            'nickname': '라이더RR2', 'name': '주민', 'phone': '01012340000',
            'role': 'rider', 'resident_number': '12345', 'terms_agreed': True,
        }, format='json')
        self.assertEqual(resp.status_code, 400, resp.content)


class RiderProfileTest(APITestCase):
    def setUp(self):
        self.rider = User.objects.create_user(
            username='rp@test.com', email='rp@test.com', password='Rider1234!',
            nickname='라이더P', name='프로', phone='01011112222', role=User.Role.RIDER,
        )

    def test_profile_get_empty_then_put_roundtrip(self):
        self.client.force_authenticate(self.rider)
        empty = self.client.get('/api/v1/rider/profile')
        self.assertEqual(empty.status_code, 200)

        put = self.client.put('/api/v1/rider/profile', {
            'bank_name': '카카오뱅크', 'account_number': '3333012345678',
            'account_holder': '김라이더', 'license_number': '11-22-334455-66',
            'vehicle_number': '12가3456', 'region': '서울 강남구',
            'delivery_method': 'motorcycle',
        }, format='json')
        self.assertEqual(put.status_code, 200, put.content)
        self.assertEqual(put.data['delivery_method_label'], '오토바이')
        # 계좌번호는 마스킹되어 내려오고 평문은 응답에 없음.
        self.assertTrue(put.data['account_number_masked'].endswith('5678'))
        self.assertNotIn('account_number', put.data)

        from rider.models import RiderProfile
        prof = RiderProfile.objects.get(rider=self.rider)
        self.assertTrue(prof.account_number_encrypted)
        self.assertNotIn('3333012345678', prof.account_number_encrypted)

    def test_profile_rejects_bad_method(self):
        self.client.force_authenticate(self.rider)
        resp = self.client.put('/api/v1/rider/profile',
                               {'delivery_method': 'rocket'}, format='json')
        self.assertEqual(resp.status_code, 400, resp.content)


class RiderDistanceFeeTest(APITestCase):
    def setUp(self):
        from orders.models import Order
        from restaurants.models import Restaurant
        from rider.models import Delivery
        self.rider = User.objects.create_user(
            username='dr@test.com', email='dr@test.com', password='Rider1234!',
            nickname='거리라이더', name='거리', phone='01012223333', role=User.Role.RIDER,
        )
        self.owner = User.objects.create_user(
            username='dro@test.com', email='dro@test.com', password='Owner1234!',
            nickname='점주D', name='점주', phone='01044445555', role=User.Role.OWNER,
        )
        self.customer = User.objects.create_user(
            username='drc@test.com', email='drc@test.com', password='Cust1234!',
            nickname='손님D', name='손님', phone='01066667777', role=User.Role.CUSTOMER,
        )
        rest = Restaurant.objects.create(owner=self.owner, name='교촌치킨', cuisine_type='치킨')
        order = Order.objects.create(user=self.customer, restaurant=rest, total=20000,
                                     status=Order.Status.DELIVERING)
        self.delivery = Delivery.objects.create(order=order, status=Delivery.Status.DELIVERING,
                                                rider=self.rider)

    def test_fee_scales_with_reported_distance(self):
        """앱이 보고한 거리로 배달료가 커진다(서버가 거리를 신뢰)."""
        self.client.force_authenticate(self.rider)
        resp = self.client.put(
            f'/api/v1/rider/deliveries/{self.delivery.id}/status',
            {'status': 'delivered', 'distance_km': 7.5}, format='json')
        self.assertEqual(resp.status_code, 200, resp.content)
        # 기본 3000 + 7.5km × 1000 = 10500
        self.assertEqual(resp.data['fee'], 10500)
        self.assertAlmostEqual(resp.data['distance_km'], 7.5)

    def test_spoofed_large_distance_inflates_fee(self):
        """거리 조작(과장 보고) 시 요금이 그대로 부풀려진다(의도된 취약점)."""
        self.client.force_authenticate(self.rider)
        resp = self.client.put(
            f'/api/v1/rider/deliveries/{self.delivery.id}/status',
            {'status': 'delivered', 'distance_km': 9999}, format='json')
        self.assertEqual(resp.status_code, 200, resp.content)
        self.assertEqual(resp.data['fee'], 3000 + 9999 * 1000)


class RiderMenusTest(APITestCase):
    def setUp(self):
        from restaurants.models import Menu, Restaurant
        self.rider = User.objects.create_user(
            username='rider3@test.com', email='rider3@test.com',
            password='Rider1234!', nickname='라이더3', name='최라이더',
            phone='01077778888', role=User.Role.RIDER,
        )
        self.owner = User.objects.create_user(
            username='owner3@test.com', email='owner3@test.com',
            password='Owner1234!', nickname='점주3', name='정점주',
            phone='01099990000', role=User.Role.OWNER,
        )
        self.rest = Restaurant.objects.create(owner=self.owner, name='교촌치킨 강남점', cuisine_type='치킨')
        Menu.objects.create(restaurant=self.rest, name='허니콤보', price=20000)
        Menu.objects.create(restaurant=self.rest, name='레드콤보', price=21000)
        Menu.objects.create(restaurant=self.rest, name='숨김메뉴', price=1000, status=Menu.Status.HIDDEN)

    def test_menus_returns_visible_with_restaurant_name(self):
        """전체 메뉴 목록이 매장명·가격과 함께 내려오고, 숨김 메뉴는 제외된다."""
        self.client.force_authenticate(self.rider)
        resp = self.client.get('/api/v1/rider/menus')
        self.assertEqual(resp.status_code, 200, resp.content)
        names = [m['name'] for m in resp.data['results']]
        self.assertIn('허니콤보', names)
        self.assertIn('레드콤보', names)
        self.assertNotIn('숨김메뉴', names)
        first = resp.data['results'][0]
        self.assertEqual(first['restaurant'], '교촌치킨 강남점')
        self.assertIn('price', first)
        self.assertIn('image', first)


class RiderIdorTest(APITestCase):
    """[의도된 취약점] IDOR/BOLA: 라이더 A 가 라이더 B 의 프로필/계좌에 접근·변조."""

    def setUp(self):
        self.attacker = User.objects.create_user(
            username='atk@test.com', email='atk@test.com', password='Rider1234!',
            nickname='공격자', name='공격', phone='01010101010', role=User.Role.RIDER,
        )
        self.victim = User.objects.create_user(
            username='vic@test.com', email='vic@test.com', password='Rider1234!',
            nickname='피해자', name='피해', phone='01020202020', role=User.Role.RIDER,
        )
        from accounts.crypto_utils import encrypt_aes128
        from rider.models import RiderProfile
        # 피해자는 정상적으로 자기 정산 계좌를 등록해 둔 상태.
        prof = RiderProfile.objects.create(
            rider=self.victim, bank_name='국민은행', account_holder='피해자')
        prof.account_number_encrypted = encrypt_aes128('110001112222')
        prof.save()

    def test_attacker_reads_victim_profile_via_idor(self):
        """공격자가 피해자 pk 로 프로필을 열람할 수 있다(소유권 검증 없음)."""
        self.client.force_authenticate(self.attacker)
        resp = self.client.get(f'/api/v1/riders/{self.victim.id}/profile')
        self.assertEqual(resp.status_code, 200, resp.content)
        self.assertEqual(resp.data['rider_id'], self.victim.id)
        self.assertEqual(resp.data['nickname'], '피해자')
        self.assertEqual(resp.data['bank_name'], '국민은행')

    def test_attacker_changes_victim_account_via_idor(self):
        """공격자가 피해자 계좌를 자기 것으로 바꾼다(정산금 탈취)."""
        self.client.force_authenticate(self.attacker)
        resp = self.client.put(
            f'/api/v1/riders/{self.victim.id}/account',
            {'account_number': '999888777666', 'account_holder': '공격자'},
            format='json')
        self.assertEqual(resp.status_code, 200, resp.content)
        from accounts.crypto_utils import decrypt_aes128
        from rider.models import RiderProfile
        prof = RiderProfile.objects.get(rider=self.victim)
        # 피해자 계좌가 공격자 값으로 바뀌었다.
        self.assertEqual(decrypt_aes128(prof.account_number_encrypted), '999888777666')

    def test_idor_endpoint_requires_rider_role(self):
        """비-라이더(고객)는 접근 불가(인증은 있으나 IsRider 통과 못함)."""
        customer = User.objects.create_user(
            username='cc@test.com', email='cc@test.com', password='Cust1234!',
            nickname='손님', name='손님', phone='01030303030', role=User.Role.CUSTOMER,
        )
        self.client.force_authenticate(customer)
        resp = self.client.get(f'/api/v1/riders/{self.victim.id}/profile')
        self.assertIn(resp.status_code, (401, 403))
