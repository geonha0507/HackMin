package com.hackmin.connect.data.model.auth;

import com.google.gson.annotations.SerializedName;

/** 라이더 회원가입 요청. deliver_app과 같은 /auth/signup 계약에 role=rider 를 추가로 보낸다. */
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

    @SerializedName("role")
    private String role;

    @SerializedName("terms_agreed")
    private boolean termsAgreed;

    public SignupRequest(String username, String email, String password,
                         String nickname, String name, String phone,
                         boolean termsAgreed) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.name = name;
        this.phone = phone;
        this.role = "rider";
        this.termsAgreed = termsAgreed;
    }
}
