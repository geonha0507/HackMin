package com.hackmin.app.data.model.user;

import com.google.gson.annotations.SerializedName;

/** 등록된 카드/간편결제(서버 응답). 카드번호는 서버에 암호화 저장되고, 여기엔 마스킹값만 온다. */
public class PaymentCardDto {
    @SerializedName("id") private long id;
    @SerializedName("provider") private String provider;   // card | kakao | naver
    @SerializedName("card_masked") private String cardMasked;

    public long getId() { return id; }
    public String getProvider() { return provider; }
    public String getCardMasked() { return cardMasked; }
}
