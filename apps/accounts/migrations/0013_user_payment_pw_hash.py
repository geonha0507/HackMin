from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('accounts', '0012_user_rrn_hash'),
    ]

    operations = [
        migrations.AddField(
            model_name='user',
            name='payment_pw_hash',
            field=models.CharField(blank=True, default='', help_text='6자리 결제 비밀번호 해시(Django 해시). 평문은 저장하지 않는다.', max_length=128),
        ),
    ]
