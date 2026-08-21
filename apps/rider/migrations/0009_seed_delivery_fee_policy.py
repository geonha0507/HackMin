"""배달비 정책 단일행(id=1) 시드.

정상 기준값: 기본료 3000, km당 1000, 상한 50000.
이 행이 adminpanel SQLi(stacked UPDATE)의 표적이 된다(fee_per_km/max_fee_krw).
"""
from django.db import migrations


def seed(apps, schema_editor):
    Policy = apps.get_model('rider', 'DeliveryFeePolicy')
    Policy.objects.update_or_create(
        pk=1,
        defaults={'base_fee_krw': 3000, 'fee_per_km': 1000, 'max_fee_krw': 50000},
    )


def unseed(apps, schema_editor):
    Policy = apps.get_model('rider', 'DeliveryFeePolicy')
    Policy.objects.filter(pk=1).delete()


class Migration(migrations.Migration):

    dependencies = [
        ('rider', '0008_deliveryfeepolicy'),
    ]

    operations = [
        migrations.RunPython(seed, unseed),
    ]
