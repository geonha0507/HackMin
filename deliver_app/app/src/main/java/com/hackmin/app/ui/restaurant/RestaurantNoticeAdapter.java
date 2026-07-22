package com.hackmin.app.ui.restaurant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.restaurant.RestaurantNoticeDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 음식점 상세의 공지사항 목록 어댑터 (GET /restaurants/{id}/notices).
 * 제목/내용은 TextView 로 그대로 렌더링하므로 이모지도 별도 처리 없이 정상 표시된다.
 */
public class RestaurantNoticeAdapter extends RecyclerView.Adapter<RestaurantNoticeAdapter.VH> {

    private final List<RestaurantNoticeDto> items = new ArrayList<>();

    public void submit(List<RestaurantNoticeDto> newItems) {
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
                .inflate(R.layout.item_restaurant_notice, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RestaurantNoticeDto n = items.get(position);

        h.title.setText(n.getTitle());
        h.content.setText(n.getContent());
        h.date.setText(n.getCreatedAt() != null
                ? n.getCreatedAt().substring(0, Math.min(10, n.getCreatedAt().length()))
                : "");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, date, content;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_notice_title);
            date = v.findViewById(R.id.tv_notice_date);
            content = v.findViewById(R.id.tv_notice_content);
        }
    }
}
