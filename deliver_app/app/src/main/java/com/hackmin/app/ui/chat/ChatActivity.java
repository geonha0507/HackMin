package com.hackmin.app.ui.chat;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
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

import java.util.Collections;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 챗봇 상담 화면.
 * - GET  /chatbot/messages (이전 대화 이력)
 * - POST /chatbot/message  (메시지 전송)
 */
public class ChatActivity extends com.hackmin.app.ui.common.BaseActivity {

    /** 대화 이력이 비어 있을 때(신규 진입/초기화 직후) 보여줄 인사말. 서버로 전송되거나 저장되지 않는 화면 전용 문구다. */
    private static final String GREETING_TEXT =
            "안녕하세요! 해킹의 민족 AI 상담원입니다. 무엇을 도와드릴까요?\n\n"
                    + "예시: \"내 최근 주문 내역 보여줘\"\n"
                    + "예시: \"교촌치킨 강남점 정보 알려줘\"";

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
        com.hackmin.app.ui.common.EdgeToEdgeUtil.apply(this);

        ImageButton btnBack = findViewById(R.id.btn_chat_back);
        ImageButton btnReset = findViewById(R.id.btn_chat_reset);
        rvMessages = findViewById(R.id.rv_chat_messages);
        pbLoading = findViewById(R.id.pb_chat_loading);
        pbSending = findViewById(R.id.pb_chat_sending);
        etInput = findViewById(R.id.et_chat_input);
        btnSend = findViewById(R.id.btn_chat_send);

        btnBack.setOnClickListener(v -> leaveChat());
        btnReset.setOnClickListener(v -> confirmResetChat());

        // 실제 챗봇 상담창처럼, 화면을 나가면(뒤로가기 제스처 포함) 대화를 초기화한다.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leaveChat();
            }
        });

        adapter = new ChatMessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        // 엔터(전송) 키로도 메시지 전송.
        etInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        etInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        etInput.setOnEditorActionListener((tv, actionId, event) -> {
            boolean enterDown = event != null
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN;
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND || enterDown) {
                sendMessage();
                return true;
            }
            return false;
        });

        loadHistory();
    }

    private void loadHistory() {
        pbLoading.setVisibility(View.VISIBLE);
        ApiClient.chatbotApi(this).getHistory().enqueue(new Callback<ChatHistoryResponse>() {
            @Override
            public void onResponse(Call<ChatHistoryResponse> call, Response<ChatHistoryResponse> response) {
                pbLoading.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().getResults() == null || response.body().getResults().isEmpty()) {
                        showGreeting();
                    } else {
                        adapter.submit(response.body().getResults());
                    }
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

    /** 화면을 나갈 때 세션을 조용히 초기화한다(응답을 기다리지 않고 바로 종료). */
    private void leaveChat() {
        ApiClient.chatbotApi(this).resetSession().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) { /* no-op */ }

            @Override
            public void onFailure(Call<Void> call, Throwable t) { /* no-op */ }
        });
        finish();
    }

    private void confirmResetChat() {
        new AlertDialog.Builder(this)
                .setTitle("대화 초기화")
                .setMessage("지금까지의 대화 내용을 모두 삭제할까요?")
                .setPositiveButton("초기화", (d, w) -> resetChat())
                .setNegativeButton("취소", null)
                .show();
    }

    private void resetChat() {
        ApiClient.chatbotApi(this).resetSession().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    showGreeting();
                    Toast.makeText(ChatActivity.this, "대화가 초기화되었습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChatActivity.this, "초기화에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    /** 대화 이력이 없을 때 화면에만 표시하는 인사말 말풍선(서버 저장 X). */
    private void showGreeting() {
        adapter.submit(Collections.singletonList(
                new ChatMessageDto(ChatMessageDto.ROLE_ASSISTANT, GREETING_TEXT)));
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
