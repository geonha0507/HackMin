from django.db import migrations, models

import restaurants.models


class Migration(migrations.Migration):

    dependencies = [
        ('restaurants', '0005_menu_is_membership_only'),
    ]

    operations = [
        migrations.AddField(
            model_name='restaurant',
            name='image',
            field=models.ImageField(
                blank=True,
                null=True,
                upload_to=restaurants.models.restaurant_image_upload_to,
            ),
        ),
    ]
