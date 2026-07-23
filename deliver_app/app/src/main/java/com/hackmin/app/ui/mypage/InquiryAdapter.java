package com.hackmin.app.ui.mypage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.inquiry.InquiryDto;

import java.util.ArrayList;
import java.util.List;

public class InquiryAdapter extends RecyclerView.Adapter<InquiryAdapter.VH> {

    public interface OnItemClickListener {
        void onItemClick(InquiryDto item);
    }

    private final List<InquiryDto> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public InquiryAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<InquiryDto> newItems) {
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
                .inflate(R.layout.item_inquiry, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        InquiryDto item = items.get(position);
        // 날짜 오름차순 목록에서 위에서부터 매기는 순번(1부터 시작).
        h.no.setText(String.valueOf(position + 1));
        h.title.setText(item.getTitle());
        String category = item.getCategoryDisplay();
        h.category.setText(category == null || category.isEmpty() ? "-" : category);
        h.author.setText(item.getAuthor());
        h.date.setText(formatDate(item.getCreatedAt()));
        h.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** "2026-07-23T04:12:00Z" 형식의 ISO 날짜를 "2026.07.23" 형식으로 변환한다. */
    private String formatDate(String iso) {
        if (iso == null || iso.length() < 10) {
            return "-";
        }
        return iso.substring(0, 10).replace("-", ".");
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView no, title, category, author, date;

        VH(@NonNull View v) {
            super(v);
            no = v.findViewById(R.id.tvInquiryNo);
            title = v.findViewById(R.id.tvInquiryTitle);
            category = v.findViewById(R.id.tvInquiryCategory);
            author = v.findViewById(R.id.tvInquiryAuthor);
            date = v.findViewById(R.id.tvInquiryDate);
        }
    }
}
