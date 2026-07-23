"""세션 기반 로그인/로그아웃 및 점주 회원가입 웹 화면."""
import hashlib
from accounts.crypto_utils import encrypt_aes128  # 실제 AES 함수가 있는 파일 경로에 맞게 수정하세요
import os
from enrollment.models import EnrollmentRequest

from django.contrib import messages
from django.contrib.auth import (
    authenticate,
    get_user_model,
    login as auth_login,
    logout as auth_logout,
)
from django.db import IntegrityError, transaction
from django.shortcuts import redirect, render

from restaurants.models import Restaurant


User = get_user_model()

_ALLOWED_LICENSE_EXTENSIONS = {'.pdf', '.jpg', '.jpeg', '.png'}
_MAX_LICENSE_SIZE = 10 * 1024 * 1024  # 10MB

def _is_valid_rrn(rrn):
    """13자리 주민등록번호 형식과 체크섬을 검증한다."""
    digits = rrn.replace('-', '')
    if not digits.isdigit() or len(digits) != 13:
        return False

    weights = [2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5]
    total = sum(int(d) * w for d, w in zip(digits[:12], weights))
    check_digit = (11 - (total % 11)) % 10

    return check_digit == int(digits[12])


def _hash_rrn(rrn):
    """중복 확인 전용 해시. 단방향이라 이 값만으로는 원본 복원이 불가능하다."""
    digits = rrn.replace('-', '')
    return hashlib.sha256(digits.encode()).hexdigest()


def _home_for(user):
    if user.role == User.Role.ADMIN:
        return redirect('web:admin_dashboard')

    if user.role == User.Role.OWNER:
        return redirect('web:owner_dashboard')

    return redirect('web:login')


def login_view(request):
    if (
        request.user.is_authenticated
        and request.user.role in (
            User.Role.ADMIN,
            User.Role.OWNER,
        )
    ):
        return _home_for(request.user)

    if request.method == 'POST':
        username = request.POST.get(
            'username',
            '',
        ).strip()

        password = request.POST.get(
            'password',
            '',
        )

        candidate = User.objects.filter(
            username=username,
        ).first()

        # 비밀번호가 맞는 점주 계정은
        # authenticate 이전에 신청 상태를 확인한다.
        if (
            candidate
            and candidate.role == User.Role.OWNER
            and candidate.check_password(password)
        ):
            if candidate.status == User.Status.PENDING:
                messages.error(
                    request,
                    '입점 승인 대기 중인 계정입니다.',
                )
                return render(
                    request,
                    'web/login.html',
                    {
                        'next': (
                            request.GET.get('next')
                            or request.POST.get('next')
                            or ''
                        ),
                        'hide_chrome': True,
                    },
                )

            if candidate.status == User.Status.SUSPENDED:
                enrollment = (
                    EnrollmentRequest.objects
                    .filter(
                        restaurant__owner=candidate,
                        status='rejected',
                    )
                    .only('rejection_reason')
                    .first()
                )

                if (
                    enrollment
                    and enrollment.rejection_reason
                ):
                    messages.error(
                        request,
                        '입점 신청이 반려되었습니다. '
                        f'반려 사유: '
                        f'{enrollment.rejection_reason}',
                    )
                else:
                    messages.error(
                        request,
                        '비활성화된 계정입니다. '
                        '관리자에게 문의하세요.',
                    )

                return render(
                    request,
                    'web/login.html',
                    {
                        'next': (
                            request.GET.get('next')
                            or request.POST.get('next')
                            or ''
                        ),
                        'hide_chrome': True,
                    },
                )

        # 승인된 계정 등은 기존 인증 흐름으로 처리
        user = authenticate(
            request,
            username=username,
            password=password,
        )

        if user is None:
            messages.error(
                request,
                '아이디 또는 비밀번호가 올바르지 않습니다.',
            )

        elif user.role not in (
            User.Role.ADMIN,
            User.Role.OWNER,
        ):
            messages.error(
                request,
                '관리자 또는 점주 계정만 로그인할 수 있습니다.',
            )

        elif (
            user.status != User.Status.ACTIVE
            or not user.is_active
        ):
            messages.error(
                request,
                '비활성화된 계정입니다. '
                '관리자에게 문의하세요.',
            )

        else:
            auth_login(request, user)

            nxt = (
                request.GET.get('next')
                or request.POST.get('next')
            )

            if nxt:
                return redirect(nxt)

            return _home_for(user)

    return render(
        request,
        'web/login.html',
        {
            'next': request.GET.get('next', ''),
            'hide_chrome': True,
        },
    )


