package com.hackmin.app.data.model.auth;

import com.google.gson.annotations.SerializedName;

public class UserDto {

    @SerializedName("id")
    private long id;

    @SerializedName("username")
    private String username;

    @SerializedName("email")
    private String email;

    // 백엔드 UserSerializer는 name이 아니라 nickname을 반환한다.
    @SerializedName("nickname")
    private String nickname;

    @SerializedName("phone")
    private String phone;

    @SerializedName("role")
    private String role; // "customer" | "owner" | "admin" | "rider"

    @SerializedName("status")
    private String status;

    @SerializedName("date_joined")
    private String dateJoined;

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getDateJoined() { return dateJoined; }
}
