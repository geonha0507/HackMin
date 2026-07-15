package com.hackmin.app.data.model.auth;

import com.google.gson.annotations.SerializedName;

public class SignupRequest {

    @SerializedName("username")
    private String username;

    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("nickname")
    private String name;

    @SerializedName("phone")
    private String phone;

    @SerializedName("terms_agreed")
    private boolean termsAgreed;

    public SignupRequest(String username, String email, String password,
                          String name, String phone, boolean termsAgreed) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.termsAgreed = termsAgreed;
    }
}
