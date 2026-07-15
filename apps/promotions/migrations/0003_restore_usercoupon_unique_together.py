from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ('promotions', '0002_alter_usercoupon_unique_together'),
    ]

    operations = [
        migrations.AlterUniqueTogether(
            name='usercoupon',
            unique_together={('user', 'coupon')},
        ),
    ]
