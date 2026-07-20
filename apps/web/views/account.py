"""마이페이지 (세션 인증 기반 웹).

`apps/owner/account.py` 의 REST API(/api/v1/owner/profile, /owner/password)와
동일한 필드·검증 규칙을 세션 기반 화면으로 제공한다.
"""

import re

from django.contrib import messages
from django.contrib.auth import update_session_auth_hash
from django.shortcuts import redirect, render

from accounts.models import WithdrawalRequest
from ..decorators import owner_required, role_required

EMAIL_RE = re.compile(r'^[^@\s]+@[^@\s]+\.[^@\s]+$')


@role_required('owner', 'admin')
def mypage(request):
    """내 정보 조회/수정."""
    user = request.user

    if request.method == 'POST':
        nickname = request.POST.get('nickname', '').strip()
        email = request.POST.get('email', '').strip()
        phone = request.POST.get('phone', '').strip()

        if email and not EMAIL_RE.match(email):
            messages.error(request, '올바른 이메일 형식이 아닙니다.')
        else:
            user.nickname = nickname
            user.email = email
            user.phone = phone
            user.save(update_fields=['nickname', 'email', 'phone'])
            messages.success(request, '내 정보가 저장되었습니다.')
            return redirect('web:mypage')

    pending_withdrawal = None
    if user.role == 'owner':
        pending_withdrawal = WithdrawalRequest.objects.filter(
            user=user, status=WithdrawalRequest.Status.PENDING,
        ).first()

    return render(request, 'web/mypage.html', {'u': user, 'pending_withdrawal': pending_withdrawal})


@role_required('owner', 'admin')
def password_change(request):
    """비밀번호 변경."""
    if request.method == 'POST':
        user = request.user
        old = request.POST.get('old_password', '')
        new = request.POST.get('new_password', '')
        new2 = request.POST.get('new_password2', '')

        if not user.check_password(old):
            messages.error(request, '현재 비밀번호가 올바르지 않습니다.')
        elif len(new) < 8:
            messages.error(request, '새 비밀번호는 8자 이상이어야 합니다.')
        elif new != new2:
            messages.error(request, '새 비밀번호와 확인이 일치하지 않습니다.')
        else:
            user.set_password(new)
            user.save(update_fields=['password'])
            update_session_auth_hash(request, user)  # 세션 유지
            messages.success(request, '비밀번호가 변경되었습니다.')

    return redirect('web:mypage')


@owner_required
def withdraw(request):
    """점주 탈퇴 요청. 비밀번호 확인 후 관리자 승인 대기 상태로 접수한다."""
    if request.method == 'POST':
        user = request.user
        password = request.POST.get('password', '')

        if not user.check_password(password):
            messages.error(request, '비밀번호가 올바르지 않습니다.')
        elif WithdrawalRequest.objects.filter(
            user=user, status=WithdrawalRequest.Status.PENDING,
        ).exists():
            messages.error(request, '이미 처리 대기 중인 탈퇴 요청이 있습니다.')
        else:
            WithdrawalRequest.objects.create(user=user)
            messages.success(request, '탈퇴 요청이 접수되었습니다. 관리자 승인 후 처리됩니다.')

    return redirect('web:mypage')
