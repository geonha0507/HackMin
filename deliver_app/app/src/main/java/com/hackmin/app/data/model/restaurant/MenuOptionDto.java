package com.hackmin.app.data.model.restaurant;

import com.google.gson.annotations.SerializedName;

public class MenuOptionDto {

    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("extra_price")
    private long extraPrice;

    @SerializedName("required")
    private boolean required;

    public long getId() { return id; }
    public String getName() { return name; }
    public long getExtraPrice() { return extraPrice; }
    public boolean isRequired() { return required; }
}
