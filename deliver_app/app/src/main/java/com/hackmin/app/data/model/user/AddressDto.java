package com.hackmin.app.data.model.user;

import com.google.gson.annotations.SerializedName;

public class AddressDto {
    @SerializedName("id") private long id;
    @SerializedName("label") private String label;
    @SerializedName("address") private String address;
    @SerializedName("detail") private String detail;
    @SerializedName("postal_code") private String postalCode;
    @SerializedName("latitude") private Double latitude;
    @SerializedName("longitude") private Double longitude;
    @SerializedName("is_default") private boolean isDefault;
    @SerializedName("created_at") private String createdAt;

    public AddressDto() {}

    public AddressDto(String label, String address, String detail,
                      String postalCode, Double latitude, Double longitude, boolean isDefault) {
        this.label = label;
        this.address = address;
        this.detail = detail;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isDefault = isDefault;
    }

    public long getId() { return id; }
    public String getLabel() { return label; }
    public String getAddress() { return address; }
    public String getDetail() { return detail; }
    public String getPostalCode() { return postalCode; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public boolean isDefault() { return isDefault; }
    public String getCreatedAt() { return createdAt; }
}
