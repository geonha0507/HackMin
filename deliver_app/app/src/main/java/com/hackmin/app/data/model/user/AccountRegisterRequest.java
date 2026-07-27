package com.hackmin.app.data.model.user;

import com.google.gson.annotations.SerializedName;

/** 계좌 등록 요청. 은행명 + 계좌번호를 전송하고 서버가 계좌번호를 AES-256 암호화 저장한다(비번은 전송·저장하지 않음). */
public class AccountRegisterRequest {
    @SerializedName("bank") private String bank;
    @SerializedName("account_number") private String accountNumber;

    public AccountRegisterRequest(String bank, String accountNumber) {
        this.bank = bank;
        this.accountNumber = accountNumber;
    }
}
