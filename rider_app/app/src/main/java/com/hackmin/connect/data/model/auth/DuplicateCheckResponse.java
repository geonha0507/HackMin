package com.hackmin.connect.data.model.auth;

import com.google.gson.annotations.SerializedName;

public class DuplicateCheckResponse {

    @SerializedName("available")
    private boolean available;

    public boolean isAvailable() { return available; }
}
