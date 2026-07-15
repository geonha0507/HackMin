package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.MessageResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * 챗봇 API (/api/v1/chatbot)
 */
public interface ChatbotApi {

    /** 챗봇 메시지 전송 */
    @POST("chatbot/message")
    Call<MessageResponse> sendMessage(@Body Map<String, String> body);
}
