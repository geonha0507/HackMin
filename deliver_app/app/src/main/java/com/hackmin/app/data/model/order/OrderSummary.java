package com.hackmin.app.data.model.order;

public class OrderSummary {
    private int id;
    private long restaurantId;
    private String restaurantName;
    private String menuSummary;
    private String orderDate;
    private int totalPrice;
    private String status;

    public OrderSummary(int id, long restaurantId, String restaurantName,
                        String menuSummary, String orderDate, int totalPrice, String status) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.menuSummary = menuSummary;
        this.orderDate = orderDate;
        this.totalPrice = totalPrice;
        this.status = status;
    }

    public int getId() { return id; }
    public long getRestaurantId() { return restaurantId; }
    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }
    public String getMenuSummary() { return menuSummary; }
    public String getOrderDate() { return orderDate; }
    public int getTotalPrice() { return totalPrice; }
    public String getStatus() { return status; }
}
