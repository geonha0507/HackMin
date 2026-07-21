from django.db import migrations


def convert_blank_email_to_null(apps, schema_editor):
    User = apps.get_model('accounts', 'User')
    User.objects.filter(email='').update(email=None)


class Migration(migrations.Migration):

    dependencies = [
        ('accounts', '0004_alter_user_email'),
    ]

    operations = [
        migrations.RunPython(
            convert_blank_email_to_null,
            migrations.RunPython.noop,
        ),
    ]