package com.hackmin.app.data.model.restaurant;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MenuDto {

    @SerializedName("id")
    private long id;

    @SerializedName("restaurant_id")
    private long restaurantId;

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("price")
    private long price;

    @SerializedName("discount_price")
    private Long discountPrice; // nullable

    @SerializedName("sold_out")
    private boolean soldOut;

    @SerializedName("category")
    private String category;

    // Only populated on /menus/{id} detail calls; null in list contexts.
    @SerializedName("options")
    private List<MenuOptionDto> options;

    public long getId() { return id; }
    public long getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public long getPrice() { return price; }
    public Long getDiscountPrice() { return discountPrice; }
    public boolean isSoldOut() { return soldOut; }
    public String getCategory() { return category; }
    public List<MenuOptionDto> getOptions() { return options; }
}
