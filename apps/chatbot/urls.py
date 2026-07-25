from django.urls import path

from . import views

app_name = 'chatbot'

urlpatterns = [
    path('chatbot/message', views.chatbot_message, name='message'),
    path('chatbot/messages', views.chatbot_history, name='history'),
    path('chatbot/session', views.chatbot_reset_session, name='reset_session'),
]
