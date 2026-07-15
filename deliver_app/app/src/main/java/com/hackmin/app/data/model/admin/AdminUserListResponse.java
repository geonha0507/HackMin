package com.hackmin.app.data.model.admin;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AdminUserListResponse {
    @SerializedName("results") private List<AdminUserDto> results;

    public List<AdminUserDto> getResults() { return results; }
}
