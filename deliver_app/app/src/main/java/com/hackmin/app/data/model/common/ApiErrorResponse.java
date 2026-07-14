package com.hackmin.app.data.model.common;

import com.google.gson.annotations.SerializedName;

/**
 * Matches the README's error format: { "code": "...", "message": "..." }
 * Parse this out of the error body in your Retrofit error handling
 * (e.g. via a Converter on response.errorBody()).
 */
public class ApiErrorResponse {

    @SerializedName("code")
    private String code;

    @SerializedName("message")
    private String message;

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
