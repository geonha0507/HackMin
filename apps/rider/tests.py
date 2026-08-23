"""라이더 회원가입 + 실시간 위치(GPS relay) 엔드포인트 테스트."""

import base64
import json
import time
import uuid

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from django.contrib.auth import get_user_model
from rest_framework.test import APITestCase

User = get_user_model()


def _gen_txn_key():
    """테스트용 EC P-256 키쌍 생성 → (private_key, public_pem_str). 앱 Keystore 대역."""
    priv = ec.generate_private_key(ec.SECP256R1())
    pem = priv.public_key().public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo).decode()
    return priv, pem


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
        """두 번 보내면 새 행이 아니라 기존 위치를 덮어쓴다(근거리 이동)."""
        self.client.force_authenticate(self.rider)
        self.client.put('/api/v1/rider/location',
                        {'latitude': 37.0, 'longitude': 127.0}, format='json')
        self.client.put('/api/v1/rider/location',
                        {'latitude': 37.005, 'longitude': 127.0}, format='json')
        from rider.models import RiderLocation
        self.assertEqual(RiderLocation.objects.filter(rider=self.rider).count(), 1)
        # self.rider.location 은 첫 PUT 때 세팅된 역참조 캐시라 stale — DB에서 새로 조회한다.
        self.assertAlmostEqual(
            RiderLocation.objects.get(rider=self.rider).latitude, 37.005)

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

    def _send_track(self, km_total, steps=10):
        """가까운 좌표를 여러 번 보내 서버 누적거리를 ~km_total 만큼 만든다(각 구간 <2km)."""
        self.client.force_authenticate(self.rider)
        step_deg = (km_total / steps) / 111.0  # 위도 1도 ≈ 111km
        lat = 37.0
        self.client.put('/api/v1/rider/location',
                        {'latitude': lat, 'longitude': 127.0}, format='json')
        for _ in range(steps):
            lat += step_deg
            self.client.put('/api/v1/rider/location',
                            {'latitude': lat, 'longitude': 127.0}, format='json')

    def test_fee_from_server_observed_track(self):
        """배달료는 서버가 관측한 위치 트랙 거리로 산정된다(클라 보고 distance_km 무시)."""
        self._send_track(7.5, steps=10)
        self.client.force_authenticate(self.rider)
        resp = self.client.put(
            f'/api/v1/rider/deliveries/{self.delivery.id}/status',
            {'status': 'delivered', 'distance_km': 9999}, format='json')  # 클라값 무시됨
        self.assertEqual(resp.status_code, 200, resp.content)
        # 서버 관측 트랙 ~7.5km 기준(반올림 오차 허용). 9999 아님 = 클라 무시 증명.
        self.assertGreater(resp.data['distance_km'], 6.5)
        self.assertLess(resp.data['distance_km'], 8.5)
        self.assertGreater(resp.data['fee'], 9000)
        self.assertLess(resp.data['fee'], 12000)

    def test_spoofed_single_distance_ignored(self):
        """트랙 없이 distance_km만 크게 보내도 무시된다 → 기본료만(단발 조작 무력화)."""
        self.client.force_authenticate(self.rider)
        resp = self.client.put(
            f'/api/v1/rider/deliveries/{self.delivery.id}/status',
            {'status': 'delivered', 'distance_km': 9999}, format='json')
        self.assertEqual(resp.status_code, 200, resp.content)
        self.assertEqual(resp.data['distance_km'], 0)
        self.assertEqual(resp.data['fee'], 3000)

    def test_teleport_location_rejected(self):
        """단일 위치 갱신이 상한(2km)을 넘게 점프하면 거부된다(순간이동 차단)."""
        self.client.force_authenticate(self.rider)
        self.client.put('/api/v1/rider/location',
                        {'latitude': 37.0, 'longitude': 127.0}, format='json')
        resp = self.client.put('/api/v1/rider/location',
                               {'latitude': 35.1, 'longitude': 129.0}, format='json')  # 부산급
        self.assertEqual(resp.status_code, 400, resp.content)


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


