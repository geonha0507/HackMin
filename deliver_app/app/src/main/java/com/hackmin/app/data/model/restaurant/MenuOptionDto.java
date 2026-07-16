package com.hackmin.app.data.model.restaurant;

import com.google.gson.annotations.SerializedName;

/** 백엔드 MenuOptionSerializer 대응: id, name, extra_price. */
public class MenuOptionDto {

    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("extra_price")
    private long extraPrice;

    public long getId() { return id; }
    public String getName() { return name; }
    public long getExtraPrice() { return extraPrice; }
}
