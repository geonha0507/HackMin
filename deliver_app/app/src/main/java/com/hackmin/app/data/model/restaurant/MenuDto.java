package com.hackmin.app.data.model.restaurant;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 메뉴 DTO.
 * 목록(GET /restaurants/{id}/menus): id, name, description, price, image, status, category
 * 상세(GET /menus/{id}): 위 필드 + restaurant, option_groups
 */
public class MenuDto {

    @SerializedName("id")
    private long id;

    // 상세 조회에서만 채워짐(목록에서는 0).
    @SerializedName("restaurant")
    private long restaurant;

    @SerializedName("category")
    private Long category; // MenuCategory FK pk, nullable

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("image")
    private String image;

    @SerializedName("price")
    private long price;

    // "on_sale" | "sold_out" | "hidden"
    @SerializedName("status")
    private String status;

    // 상세(/menus/{id})에서만 채워짐. 목록에서는 null.
    @SerializedName("option_groups")
    private List<MenuOptionGroupDto> optionGroups;

    public long getId() { return id; }
    public long getRestaurant() { return restaurant; }
    public Long getCategory() { return category; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getImage() { return image; }
    public long getPrice() { return price; }
    public String getStatus() { return status; }
    public List<MenuOptionGroupDto> getOptionGroups() { return optionGroups; }

    public boolean isSoldOut() { return "sold_out".equals(status); }
    public boolean isOnSale() { return "on_sale".equals(status); }
}
