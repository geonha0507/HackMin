## 🔄 마이그레이션

```bash
# 마이그레이션 생성
python manage.py makemigrations enrollment

# 마이그레이션 적용
python manage.py migrate
```

---

## 📌 API 엔드포인트

### 1. 입점 요청 제출

**요청:**
```http
POST /api/v1/enrollment/submit
Content-Type: multipart/form-data

username: owner1
password: password123
phone: 01012345678
owner_name: 김철수
restaurant_name: 철수네 치킨
business_license: <binary PDF/JPG>
```

**성공 응답 (201):**
```json
{
  "id": 1,
  "username": "owner1",
  "phone": "01012345678",
  "owner_name": "김철수",
  "restaurant_name": "철수네 치킨",
  "created_at": "2024-01-15T10:30:00Z"
}
```

**에러:**
- `duplicate_username`: 이미 등록된 사용자명
- `bad_request`: 필수 항목 누락

---

### 2. Admin: 대기 요청 목록 조회

**요청:**
```http
GET /api/v1/enrollment/list
Authorization: Token <admin_token>
```

**Query 파라미터:**
- `?status=pending` (기본값)
- `?status=approved`
- `?status=rejected`
- `?status=all`

**응답:**
```json
[
  {
    "id": 1,
    "username": "owner1",
    "phone": "01012345678",
    "owner_name": "김철수",
    "restaurant_name": "철수네 치킨",
    "business_license_url": "/media/enrollment_licenses/license.pdf",
    "status": "pending",
    "created_at": "2024-01-15T10:30:00Z",
    "reviewed_at": null,
    "reviewed_by_username": null,
    "rejection_reason": null
  }
]
```

---

### 3. Admin: 요청 상세 조회

**요청:**
```http
GET /api/v1/enrollment/1
Authorization: Token <admin_token>
```

**응답:**
```json
{
  "id": 1,
  "username": "owner1",
  "phone": "01012345678",
  "owner_name": "김철수",
  "restaurant_name": "철수네 치킨",
  "business_license_url": "/media/enrollment_licenses/license.pdf",
  "status": "pending",
  "created_at": "2024-01-15T10:30:00Z",
  "reviewed_at": null,
  "reviewed_by_username": null,
  "rejection_reason": null
}
```

---

### 4. Admin: 요청 승인

**요청:**
```http
POST /api/v1/enrollment/1/review
Authorization: Token <admin_token>
Content-Type: application/json

{
  "status": "approved"
}
```

**결과:**
- ✅ User 자동 생성 (username, hashed password)
- ✅ Restaurant 자동 생성 (owner, name, phone, business_license)
- ✅ 상태 → `approved`

**응답 (200):**
```json
{
  "id": 1,
  "status": "approved",
  "reviewed_at": "2024-01-15T10:35:00Z",
  "reviewed_by_username": "admin"
}
```

---

### 5. Admin: 요청 거절

**요청:**
```http
POST /api/v1/enrollment/1/review
Authorization: Token <admin_token>
Content-Type: application/json

{
  "status": "rejected",
  "rejection_reason": "서류 미비. 사업자등록증이 불명확합니다."
}
```

**결과:**
- ✅ User/Restaurant 미생성
- ✅ 상태 → `rejected`
- ✅ 거절 사유 저장

**응답 (200):**
```json
{
  "id": 1,
  "status": "rejected",
  "rejection_reason": "서류 미비. 사업자등록증이 불명확합니다.",
  "reviewed_at": "2024-01-15T10:35:00Z",
  "reviewed_by_username": "admin"
}
```

---

## 🔐 권한 설정

| 엔드포인트 | 권한 | 설명 |
|-----------|------|------|
| `POST /enrollment/submit/` | AllowAny | 누구나 입점 신청 가능 |
| `GET /enrollment/list/` | IsAdmin | Admin만 목록 조회 |
| `GET /enrollment/<id>/` | IsAdmin | Admin만 상세 조회 |
| `POST /enrollment/<id>/review/` | IsAdmin | Admin만 승인/거절 |

**Admin 계정:**
- `is_staff = True` 필수
- 또는 `is_superuser = True`

**Django Shell에서 설정:**
```python
from django.contrib.auth.models import User
user = User.objects.get(username='admin')
user.is_staff = True
user.is_superuser = True
user.save()
```

---

## 📊 DB 모델

### EnrollmentRequest 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | PK | 요청 ID |
| username | CharField | Owner 사용자명 (unique) |
| password | CharField | 암호화된 비밀번호 |
| phone | CharField | 전화번호 |
| owner_name | CharField | 점주명 |
| restaurant_name | CharField | 가게명 |
| business_license | FileField | 사업자등록증 (upload_to: 'enrollment_licenses/') |
| status | CharField | pending/approved/rejected |
| created_at | DateTimeField | 요청 제출 시간 |
| reviewed_at | DateTimeField | 승인/거절 시간 |
| reviewed_by_id | ForeignKey | Admin 계정 |
| rejection_reason | TextField | 거절 사유 |

---

## 🔒 보안 설정

### Vulnerable 모드
- 파일 확장자 검증 스킵 가능
- 파일 크기 검증 스킵 가능

### Secure 모드 (기본)
- ✅ 파일 확장자: `.pdf`, `.jpg`, `.jpeg`, `.png` 화이트리스트
- ✅ 파일 크기: 최대 10MB
- ✅ 비밀번호: Django `make_password()` 해싱
- ✅ 권한 검증: IsAdmin, AllowAny

---

## 🛠️ 문제 해결

### ImportError: cannot import name 'EnrollmentRequest'

**원인:** models.py 파일이 없음
**해결:** `apps/enrollment/models.py` 생성

### RuntimeError: Model class doesn't declare an explicit app_label

**원인:** settings.py에 'enrollment' 미등록
**해결:** `INSTALLED_APPS`에 'enrollment' 추가

### 파일 업로드 안됨

**해결:**
```bash
# MEDIA 폴더 생성
mkdir -p media/enrollment_licenses

# 권한 설정
chmod 755 media
```

### Admin 권한 오류

**해결:**
```python
from django.contrib.auth.models import User
user = User.objects.get(username='admin')
user.is_staff = True
user.save()
```

---

## ✅ 체크리스트

- [ ] 파일 배치 완료 (models.py, views.py, serializers.py 등)
- [ ] settings.py에 'enrollment' 추가
- [ ] urls.py에 enrollment 경로 추가
- [ ] 마이그레이션 실행
- [ ] Admin 계정 권한 설정 (is_staff=True)
- [ ] 미디어 폴더 생성
- [ ] API 테스트

---

## 🚀 빠른 시작

```bash
# 1. 마이그레이션
python manage.py makemigrations enrollment
python manage.py migrate

# 2. 서버 실행
python manage.py runserver

# 3. Admin 권한 설정
python manage.py shell
>>> from django.contrib.auth.models import User
>>> user = User.objects.get(username='admin')
>>> user.is_staff = True
>>> user.save()

# 4. API 테스트
curl -X POST http://localhost:8000/api/v1/enrollment/submit/ \
  -F "username=owner1" \
  -F "password=password123" \
  -F "phone=01012345678" \
  -F "owner_name=김철수" \
  -F "restaurant_name=철수네 치킨" \
  -F "business_license=@/path/to/license.pdf"
```
