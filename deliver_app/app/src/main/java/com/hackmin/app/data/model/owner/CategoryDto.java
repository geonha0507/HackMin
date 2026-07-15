package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class CategoryDto {
    @SerializedName("id") private long id;
    @SerializedName("restaurant") private long restaurant;
    @SerializedName("name") private String name;
    @SerializedName("display_order") private int displayOrder;

    public CategoryDto() {}
    public CategoryDto(long restaurant, String name, int displayOrder) {
        this.restaurant = restaurant;
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public long getId() { return id; }
    public long getRestaurant() { return restaurant; }
    public String getName() { return name; }
    public int getDisplayOrder() { return displayOrder; }
}
