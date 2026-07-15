package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class OwnerSignupResponse {
    @SerializedName("id") private long id;
    @SerializedName("username") private String username;
    @SerializedName("role") private String role;
    @SerializedName("access") private String accessToken;
    @SerializedName("refresh") private String refreshToken;

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
}
