package com.hackmin.app.util;

/**
 * 장바구니 관련 클라이언트 정책 상수.
 * 동일 메뉴+옵션 조합 1건당 담을 수 있는 최대 수량을 한곳에서 관리한다.
 */
public final class CartRules {

    private CartRules() {}

    /** 장바구니 항목 1개(동일 메뉴+옵션)당 최대 수량. */
    public static final int MAX_ITEM_QUANTITY = 50;

    /** 상한 초과 시 사용자에게 보여줄 안내 문구. */
    public static final String MAX_QUANTITY_MESSAGE =
            "최대 " + MAX_ITEM_QUANTITY + "개까지 담을 수 있습니다.";
}
