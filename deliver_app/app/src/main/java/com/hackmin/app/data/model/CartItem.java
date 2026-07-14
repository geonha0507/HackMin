package com.hackmin.app.data.model;

public class CartItem {
    private int id;
    private String menuName;
    private String optionName;
    private int price;
    private int quantity;
    private String imageUrl;

    public CartItem(int id, String menuName, String optionName, int price, int quantity, String imageUrl) {
        this.id = id;
        this.menuName = menuName;
        this.optionName = optionName;
        this.price = price;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
    }

    public int getId() { return id; }
    public String getMenuName() { return menuName; }
    public String getOptionName() { return optionName; }
    public int getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getImageUrl() { return imageUrl; }
}