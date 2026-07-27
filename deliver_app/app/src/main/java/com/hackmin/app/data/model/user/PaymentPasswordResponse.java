package com.hackmin.app.data.model.user;

import com.google.gson.annotations.SerializedName;

/** 결제 비밀번호 설정 여부(is_set) / 검증 결과(valid) 응답. */
public class PaymentPasswordResponse {
    @SerializedName("is_set") private boolean isSet;
    @SerializedName("valid") private boolean valid;

    public boolean isSet() { return isSet; }
    public boolean isValid() { return valid; }
}
