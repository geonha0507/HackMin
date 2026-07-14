package com.hackmin.app.data.model.auth;

import com.google.gson.annotations.SerializedName;

public class RefreshResponse {

    @SerializedName("access")
    private String accessToken;

    public String getAccessToken() { return accessToken; }
}
