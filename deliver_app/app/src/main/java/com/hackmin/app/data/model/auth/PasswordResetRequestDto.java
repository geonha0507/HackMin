package com.hackmin.app.data.model.auth;

import com.google.gson.annotations.SerializedName;

public class PasswordResetRequestDto {

    @SerializedName("username_or_email")
    private String usernameOrEmail;

    public PasswordResetRequestDto(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }
}
