package com.hackmin.app.ui.chat;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.app.R;
import com.hackmin.app.data.model.chat.ChatHistoryResponse;
import com.hackmin.app.data.model.chat.ChatMessageDto;
import com.hackmin.app.data.model.chat.ChatSendRequest;
import com.hackmin.app.data.model.chat.ChatSendResponse;
import com.hackmin.app.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 챗봇 상담 화면.
 * - GET  /chatbot/messages (이전 대화 이력)
 * - POST /chatbot/message  (메시지 전송)
 */
public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvMessages;
    private ProgressBar pbLoading;
    private ProgressBar pbSending;
    private TextInputEditText etInput;
    private ImageButton btnSend;
    private ChatMessageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        ImageButton btnBack = findViewById(R.id.btn_chat_back);
        rvMessages = findViewById(R.id.rv_chat_messages);
        pbLoading = findViewById(R.id.pb_chat_loading);
        pbSending = findViewById(R.id.pb_chat_sending);
        etInput = findViewById(R.id.et_chat_input);
        btnSend = findViewById(R.id.btn_chat_send);

        btnBack.setOnClickListener(v -> finish());

        adapter = new ChatMessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        loadHistory();
    }

    private void loadHistory() {
        pbLoading.setVisibility(View.VISIBLE);
        ApiClient.chatbotApi(this).getHistory().enqueue(new Callback<ChatHistoryResponse>() {
            @Override
            public void onResponse(Call<ChatHistoryResponse> call, Response<ChatHistoryResponse> response) {
                pbLoading.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    adapter.submit(response.body().getResults());
                    scrollToBottom();
                } else {
                    Toast.makeText(ChatActivity.this, "대화 이력을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ChatHistoryResponse> call, Throwable t) {
                pbLoading.setVisibility(View.GONE);
                Toast.makeText(ChatActivity.this, "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sendMessage() {
        String text = etInput.getText() != null ? etInput.getText().toString().trim() : "";
        if (text.isEmpty()) {
            return;
        }

        adapter.addMessage(new ChatMessageDto(ChatMessageDto.ROLE_USER, text));
        scrollToBottom();
        etInput.setText("");
        setSending(true);

        ApiClient.chatbotApi(this).sendMessage(new ChatSendRequest(text))
                .enqueue(new Callback<ChatSendResponse>() {
                    @Override
                    public void onResponse(Call<ChatSendResponse> call, Response<ChatSendResponse> response) {
                        setSending(false);
                        if (response.isSuccessful() && response.body() != null) {
                            adapter.addMessage(new ChatMessageDto(
                                    ChatMessageDto.ROLE_ASSISTANT, response.body().getReply()));
                            scrollToBottom();
                        } else {
                            Toast.makeText(ChatActivity.this, "챗봇 응답을 가져오지 못했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ChatSendResponse> call, Throwable t) {
                        setSending(false);
                        Toast.makeText(ChatActivity.this, "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setSending(boolean sending) {
        pbSending.setVisibility(sending ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!sending);
        etInput.setEnabled(!sending);
    }

    private void scrollToBottom() {
        int count = adapter.getItemCount();
        if (count > 0) {
            rvMessages.scrollToPosition(count - 1);
        }
    }

}