class ReceiptConfirmGateTest(APITestCase):
    """[시나리오] 고객 수령확인 게이트.

    라이더가 '배달완료'로 바꿔도 배달료는 '정산 대기'일 뿐이고, 주문한 고객이
    수령확인을 해야 정산이 확정된다 → 고객앱(=고객 역할) 없이는 라이더가 돈을 못 받음.
    """

    def setUp(self):
        from orders.models import Order
        from restaurants.models import Restaurant
        from rider.models import Delivery
        self.rider = User.objects.create_user(
            username='sr@test.com', email='sr@test.com', password='Rider1234!',
            nickname='정산라이더', name='정산', phone='01011110000', role=User.Role.RIDER,
        )
        self.owner = User.objects.create_user(
            username='so@test.com', email='so@test.com', password='Owner1234!',
            nickname='점주S', name='점주', phone='01022220000', role=User.Role.OWNER,
        )
        self.customer = User.objects.create_user(
            username='sc@test.com', email='sc@test.com', password='Cust1234!',
            nickname='손님S', name='손님', phone='01033330000', role=User.Role.CUSTOMER,
        )
        rest = Restaurant.objects.create(owner=self.owner, name='정산분식', cuisine_type='분식')
        self.order = Order.objects.create(user=self.customer, restaurant=rest, total=20000,
                                          status=Order.Status.DELIVERING)
        self.delivery = Delivery.objects.create(order=self.order, rider=self.rider,
                                                status=Delivery.Status.DELIVERING)

    def _complete_delivery(self):
        self.client.force_authenticate(self.rider)
        return self.client.put(
            f'/api/v1/rider/deliveries/{self.delivery.id}/status',
            {'status': 'delivered'}, format='json')

    def _key_for(self, user):
        """user 별 EC P-256 키쌍을 만들어 수령확인 공개키로 등록(캐시). 앱 Keystore 대역."""
        cache = getattr(self, '_keys', None) or {}
        self._keys = cache
        if user.id not in cache:
            priv, pem = _gen_txn_key()
            self.client.force_authenticate(user)
            r = self.client.post('/api/v1/orders/receipt-key',
                                 {'key_id': 'rk1', 'public_key_pem': pem}, format='json')
            self.assertEqual(r.status_code, 201, r.content)
            cache[user.id] = priv
        return cache[user.id]

    def _confirm(self, user, order_id, sign=True):
        """수령확인 요청. sign=True 면 canonical 을 user 키로 ECDSA 서명해 헤더 부착."""
        path = f'/api/v1/orders/{order_id}/confirm-receipt'
        extra = {}
        if sign:
            priv = self._key_for(user)
            nonce = str(uuid.uuid4())
            ts = int(time.time() * 1000)
            canon = f'POST\n{path}\n{ts}\n{nonce}\n'.encode('utf-8')
            sig = priv.sign(canon, ec.ECDSA(hashes.SHA256()))
            extra = {
                'HTTP_X_RECEIPT_TS': str(ts),
                'HTTP_X_RECEIPT_NONCE': nonce,
                'HTTP_X_RECEIPT_SIG': base64.b64encode(sig).decode(),
                'HTTP_X_KEY_ID': 'rk1',
            }
        self.client.force_authenticate(user)
        return self.client.post(path, **extra)

    def test_delivered_is_pending_until_customer_confirms(self):
        """라이더 배달완료 직후엔 정산 대기(settled=False), earnings는 pending."""
        resp = self._complete_delivery()
        self.assertEqual(resp.status_code, 200, resp.content)
        self.assertFalse(resp.data['settled'])
        fee = resp.data['fee']
        self.client.force_authenticate(self.rider)
        earn = self.client.get('/api/v1/rider/earnings')
        self.assertEqual(earn.data['settled_total'], 0)
        self.assertEqual(earn.data['pending_total'], fee)  # 배달비는 아직 '대기'

    def test_customer_confirm_settles_fee(self):
        """[fix②③⑤] 고객 수령확인(유효 서명) → 정산 재계산에서 settled 로 이동."""
        comp = self._complete_delivery()
        fee = comp.data['fee']
        conf = self._confirm(self.customer, self.order.id)
        self.assertEqual(conf.status_code, 200, conf.content)
        self.assertTrue(conf.data['settled'])

        from rider.models import Delivery
        self.assertTrue(Delivery.objects.get(pk=self.delivery.id).settled)

        self.client.force_authenticate(self.rider)
        earn = self.client.get('/api/v1/rider/earnings')
        self.assertEqual(earn.data['settled_total'], fee)  # 서명 재검증+거리 도장 통과
        self.assertEqual(earn.data['pending_total'], 0)

    def test_confirm_before_delivered_rejected(self):
        """배달완료 전엔 수령확인 불가(409). (유효 서명이어도 상태 검사에서 막힘)"""
        resp = self._confirm(self.customer, self.order.id)
        self.assertEqual(resp.status_code, 409, resp.content)

    def test_non_owner_cannot_confirm(self):
        """남의 주문은 수령확인 불가(404). (타인이 자기 키로 서명해도 주문 소유 아님)"""
        self._complete_delivery()
        other = User.objects.create_user(
            username='sx@test.com', email='sx@test.com', password='Cust1234!',
            nickname='타인', name='타인', phone='01044440000', role=User.Role.CUSTOMER,
        )
        resp = self._confirm(other, self.order.id)
        self.assertEqual(resp.status_code, 404, resp.content)

    def test_sqli_distance_tamper_rejected_in_settlement(self):
        """[fix⑤] SQLi 로 distance_km/fee 를 부풀려도 거리 도장 불일치로 정산 무효."""
        self._complete_delivery()
        self._confirm(self.customer, self.order.id)
        from rider.models import Delivery
        # SQLi 시뮬레이션: 도장은 그대로 두고 거리·요금만 직접 조작
        Delivery.objects.filter(pk=self.delivery.id).update(
            distance_km=99999, fee=99_999_999)
        self.client.force_authenticate(self.rider)
        earn = self.client.get('/api/v1/rider/earnings')
        # 부풀린 금액이 안 잡힘(도장 불일치 → 미인정)
        self.assertEqual(earn.data['settled_total'], 0)

    def test_sqli_pubkey_swap_rejected_in_settlement(self):
        """[fix③ 앵커] SQLi 로 등록 공개키를 스왑하면 정산 재검증에서 탈락."""
        self._complete_delivery()
        self._confirm(self.customer, self.order.id)
        from rider.models import TxnKey
        _, other_pem = _gen_txn_key()
        # SQLi 시뮬레이션: reg_seal 은 그대로 두고 공개키만 공격자 키로 교체
        TxnKey.objects.filter(user=self.customer, key_id='rk1').update(
            public_key_pem=other_pem)
        self.client.force_authenticate(self.rider)
        earn = self.client.get('/api/v1/rider/earnings')
        self.assertEqual(earn.data['settled_total'], 0)   # 봉인 불일치 → 미인정


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
        # 계좌변경은 거래서명(Keystore EC)을 요구한다. 공격자의 키쌍(=루팅 후 오라클로
        # 확보한 앱의 서명 능력 대역)을 준비해 둔다.
        self.priv, self.pem = _gen_txn_key()
        self.key_id = 'atk-key'

    def _register_key(self):
        self.client.force_authenticate(self.attacker)
        r = self.client.post('/api/v1/rider/txn-key',
                             {'key_id': self.key_id, 'public_key_pem': self.pem},
                             format='json')
        self.assertEqual(r.status_code, 201, r.content)

    def _account_change(self, target_id, body_dict, nonce=None, ts=None, sign=True):
        """계좌변경 요청. sign=True 면 canonical 을 공격자 키로 ECDSA 서명해 헤더 부착."""
        self.client.force_authenticate(self.attacker)
        path = f'/api/v1/riders/{target_id}/account'
        body = json.dumps(body_dict)
        extra = {}
        if sign:
            nonce = nonce or str(uuid.uuid4())
            ts = ts if ts is not None else int(time.time() * 1000)
            canon = f'PUT\n{path}\n{ts}\n{nonce}\n{body}'.encode('utf-8')
            sig = self.priv.sign(canon, ec.ECDSA(hashes.SHA256()))
            extra = {
                'HTTP_X_TXN_TS': str(ts),
                'HTTP_X_TXN_NONCE': nonce,
                'HTTP_X_TXN_SIG': base64.b64encode(sig).decode(),
                'HTTP_X_KEY_ID': self.key_id,
            }
        return self.client.put(path, data=body, content_type='application/json', **extra)

    def test_attacker_reads_victim_profile_via_idor(self):
        """공격자가 피해자 pk 로 프로필을 열람할 수 있다(소유권 검증 없음, GET은 서명 불요)."""
        self.client.force_authenticate(self.attacker)
        resp = self.client.get(f'/api/v1/riders/{self.victim.id}/profile')
        self.assertEqual(resp.status_code, 200, resp.content)
        self.assertEqual(resp.data['rider_id'], self.victim.id)
        self.assertEqual(resp.data['nickname'], '피해자')
        self.assertEqual(resp.data['bank_name'], '국민은행')

    def test_account_change_without_signature_rejected(self):
        """[무루팅 커스텀 클라] 서명 없이 계좌변경 → 401. 하드웨어 서명이 필수라 막힘."""
        resp = self._account_change(
            self.victim.id, {'account_number': '999888777666'}, sign=False)
        self.assertEqual(resp.status_code, 401, resp.content)

    def test_attacker_changes_victim_account_with_signature(self):
        """[루팅+서명 오라클] 공격자가 '자기 키로 서명'해 피해자 계좌를 바꾼다.

        서명은 '진짜 앱 인스턴스가 보냄'만 증명 → 소유권 미검사(IDOR)라 남의 계좌가 바뀐다.
        """
        self._register_key()
        resp = self._account_change(
            self.victim.id, {'account_number': '999888777666', 'account_holder': '공격자'})
        self.assertEqual(resp.status_code, 200, resp.content)
        from accounts.crypto_utils import decrypt_aes128
        from rider.models import RiderProfile
        prof = RiderProfile.objects.get(rider=self.victim)
        self.assertEqual(decrypt_aes128(prof.account_number_encrypted), '999888777666')

    def test_replayed_nonce_rejected(self):
        """캡처한 서명 재전송(같은 nonce) → 401. 재전송 방지."""
        self._register_key()
        nonce = str(uuid.uuid4())
        r1 = self._account_change(self.victim.id, {'account_number': '111111111111'}, nonce=nonce)
        self.assertEqual(r1.status_code, 200, r1.content)
        r2 = self._account_change(self.victim.id, {'account_number': '222222222222'}, nonce=nonce)
        self.assertEqual(r2.status_code, 401, r2.content)

    def test_idor_endpoint_requires_rider_role(self):
        """비-라이더(고객)는 접근 불가(인증은 있으나 IsRider 통과 못함)."""
        customer = User.objects.create_user(
            username='cc@test.com', email='cc@test.com', password='Cust1234!',
            nickname='손님', name='손님', phone='01030303030', role=User.Role.CUSTOMER,
        )
        self.client.force_authenticate(customer)
        resp = self.client.get(f'/api/v1/riders/{self.victim.id}/profile')
        self.assertIn(resp.status_code, (401, 403))


