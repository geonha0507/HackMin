package com.hackmin.app.data.model.user;

import com.google.gson.annotations.SerializedName;

public class UpdateProfileRequest {
    @SerializedName("email") private String email;
    @SerializedName("phone") private String phone;
    @SerializedName("nickname") private String nickname;

    public UpdateProfileRequest(String email, String phone, String nickname) {
        this.email = email;
        this.phone = phone;
        this.nickname = nickname;
    }
}
