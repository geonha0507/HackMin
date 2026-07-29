import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ('inquiries', '0002_inquiry_updated_at_alter_inquiry_category_and_more'),
    ]

    operations = [
        migrations.AddField(
            model_name='inquiry',
            name='answer',
            field=models.TextField(blank=True, default=''),
        ),
        migrations.AddField(
            model_name='inquiry',
            name='answered_at',
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name='inquiry',
            name='answered_by',
            field=models.ForeignKey(
                blank=True, null=True,
                on_delete=django.db.models.deletion.SET_NULL,
                related_name='answered_inquiries',
                to=settings.AUTH_USER_MODEL,
            ),
        ),
    ]
