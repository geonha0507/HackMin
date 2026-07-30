import reviews.models
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('reviews', '0005_review_soft_delete'),
    ]

    operations = [
        migrations.AlterField(
            model_name='reviewimage',
            name='image',
            field=models.ImageField(
                storage=reviews.models.review_image_storage,
                upload_to=reviews.models.review_image_upload_to,
            ),
        ),
    ]
