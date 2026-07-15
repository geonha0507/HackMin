package com.hackmin.app.data.model.user;

import com.google.gson.annotations.SerializedName;

public class UserProfileDto {
    @SerializedName("id") private long id;
    @SerializedName("username") private String username;
    @SerializedName("email") private String email;
    @SerializedName("phone") private String phone;
    @SerializedName("nickname") private String nickname;
    @SerializedName("role") private String role;
    @SerializedName("status") private String status;
    @SerializedName("date_joined") private String dateJoined;

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getNickname() { return nickname; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getDateJoined() { return dateJoined; }
}
