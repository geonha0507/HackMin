package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class OwnerProfileDto {
    @SerializedName("id") private long id;
    @SerializedName("username") private String username;
    @SerializedName("nickname") private String nickname;
    @SerializedName("email") private String email;
    @SerializedName("phone") private String phone;
    @SerializedName("role") private String role;

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
}
