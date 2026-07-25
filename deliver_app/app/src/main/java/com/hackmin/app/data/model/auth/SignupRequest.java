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
    private String nickname;

    @SerializedName("name")
    private String name;

    @SerializedName("phone")
    private String phone;

    @SerializedName("rrn")
    private String rrn;

    @SerializedName("terms_agreed")
    private boolean termsAgreed;

    public SignupRequest(String username, String email, String password,
                         String nickname, String name, String phone,
                         String rrn, boolean termsAgreed) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.name = name;
        this.phone = phone;
        this.rrn = rrn;
        this.termsAgreed = termsAgreed;
    }
}
