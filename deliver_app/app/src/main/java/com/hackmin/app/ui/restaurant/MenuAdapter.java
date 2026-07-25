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

public class MenuAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;  // 메뉴 목록 맨 아래 "유의사항" 푸터.

    public interface OnMenuClickListener {
        void onClick(MenuDto menu);
    }

    private final List<MenuDto> items = new ArrayList<>();
    private final OnMenuClickListener listener;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);
    private String restaurantName = "";

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

    /** 유의사항 문구에 넣을 매장 이름을 설정한다. */
    public void setRestaurantName(String name) {
        this.restaurantName = name == null ? "" : name;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == items.size() ? TYPE_FOOTER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_FOOTER) {
            View v = inflater.inflate(R.layout.item_menu_footer, parent, false);
            return new FooterVH(v);
        }
        View v = inflater.inflate(R.layout.item_menu, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterVH) {
            ((FooterVH) holder).bind(restaurantName);
            return;
        }
        VH h = (VH) holder;
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
        // 메뉴가 있을 때만 유의사항 푸터를 1개 붙인다.
        return items.isEmpty() ? 0 : items.size() + 1;
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

    static class FooterVH extends RecyclerView.ViewHolder {
        final TextView disclaimer;

        FooterVH(@NonNull View v) {
            super(v);
            disclaimer = v.findViewById(R.id.tv_notice_disclaimer);
        }

        void bind(String restaurantName) {
            String name = restaurantName == null || restaurantName.isEmpty() ? "본 매장" : restaurantName;
            String josa = hasFinalConsonant(name) ? "은" : "는";
            disclaimer.setText("• " + name + josa + " 상품거래에 대한 통신판매중개자이며, 통신판매의 당사자가 아닙니다. "
                    + "따라서 " + name + josa + " 상품·거래정보 및 거래에 대하여 책임을 지지 않습니다.");
        }

        /** 한글 이름 끝 글자에 받침이 있으면 true(은/는 조사 선택용). */
        private boolean hasFinalConsonant(String name) {
            char last = name.charAt(name.length() - 1);
            if (last < 0xAC00 || last > 0xD7A3) {
                return true;  // 한글이 아니면 기본 "은".
            }
            return (last - 0xAC00) % 28 != 0;
        }
    }
}
