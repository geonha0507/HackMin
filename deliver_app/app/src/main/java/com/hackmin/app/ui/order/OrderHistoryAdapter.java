package com.hackmin.app.ui.order;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.order.OrderSummary;

import java.util.List;

public class OrderHistoryAdapter extends RecyclerView.Adapter<OrderHistoryAdapter.OrderViewHolder> {

    public interface OnOrderItemClickListener {
        void onOrderClick(OrderSummary order);
    }

    private final List<OrderSummary> orderList;
    private final OnOrderItemClickListener listener;

    public OrderHistoryAdapter(List<OrderSummary> orderList, OnOrderItemClickListener listener) {
        this.orderList = orderList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_history, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        OrderSummary order = orderList.get(position);
        holder.tvRestaurantName.setText(order.getRestaurantName());
        holder.tvOrderDate.setText(order.getOrderDate());
        holder.tvTotalPrice.setText(order.getTotalPrice() + "원");
        holder.tvStatus.setText(order.getStatus());

        holder.itemView.setOnClickListener(v -> listener.onOrderClick(order));
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvRestaurantName, tvOrderDate, tvTotalPrice, tvStatus;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRestaurantName = itemView.findViewById(R.id.tvRestaurantName);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvTotalPrice = itemView.findViewById(R.id.tvTotalPrice);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}