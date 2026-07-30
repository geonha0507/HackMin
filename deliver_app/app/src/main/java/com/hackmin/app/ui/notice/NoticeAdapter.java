package com.hackmin.app.ui.notice;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.notice.NoticeDto;
import com.hackmin.app.util.ImageLoader;
import com.hackmin.app.util.NoticeReadStore;

import java.util.ArrayList;
import java.util.List;

public class NoticeAdapter extends RecyclerView.Adapter<NoticeAdapter.VH> {

    public interface OnNoticeClickListener {
        void onClick(NoticeDto notice);
    }

    private final List<NoticeDto> items = new ArrayList<>();
    private final OnNoticeClickListener listener;

    public NoticeAdapter(OnNoticeClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<NoticeDto> newItems) {
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
                .inflate(R.layout.item_notice, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NoticeDto n = items.get(position);
        h.title.setText(n.getTitle());
        h.pinned.setVisibility(n.isPinned() ? View.VISIBLE : View.GONE);

        String content = n.getContent();
        h.preview.setText(content == null ? "" : content);

        String date = n.getCreatedAt();
        h.date.setText(date != null && date.length() >= 10 ? date.substring(0, 10) : "");

        // 읽은 공지면 우측 상단에 "읽음" 표시 + 카드 배경을 살짝 회색으로.
        boolean read = NoticeReadStore.isRead(h.itemView.getContext(), n.getId());
        h.read.setVisibility(read ? View.VISIBLE : View.GONE);
        if (h.itemView instanceof com.google.android.material.card.MaterialCardView) {
            ((com.google.android.material.card.MaterialCardView) h.itemView)
                    .setCardBackgroundColor(read ? 0xFFF1F3F5 : 0xFFFFFFFF);
        }

        String image = n.getImage();
        if (image == null || image.trim().isEmpty()) {
            h.image.setVisibility(View.GONE);
        } else {
            h.image.setVisibility(View.VISIBLE);
            ImageLoader.load(h.image, image);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(n);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, preview, date, pinned, read;
        final ImageView image;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_notice_title);
            preview = v.findViewById(R.id.tv_notice_preview);
            date = v.findViewById(R.id.tv_notice_date);
            pinned = v.findViewById(R.id.tv_notice_pinned);
            read = v.findViewById(R.id.tv_notice_read);
            image = v.findViewById(R.id.iv_notice_image);
        }
    }
}
