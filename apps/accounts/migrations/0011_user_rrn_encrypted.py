# Generated migration for adding rrn_encrypted field to User model

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('accounts', '0010_user_name'),
    ]

    operations = [
        migrations.AddField(
            model_name='user',
            name='rrn_encrypted',
            field=models.CharField(
                blank=True,
                help_text='주민등록번호 (AES-128 암호화)',
                max_length=255,
                null=True,
            ),
        ),
    ]