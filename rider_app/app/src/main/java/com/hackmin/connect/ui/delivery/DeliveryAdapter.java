package com.hackmin.connect.ui.delivery;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.connect.R;
import com.hackmin.connect.util.ConnectFormat;
import com.hackmin.connect.util.ImageLoader;
import com.hackmin.connect.data.model.rider.DeliveryDto;

import java.util.ArrayList;
import java.util.List;

/** 운행(콜) 목록 어댑터. 항목 탭 → 배달 상세. */
public class DeliveryAdapter extends RecyclerView.Adapter<DeliveryAdapter.Holder> {

    public interface OnItemClick {
        void onClick(DeliveryDto delivery);
    }

    private final List<DeliveryDto> items = new ArrayList<>();
    private final OnItemClick listener;

    public DeliveryAdapter(OnItemClick listener) {
        this.listener = listener;
    }

    public void submit(List<DeliveryDto> deliveries) {
        items.clear();
        if (deliveries != null) items.addAll(deliveries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_delivery, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        DeliveryDto d = items.get(position);
        h.tvRestaurant.setText(d.getRestaurant() == null || d.getRestaurant().isEmpty()
                ? "가게 미지정" : d.getRestaurant());
        ImageLoader.loadStore(h.ivRestaurant, d.getRestaurantImage());
        h.tvOrderNumber.setText("주문 " + (d.getOrderNumber() == null ? "-" : d.getOrderNumber()));
        h.tvTotal.setText("주문금액 " + ConnectFormat.won(d.getTotal()));
        h.tvTime.setText(ConnectFormat.shortTime(d.getAssignedAt()));
        bindStatus(h.tvStatus, d.getStatus());
        h.itemView.setOnClickListener(v -> listener.onClick(d));
    }

    /** 상태 칩: 신규 콜(코랄) / 진행 중(주황) / 완료(회색). */
    static void bindStatus(TextView chip, String status) {
        String label;
        int bg;
        switch (status == null ? "" : status) {
            case "assigned":
                label = "신규 콜";
                bg = R.drawable.bg_chip_status_new;
                break;
            case "picked_up":
                label = "픽업 완료";
                bg = R.drawable.bg_chip_status_progress;
                break;
            case "delivering":
                label = "배달 중";
                bg = R.drawable.bg_chip_status_progress;
                break;
            case "delivered":
                label = "배달 완료";
                bg = R.drawable.bg_chip_status_done;
                break;
            default:
                label = status == null ? "-" : status;
                bg = R.drawable.bg_chip_status_done;
        }
        chip.setText(label);
        chip.setBackgroundResource(bg);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView ivRestaurant;
        final TextView tvRestaurant, tvOrderNumber, tvTotal, tvTime, tvStatus;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivRestaurant = itemView.findViewById(R.id.iv_restaurant);
            tvRestaurant = itemView.findViewById(R.id.tv_restaurant);
            tvOrderNumber = itemView.findViewById(R.id.tv_order_number);
            tvTotal = itemView.findViewById(R.id.tv_total);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}
