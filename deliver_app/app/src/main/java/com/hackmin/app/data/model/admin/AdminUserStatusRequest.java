package com.hackmin.app.data.model.admin;

import com.google.gson.annotations.SerializedName;

public class AdminUserStatusRequest {
    @SerializedName("status") private String status;

    public AdminUserStatusRequest(String status) {
        this.status = status;
    }
}
