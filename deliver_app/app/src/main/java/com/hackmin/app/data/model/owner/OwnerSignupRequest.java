package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class OwnerSignupRequest {
    @SerializedName("username") private String username;
    @SerializedName("password") private String password;
    @SerializedName("nickname") private String nickname;
    @SerializedName("restaurant_name") private String restaurantName;

    public OwnerSignupRequest(String username, String password, String nickname, String restaurantName) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.restaurantName = restaurantName;
    }
}
