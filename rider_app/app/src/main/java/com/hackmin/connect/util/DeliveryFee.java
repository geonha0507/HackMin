package com.hackmin.connect.util;

import com.hackmin.connect.data.model.rider.DeliveryDto;

import java.util.List;

/**
 * 라이더 배달료 정책.
 *
 * <p>서버에 건별 배달료 데이터가 없어(Delivery 모델에 fee 없음) 앱에서 고정
 * 단가로 계산해 보여준다. 훈련 환경용 단순 정책 — 실서비스라면 서버가
 * 거리·시간대 기반으로 산정한 금액을 내려줘야 한다.</p>
 */
public final class DeliveryFee {

    /** 건당 기본 배달료(원). */
    public static final int PER_DELIVERY = 3500;

    private DeliveryFee() {}

    /** 완료(delivered)된 배달 건수 × 단가. */
    public static long earned(int deliveredCount) {
        return (long) deliveredCount * PER_DELIVERY;
    }

    /** 목록에서 완료 건만 세어 수입을 계산한다. */
    public static long earnedFrom(List<DeliveryDto> deliveries) {
        if (deliveries == null) return 0;
        int done = 0;
        for (DeliveryDto d : deliveries) {
            if ("delivered".equals(d.getStatus())) done++;
        }
        return earned(done);
    }
}
