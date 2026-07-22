package com.hackmin.app.ui.mypage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.promotion.CouponDto;
import com.hackmin.app.data.model.promotion.UserCouponDto;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CouponAdapter extends RecyclerView.Adapter<CouponAdapter.VH> {

    private final List<UserCouponDto> items = new ArrayList<>();
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    public void submit(List<UserCouponDto> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_coupon, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        UserCouponDto uc = items.get(position);
        CouponDto c = uc.getCoupon();

        if (c != null) {
            // 할인 표기: percent → N%, fixed → N원
            String discount = "percent".equals(c.getDiscountType())
                    ? c.getDiscountValue() + "% 할인"
                    : won.format(c.getDiscountValue()) + "원 할인";
            h.discount.setText(discount);
            h.name.setText(c.getName());

            StringBuilder meta = new StringBuilder();
            if (c.getMinOrderAmount() > 0) {
                meta.append("최소주문 ").append(won.format(c.getMinOrderAmount())).append("원");
            }
            if (c.getValidUntil() != null && !c.getValidUntil().isEmpty()) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append("~").append(c.getValidUntil());
            }
            h.meta.setText(meta.toString());
        }

        h.used.setVisibility(uc.isUsed() ? View.VISIBLE : View.GONE);
        // 사용 완료 쿠폰은 카드를 흐리게 표시해 사용 불가임을 시각적으로 강조한다.
        h.itemView.setAlpha(uc.isUsed() ? 0.45f : 1.0f);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView discount, name, meta, used;

        VH(@NonNull View v) {
            super(v);
            discount = v.findViewById(R.id.tvCouponDiscount);
            name = v.findViewById(R.id.tvCouponName);
            meta = v.findViewById(R.id.tvCouponMeta);
            used = v.findViewById(R.id.tvCouponUsed);
        }
    }
}