class PayoutBatchTest(APITestCase):
    """[정산 배치 + IDOR-절도] 지급 로직 검증.

    · 정상: 재검증된 정산금이 라이더 '본인 계좌'로 지급
    · IDOR-절도: 계좌가 공격자 것으로 바뀌면 피해자 정산금이 공격자 계좌로 지급
    · 방어: SQLi 로 위조한 정산(거리 도장 불일치)은 지급 배치에서도 제외
    """

    def setUp(self):
        from orders.models import Order
        from restaurants.models import Restaurant
        from rider.models import Delivery
        self.rider = User.objects.create_user(
            username='pr@test.com', email='pr@test.com', password='Rider1234!',
            nickname='지급라이더', name='지급', phone='01088880000', role=User.Role.RIDER)
        self.owner = User.objects.create_user(
            username='po@test.com', email='po@test.com', password='Owner1234!',
            nickname='점주P', name='점주', phone='01088881111', role=User.Role.OWNER)
        self.customer = User.objects.create_user(
            username='pc@test.com', email='pc@test.com', password='Cust1234!',
            nickname='손님P', name='손님', phone='01088882222', role=User.Role.CUSTOMER)
        rest = Restaurant.objects.create(owner=self.owner, name='지급식당', cuisine_type='분식')
        self.order = Order.objects.create(user=self.customer, restaurant=rest, total=20000,
                                          status=Order.Status.DELIVERING)
        self.delivery = Delivery.objects.create(order=self.order, rider=self.rider,
                                                status=Delivery.Status.DELIVERING)
        self._set_account(self.rider, '3333000011112222')   # 본인 계좌(끝 2222)

    def _set_account(self, rider, number, bank='국민은행', holder='홍길동'):
        from rider.models import RiderProfile
        from accounts.crypto_utils import encrypt_aes128
        RiderProfile.objects.update_or_create(
            rider=rider,
            defaults={'bank_name': bank, 'account_holder': holder,
                      'account_number_encrypted': encrypt_aes128(number)})

    def _earn_one_delivery(self):
        """배달 완료 + 고객 유효 서명 수령확인 → 정산 확정 1건. 반환: fee."""
        self.client.force_authenticate(self.rider)
        comp = self.client.put(f'/api/v1/rider/deliveries/{self.delivery.id}/status',
                               {'status': 'delivered'}, format='json')
        fee = comp.data['fee']
        priv, pem = _gen_txn_key()
        self.client.force_authenticate(self.customer)
        self.client.post('/api/v1/orders/receipt-key',
                         {'key_id': 'rk1', 'public_key_pem': pem}, format='json')
        path = f'/api/v1/orders/{self.order.id}/confirm-receipt'
        ts = int(time.time() * 1000)
        nonce = str(uuid.uuid4())
        canon = f'POST\n{path}\n{ts}\n{nonce}\n'.encode('utf-8')
        sig = base64.b64encode(priv.sign(canon, ec.ECDSA(hashes.SHA256()))).decode()
        self.client.post(path, HTTP_X_RECEIPT_TS=str(ts), HTTP_X_RECEIPT_NONCE=nonce,
                         HTTP_X_RECEIPT_SIG=sig, HTTP_X_KEY_ID='rk1')
        return fee

    def test_legit_payout_to_own_account(self):
        """정상: 재검증된 정산금이 본인 계좌로 지급되고, 재실행 시 중복 지급 없음."""
        from rider.views import run_payout_batch
        from rider.models import RiderPayout, Delivery
        fee = self._earn_one_delivery()
        results = run_payout_batch()
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]['amount'], fee)
        self.assertTrue(results[0]['account'].endswith('2222'))     # 본인 계좌
        self.assertEqual(RiderPayout.objects.get(rider=self.rider).amount, fee)
        self.assertTrue(Delivery.objects.get(pk=self.delivery.id).paid_out)
        self.assertEqual(run_payout_batch(), [])                    # 중복 지급 방지

    def test_idor_account_swap_steals_payout(self):
        """[IDOR-절도] 피해자 계좌를 공격자 것으로 바꾸면 피해자 정산금이 공격자 계좌로 지급."""
        from rider.views import run_payout_batch
        fee = self._earn_one_delivery()
        # IDOR 결과 시뮬: 피해자 계좌를 공격자 계좌(끝 9999)로 스왑
        self._set_account(self.rider, '7777000099999999', bank='공격자은행', holder='공격자')
        results = run_payout_batch()
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]['amount'], fee)                 # 피해자 실적 금액
        self.assertTrue(results[0]['account'].endswith('9999'))     # 공격자 계좌로 지급됨
        self.assertEqual(results[0]['bank'], '공격자은행')

    def test_forged_settlement_not_paid(self):
        """[fix⑤] SQLi 로 거리 위조한 정산은 지급 배치에서도 제외(도장 불일치)."""
        from rider.views import run_payout_batch
        from rider.models import Delivery
        self._earn_one_delivery()
        Delivery.objects.filter(pk=self.delivery.id).update(distance_km=99999, fee=99_999_999)
        self.assertEqual(run_payout_batch(), [])
