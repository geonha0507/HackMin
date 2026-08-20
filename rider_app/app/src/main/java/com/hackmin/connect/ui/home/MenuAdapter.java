package com.hackmin.connect.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.connect.R;
import com.hackmin.connect.data.model.rider.MenuDto;
import com.hackmin.connect.util.ConnectFormat;
import com.hackmin.connect.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/** 홈 '해킹의 민족 인기 메뉴' 가로 목록 어댑터. */
public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.Holder> {

    private final List<MenuDto> items = new ArrayList<>();

    public void submit(List<MenuDto> menus) {
        items.clear();
        if (menus != null) items.addAll(menus);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_menu, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        MenuDto m = items.get(position);
        h.tvName.setText(m.getName() == null ? "" : m.getName());
        h.tvRestaurant.setText(m.getRestaurant() == null ? "" : m.getRestaurant());
        h.tvPrice.setText(ConnectFormat.won(m.getPrice()));
        ImageLoader.loadStore(h.ivMenu, m.getImage());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView ivMenu;
        final TextView tvName, tvRestaurant, tvPrice;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivMenu = itemView.findViewById(R.id.iv_menu);
            tvName = itemView.findViewById(R.id.tv_menu_name);
            tvRestaurant = itemView.findViewById(R.id.tv_menu_restaurant);
            tvPrice = itemView.findViewById(R.id.tv_menu_price);
        }
    }
}