def signup_view(request):
    """점주 계정과 매장 입점 신청 정보를 함께 생성한다."""

    if (
        request.user.is_authenticated
        and request.user.role in (User.Role.ADMIN, User.Role.OWNER)
    ):
        return _home_for(request.user)

    form_data = {
        'username': '',
        'email': '',
        'phone': '',
        'nickname': '',
        'store_name': '',
        'store_phone': '',
        'store_postcode': '',
        'store_road_address': '',
        'store_detail_address': '',
        # rrn은 민감정보라 비밀번호처럼 화면에 다시 채워주지 않는다.
    }

    if request.method == 'POST':
        username = request.POST.get('username', '').strip()
        email = request.POST.get('email', '').strip()
        phone = request.POST.get('phone', '').strip()
        nickname = request.POST.get('nickname', '').strip()
        rrn = request.POST.get('rrn', '').strip()

        store_name = request.POST.get('store_name', '').strip()
        store_phone = request.POST.get('store_phone', '').strip()
        store_postcode = request.POST.get('store_postcode', '').strip()
        store_road_address = request.POST.get('store_road_address', '').strip()
        store_detail_address = request.POST.get('store_detail_address', '').strip()

        password = request.POST.get('password', '')
        password_confirm = request.POST.get(
            'password_confirm',
            '',
        )

        business_license = request.FILES.get(
            'business_license',
        )

        form_data = {
            'username': username,
            'email': email,
            'phone': phone,
            'nickname': nickname,
            'store_name': store_name,
            'store_phone': store_phone,
            'store_postcode': store_postcode,
            'store_road_address': store_road_address,
            'store_detail_address': store_detail_address,
        }

        error_found = False

        if not username:
            messages.error(request, '아이디를 입력하세요.')
            error_found = True

        elif not email:
            messages.error(request, '이메일을 입력하세요.')
            error_found = True

        elif not nickname:
            messages.error(request, '점주명을 입력하세요.')
            error_found = True

        elif not rrn:
            messages.error(request, '주민등록번호를 입력하세요.')
            error_found = True

        elif not _is_valid_rrn(rrn):
            messages.error(
                request,
                '주민등록번호 형식이 올바르지 않습니다.',
            )
            error_found = True

        elif User.objects.filter(rrn_hash=_hash_rrn(rrn)).exists():
            messages.error(
                request,
                '이미 등록된 주민등록번호입니다.',
            )
            error_found = True

        elif not store_name:
            messages.error(request, '매장명을 입력하세요.')
            error_found = True

        elif not store_postcode or not store_road_address:
            messages.error(request, '주소 검색으로 매장 주소를 입력하세요.')
            error_found = True

        elif not store_detail_address:
            messages.error(request, '상세 주소를 입력하세요.')
            error_found = True

        elif not business_license:
            messages.error(
                request,
                '사업자등록증 파일을 첨부하세요.',
            )
            error_found = True

        elif not password:
            messages.error(request, '비밀번호를 입력하세요.')
            error_found = True

        elif len(password) < 8:
            messages.error(
                request,
                '비밀번호는 8자 이상 입력하세요.',
            )
            error_found = True

        elif password != password_confirm:
            messages.error(
                request,
                '비밀번호가 일치하지 않습니다.',
            )
            error_found = True

        elif User.objects.filter(username=username).exists():
            messages.error(
                request,
                '이미 사용 중인 아이디입니다.',
            )
            error_found = True

        elif User.objects.filter(
            email__iexact=email,
        ).exists():
            messages.error(
                request,
                '이미 사용 중인 이메일입니다.',
            )
            error_found = True

        # 파일 서버 측 검증
        if not error_found and business_license:
            extension = os.path.splitext(
                business_license.name,
            )[1].lower()

            if extension not in _ALLOWED_LICENSE_EXTENSIONS:
                messages.error(
                    request,
                    '사업자등록증은 PDF, JPG, JPEG, PNG 파일만 가능합니다.',
                )
                error_found = True

            elif business_license.size > _MAX_LICENSE_SIZE:
                messages.error(
                    request,
                    '사업자등록증 파일은 10MB 이하만 가능합니다.',
                )
                error_found = True

        if not error_found:
            store_address = (
                f'[{store_postcode}] {store_road_address} {store_detail_address}'
            )

            try:
                # User와 Restaurant 중 하나만 저장되는 일을 막는다.
                with transaction.atomic():
                    owner = User.objects.create_user(
                        username=username,
                        password=password,
                        email=email,
                        phone=phone,
                        nickname=nickname,
                        role=User.Role.OWNER,

                        # 관리자 승인 전 상태
                        status=User.Status.PENDING,

                        # 비밀번호 인증은 가능하게 두되,
                        # login_view에서 pending 계정을 차단한다.
                        is_active=True,

                        rrn_hash=_hash_rrn(rrn),
                        rrn_encrypted=encrypt_aes128(rrn),
                    )

                    Restaurant.objects.create(
                        owner=owner,
                        name=store_name,
                        phone=store_phone or phone,
                        address=store_address,
                        business_license=business_license,

                        # 승인 전에는 영업 중으로 표시하지 않음
                        is_open=False,
                    )

            except IntegrityError:
                messages.error(
                    request,
                    '이미 사용 중인 계정 정보입니다.',
                )

            except Exception:
                messages.error(
                    request,
                    '입점 신청 처리 중 오류가 발생했습니다.',
                )

            else:
                messages.success(
                    request,
                    '입점 신청이 완료되었습니다. 관리자 승인 후 로그인할 수 있습니다.',
                )
                return redirect('web:login')

    return render(
        request,
        'web/signup.html',
        {
            'form_data': form_data,
            'hide_chrome': True,
        },
    )


def logout_view(request):
    auth_logout(request)
    messages.success(
        request,
        '로그아웃되었습니다.',
    )
    return redirect('web:login')