package com.hackmin.app.data.model.user;

import com.google.gson.annotations.SerializedName;

/** 카드/간편결제 등록 요청. 카드번호는 서버가 AES-256 암호화 저장(CVC/비번은 전송·저장하지 않음). */
public class CardRegisterRequest {
    @SerializedName("provider") private String provider;   // card | kakao | naver
    @SerializedName("card_number") private String cardNumber;

    public CardRegisterRequest(String provider, String cardNumber) {
        this.provider = provider;
        this.cardNumber = cardNumber;
    }
}
