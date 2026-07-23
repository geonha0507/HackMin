package com.hackmin.app.data.api;

import com.hackmin.app.data.model.chat.ChatHistoryResponse;
import com.hackmin.app.data.model.chat.ChatSendRequest;
import com.hackmin.app.data.model.chat.ChatSendResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * 챗봇 API (/api/v1/chatbot)
 */
public interface ChatbotApi {

    /** 챗봇 메시지 전송 */
    @POST("chatbot/message")
    Call<ChatSendResponse> sendMessage(@Body ChatSendRequest request);

    /** 대화 이력 조회 */
    @GET("chatbot/messages")
    Call<ChatHistoryResponse> getHistory();
}
