package com.hackmin.app.data.model.auth;

import com.google.gson.annotations.SerializedName;

/**
 * ASSUMPTION: exact field names are not in the README yet — confirm with
 * the backend dev. This follows the common SimpleJWT convention
 * (access/refresh) since the README says JWT auth with Bearer tokens.
 */
public class LoginResponse {

    @SerializedName("access")
    private String accessToken;

    @SerializedName("refresh")
    private String refreshToken;

    @SerializedName("user")
    private UserDto user;

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public UserDto getUser() { return user; }
}
