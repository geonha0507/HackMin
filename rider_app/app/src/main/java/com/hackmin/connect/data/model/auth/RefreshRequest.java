package com.hackmin.connect.data.model.auth;

import com.google.gson.annotations.SerializedName;

public class RefreshRequest {

    @SerializedName("refresh")
    private String refreshToken;

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
