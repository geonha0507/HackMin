package com.hackmin.connect.data.model.rider;

import com.google.gson.annotations.SerializedName;

/**
 * 배달 전 정보(정산 계좌·면허·차량·희망지역·배달수단).
 * 서버 RiderProfileSerializer 와 대응. account_number 는 저장(쓰기)용 평문,
 * account_number_masked 는 조회(읽기)용 마스킹 값.
 */
public class RiderProfileDto {
    @SerializedName("bank_name") private String bankName;
    @SerializedName("account_number") private String accountNumber;          // write-only
    @SerializedName("account_number_masked") private String accountNumberMasked; // read-only
    @SerializedName("account_holder") private String accountHolder;
    @SerializedName("license_number") private String licenseNumber;
    @SerializedName("vehicle_number") private String vehicleNumber;
    @SerializedName("region") private String region;
    @SerializedName("delivery_method") private String deliveryMethod;        // walk|bicycle|motorcycle|car
    @SerializedName("delivery_method_label") private String deliveryMethodLabel; // read-only

    public String getBankName() { return bankName; }
    public void setBankName(String v) { this.bankName = v; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String v) { this.accountNumber = v; }

    public String getAccountNumberMasked() { return accountNumberMasked; }

    public String getAccountHolder() { return accountHolder; }
    public void setAccountHolder(String v) { this.accountHolder = v; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String v) { this.licenseNumber = v; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String v) { this.vehicleNumber = v; }

    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v; }

    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String v) { this.deliveryMethod = v; }

    public String getDeliveryMethodLabel() { return deliveryMethodLabel; }
}
