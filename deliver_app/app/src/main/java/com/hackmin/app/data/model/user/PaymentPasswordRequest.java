package com.hackmin.app.data.model.user;

import com.google.gson.annotations.SerializedName;

/** 결제 비밀번호(6자리) 설정/검증 요청. 서버는 해시로만 저장한다. */
public class PaymentPasswordRequest {
    @SerializedName("password") private String password;

    public PaymentPasswordRequest(String password) {
        this.password = password;
    }
}
