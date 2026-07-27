from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('accounts', '0012_user_rrn_hash'),
    ]

    operations = [
        migrations.AddField(
            model_name='user',
            name='payment_pw_hash',
            field=models.CharField(blank=True, default='', max_length=128),
        ),
    ]
