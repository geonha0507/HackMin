package com.hackmin.app.ui.cart;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.cart.CartItemDto;
import com.hackmin.app.util.CartRules;

import java.util.ArrayList;
import java.util.List;

public class CartItemAdapter extends RecyclerView.Adapter<CartItemAdapter.CartViewHolder> {

    /** 서버(GET /cart) 응답 항목을 그대로 바인딩한다. 수량/삭제는 서버 호출로 위임. */
    public interface OnCartItemActionListener {
        void onQuantityChanged(long itemId, int newQuantity);
        void onDeleteItem(long itemId);
    }

    private final List<CartItemDto> cartItems = new ArrayList<>();
    private final OnCartItemActionListener listener;

    public CartItemAdapter(OnCartItemActionListener listener) {
        this.listener = listener;
    }

    /** GET /cart 응답으로 목록을 통째로 교체한다. */
    public void setItems(List<CartItemDto> items) {
        cartItems.clear();
        if (items != null) {
            cartItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItemDto item = cartItems.get(position);

        holder.tvName.setText(item.getMenuName());

        // 서버는 옵션 이름 대신 옵션 ID 리스트만 주므로 개수로 표시한다.
        int optionCount = item.getOptions() == null ? 0 : item.getOptions().size();
        if (optionCount > 0) {
            holder.tvOption.setVisibility(View.VISIBLE);
            holder.tvOption.setText("옵션 " + optionCount + "개");
        } else {
            holder.tvOption.setVisibility(View.GONE);
        }

        holder.tvPrice.setText(item.getLineTotal() + "원");
        holder.tvQuantity.setText(String.valueOf(item.getQuantity()));

        holder.btnIncrease.setOnClickListener(v -> {
            if (item.getQuantity() >= CartRules.MAX_ITEM_QUANTITY) {
                Toast.makeText(v.getContext(), CartRules.MAX_QUANTITY_MESSAGE, Toast.LENGTH_SHORT).show();
                return;
            }
            int newQty = item.getQuantity() + 1;
            holder.tvQuantity.setText(String.valueOf(newQty)); // 낙관적 표시, 서버 응답으로 최종 동기화
            listener.onQuantityChanged(item.getId(), newQty);
        });

        holder.btnDecrease.setOnClickListener(v -> {
            if (item.getQuantity() <= 1) return;
            int newQty = item.getQuantity() - 1;
            holder.tvQuantity.setText(String.valueOf(newQty));
            listener.onQuantityChanged(item.getId(), newQty);
        });

        holder.btnDelete.setOnClickListener(v -> listener.onDeleteItem(item.getId()));
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    static class CartViewHolder extends RecyclerView.ViewHolder {
        ImageView imgItem;
        TextView tvName, tvOption, tvPrice, tvQuantity;
        Button btnIncrease, btnDecrease;
        ImageButton btnDelete;

        CartViewHolder(@NonNull View itemView) {
            super(itemView);
            imgItem = itemView.findViewById(R.id.imgCartItem);
            tvName = itemView.findViewById(R.id.tvCartItemName);
            tvOption = itemView.findViewById(R.id.tvCartItemOption);
            tvPrice = itemView.findViewById(R.id.tvCartItemPrice);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            btnIncrease = itemView.findViewById(R.id.btnIncrease);
            btnDecrease = itemView.findViewById(R.id.btnDecrease);
            btnDelete = itemView.findViewById(R.id.btnDeleteItem);
        }
    }
}
