package com.hackmin.app.data.model.order;

public class OrderSummary {
    private int id;
    private long restaurantId;
    private String restaurantName;
    private String menuSummary;
    private String orderDate;
    private int totalPrice;
    private String status;
    /** 주문 대표 썸네일(첫 항목 메뉴 사진) URL. 없으면 null → placeholder 표시. */
    private String thumbnailUrl;

    public OrderSummary(int id, long restaurantId, String restaurantName,
                        String menuSummary, String orderDate, int totalPrice, String status,
                        String thumbnailUrl) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.menuSummary = menuSummary;
        this.orderDate = orderDate;
        this.totalPrice = totalPrice;
        this.status = status;
        this.thumbnailUrl = thumbnailUrl;
    }

    public int getId() { return id; }
    public long getRestaurantId() { return restaurantId; }
    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }
    public String getMenuSummary() { return menuSummary; }
    public String getOrderDate() { return orderDate; }
    public int getTotalPrice() { return totalPrice; }
    public String getStatus() { return status; }
    public String getThumbnailUrl() { return thumbnailUrl; }
}
