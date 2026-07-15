package com.hackmin.app.data.model.order;

public class OrderSummary {
    private int id;
    private String restaurantName;
    private String orderDate;
    private int totalPrice;
    private String status;

    public OrderSummary(int id, String restaurantName, String orderDate, int totalPrice, String status) {
        this.id = id;
        this.restaurantName = restaurantName;
        this.orderDate = orderDate;
        this.totalPrice = totalPrice;
        this.status = status;
    }

    public int getId() { return id; }
    public String getRestaurantName() { return restaurantName; }
    public String getOrderDate() { return orderDate; }
    public int getTotalPrice() { return totalPrice; }
    public String getStatus() { return status; }
}