package com.hackmin.app.data.model.auth;

import com.google.gson.annotations.SerializedName;

public class LoginRequest {

    @SerializedName("username")
    private String username;

    @SerializedName("password")
    private String password;

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
