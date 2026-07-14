package com.hackmin.app.data.model.auth;

import com.google.gson.annotations.SerializedName;

public class PasswordResetDto {

    @SerializedName("reset_token")
    private String resetToken;

    @SerializedName("new_password")
    private String newPassword;

    public PasswordResetDto(String resetToken, String newPassword) {
        this.resetToken = resetToken;
        this.newPassword = newPassword;
    }
}
