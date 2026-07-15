package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class ProductDto {
    @SerializedName("id") private long id;
    @SerializedName("restaurant") private long restaurant;
    @SerializedName("category") private Long category;
    @SerializedName("name") private String name;
    @SerializedName("description") private String description;
    @SerializedName("price") private int price;
    @SerializedName("image") private String image;
    @SerializedName("status") private String status;
    @SerializedName("created_at") private String createdAt;

    public long getId() { return id; }
    public long getRestaurant() { return restaurant; }
    public Long getCategory() { return category; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getPrice() { return price; }
    public String getImage() { return image; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
}
