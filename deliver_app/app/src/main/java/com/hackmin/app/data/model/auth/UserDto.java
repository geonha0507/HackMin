package com.hackmin.app.data.model.auth;

import com.google.gson.annotations.SerializedName;

public class UserDto {

    @SerializedName("id")
    private long id;

    @SerializedName("username")
    private String username;

    @SerializedName("email")
    private String email;

    @SerializedName("name")
    private String name;

    @SerializedName("phone")
    private String phone;

    @SerializedName("role")
    private String role; // "customer" | "owner" | "admin" | "rider"

    @SerializedName("created_at")
    private String createdAt;

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public String getCreatedAt() { return createdAt; }
}
