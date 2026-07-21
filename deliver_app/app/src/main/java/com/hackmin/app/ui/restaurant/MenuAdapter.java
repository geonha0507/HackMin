package com.hackmin.app.ui.restaurant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.restaurant.MenuDto;
import com.hackmin.app.util.ImageLoader;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.VH> {

    public interface OnMenuClickListener {
        void onClick(MenuDto menu);
    }

    private final List<MenuDto> items = new ArrayList<>();
    private final OnMenuClickListener listener;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    public MenuAdapter(OnMenuClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<MenuDto> newItems) {
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
                .inflate(R.layout.item_menu, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MenuDto m = items.get(position);

        h.name.setText(m.getName());

        String desc = m.getDescription();
        if (desc == null || desc.isEmpty()) {
            h.desc.setVisibility(View.GONE);
        } else {
            h.desc.setVisibility(View.VISIBLE);
            h.desc.setText(desc);
        }

        h.price.setText(won.format(m.getPrice()) + "원");
        h.soldOut.setVisibility(m.isSoldOut() ? View.VISIBLE : View.GONE);

        ImageLoader.load(h.thumb, m.getImage());

        h.itemView.setOnClickListener(v -> {
            if (listener != null && !m.isSoldOut()) {
                listener.onClick(m);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, desc, price, soldOut;
        final ImageView thumb;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tv_menu_name);
            desc = v.findViewById(R.id.tv_menu_desc);
            price = v.findViewById(R.id.tv_menu_price);
            soldOut = v.findViewById(R.id.tv_menu_soldout);
            thumb = v.findViewById(R.id.iv_menu_thumb);
        }
    }
}
