package com.hackmin.app.data.model.user;

import com.google.gson.annotations.SerializedName;

/** 등록된 계좌(서버 응답). 계좌번호는 서버에 암호화 저장되고, 여기엔 마스킹값만 온다. */
public class BankAccountDto {
    @SerializedName("id") private long id;
    @SerializedName("bank") private String bank;
    @SerializedName("account_masked") private String accountMasked;

    public long getId() { return id; }
    public String getBank() { return bank; }
    public String getAccountMasked() { return accountMasked; }
}
