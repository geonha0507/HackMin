package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class OptionGroupDto {
    @SerializedName("id") private long id;
    @SerializedName("menu") private long menu;
    @SerializedName("name") private String name;
    @SerializedName("is_required") private boolean isRequired;
    @SerializedName("max_select") private int maxSelect;

    public long getId() { return id; }
    public long getMenu() { return menu; }
    public String getName() { return name; }
    public boolean isRequired() { return isRequired; }
    public int getMaxSelect() { return maxSelect; }
}
