package com.hackmin.connect.data.model.rider;

import com.google.gson.annotations.SerializedName;

/**
 * 메뉴(음식) 항목. 해킹의 민족 메뉴를 홈에 노출하기 위한 DTO.
 * 서버 MenuSerializer(id, name, price, image, ...)와 대응한다.
 * image 는 서버 URL(http/상대경로) 또는 미리보기용 "res:이름".
 */
public class MenuDto {
    @SerializedName("id") private long id;
    @SerializedName("name") private String name;
    @SerializedName("restaurant") private String restaurant;
    @SerializedName("price") private int price;
    @SerializedName("image") private String image;

    public long getId() { return id; }
    public String getName() { return name; }
    public String getRestaurant() { return restaurant; }
    public int getPrice() { return price; }
    public String getImage() { return image; }
}
