package com.hackmin.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.restaurant.RestaurantSummaryDto;
import com.hackmin.app.util.ImageLoader;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RestaurantAdapter extends RecyclerView.Adapter<RestaurantAdapter.VH> {

    public interface OnRestaurantClickListener {
        void onClick(RestaurantSummaryDto restaurant);
    }

    private final List<RestaurantSummaryDto> items = new ArrayList<>();
    private final OnRestaurantClickListener listener;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    public RestaurantAdapter(OnRestaurantClickListener listener) {
        this.listener = listener;
    }

    /** 검색/목록 결과로 전체 교체. */
    public void submit(List<RestaurantSummaryDto> newItems) {
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
                .inflate(R.layout.item_restaurant, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RestaurantSummaryDto r = items.get(position);

        h.name.setText(r.getName());

        String cuisine = r.getCuisineType();
        h.cuisine.setText(cuisine == null || cuisine.isEmpty() ? "음식점" : cuisine);

        String meta = "⭐ " + String.format(Locale.KOREA, "%.1f", r.getRating())
                + " · 배달비 " + won.format(r.getDeliveryFee()) + "원"
                + " · 최소주문 " + won.format(r.getMinOrderAmount()) + "원";
        h.meta.setText(meta);

        h.closed.setVisibility(r.isOpen() ? View.GONE : View.VISIBLE);

        ImageLoader.load(h.thumb, r.getImage());

        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(r);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, cuisine, meta, closed;
        final ImageView thumb;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tv_item_name);
            cuisine = v.findViewById(R.id.tv_item_cuisine);
            meta = v.findViewById(R.id.tv_item_meta);
            closed = v.findViewById(R.id.tv_item_closed);
            thumb = v.findViewById(R.id.iv_item_thumb);
        }
    }
}
