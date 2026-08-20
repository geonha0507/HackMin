package com.hackmin.connect.ui.earnings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.connect.R;
import com.hackmin.connect.data.model.rider.DeliveryDto;
import com.hackmin.connect.util.ConnectFormat;
import com.hackmin.connect.util.DeliveryFee;

import java.util.ArrayList;
import java.util.List;

/** 수입 내역(완료 건) 어댑터. */
public class EarningsAdapter extends RecyclerView.Adapter<EarningsAdapter.Holder> {

    private final List<DeliveryDto> items = new ArrayList<>();

    public void submit(List<DeliveryDto> deliveries) {
        items.clear();
        if (deliveries != null) items.addAll(deliveries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_earning, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        DeliveryDto d = items.get(position);
        h.tvRestaurant.setText(d.getRestaurant() == null || d.getRestaurant().isEmpty()
                ? "가게 미지정" : d.getRestaurant());
        h.tvMeta.setText(ConnectFormat.shortTime(d.getAssignedAt())
                + " · 주문 " + (d.getOrderNumber() == null ? "-" : d.getOrderNumber()));
        h.tvFee.setText("+" + ConnectFormat.won(DeliveryFee.PER_DELIVERY));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvRestaurant, tvMeta, tvFee;

        Holder(@NonNull View itemView) {
            super(itemView);
            tvRestaurant = itemView.findViewById(R.id.tv_restaurant);
            tvMeta = itemView.findViewById(R.id.tv_meta);
            tvFee = itemView.findViewById(R.id.tv_fee);
        }
    }
}
