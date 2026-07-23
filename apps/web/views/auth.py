"""세션 기반 로그인/로그아웃 및 점주 회원가입 웹 화면."""

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


def _home_for(user):
    if user.role == User.Role.OWNER:
        return redirect('web:owner_dashboard')

    # 관리자 화면은 admin_web 앱(별도 컨테이너/도메인)으로 이동했다.
    # 이 컨테이너(owner-web)에는 admin 전용 라우트가 없으므로,
    # 관리자 계정 세션이 여기로 흘러들어와도 로그인 화면으로 되돌린다.
    return redirect('web:login')


def login_view(request):
    # 이 컨테이너(owner-web)는 owner 전용이다. admin 계정 세션이 있어도
    # 여기엔 admin_dashboard가 없으므로 owner일 때만 바로 홈으로 보낸다.
    if (
        request.user.is_authenticated
        and request.user.role == User.Role.OWNER
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

        elif user.role != User.Role.OWNER:
            messages.error(
                request,
                '점주 계정만 로그인할 수 있습니다.',
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
        and request.user.role == User.Role.OWNER
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
    }

    if request.method == 'POST':
        username = request.POST.get('username', '').strip()
        email = request.POST.get('email', '').strip()
        phone = request.POST.get('phone', '').strip()
        nickname = request.POST.get('nickname', '').strip()

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