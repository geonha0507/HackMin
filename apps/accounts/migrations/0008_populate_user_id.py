import uuid
from django.db import migrations


def populate_user_ids(apps, schema_editor):
    User = apps.get_model('accounts', 'User')
    for user in User.objects.all():
        user.user_id = uuid.uuid4()
        user.save(update_fields=['user_id'])


class Migration(migrations.Migration):

    dependencies = [
        ('accounts', '0007_user_user_id'),
    ]

    operations = [
        migrations.RunPython(populate_user_ids, migrations.RunPython.noop),
    ]
