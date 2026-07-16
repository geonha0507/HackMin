package com.hackmin.app.ui.mypage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.review.ReviewDto;

import java.util.ArrayList;
import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.VH> {

    public interface OnDeleteListener {
        void onDelete(ReviewDto review);
    }

    private final List<ReviewDto> items = new ArrayList<>();
    private final OnDeleteListener deleteListener;

    public ReviewAdapter(OnDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void submit(List<ReviewDto> newItems) {
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
                .inflate(R.layout.item_review, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ReviewDto r = items.get(position);

        h.rating.setText(stars(r.getRating()));
        h.content.setText(r.getContent());
        h.date.setText(r.getCreatedAt() != null ? r.getCreatedAt().substring(0, Math.min(10, r.getCreatedAt().length())) : "");

        if (r.getReply() != null && r.getReply().getContent() != null) {
            h.reply.setVisibility(View.VISIBLE);
            h.reply.setText("사장님 답변: " + r.getReply().getContent());
        } else {
            h.reply.setVisibility(View.GONE);
        }

        h.delete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(r);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String stars(int rating) {
        int r = Math.max(0, Math.min(5, rating));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r; i++) sb.append("⭐");
        return sb.toString();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView rating, content, date, reply, delete;

        VH(@NonNull View v) {
            super(v);
            rating = v.findViewById(R.id.tvReviewRating);
            content = v.findViewById(R.id.tvReviewContent);
            date = v.findViewById(R.id.tvReviewDate);
            reply = v.findViewById(R.id.tvReviewReply);
            delete = v.findViewById(R.id.tvReviewDelete);
        }
    }
}
