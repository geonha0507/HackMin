package com.hackmin.app.ui.mypage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.hackmin.app.R;
import com.hackmin.app.data.model.review.ReviewDto;
import com.hackmin.app.data.model.review.ReviewImageDto;
import com.hackmin.app.util.RatingFormat;

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

        h.rating.setText(RatingFormat.stars(r.getRating()));
        h.content.setText(r.getContent());
        h.date.setText(r.getCreatedAt() != null ? r.getCreatedAt().substring(0, Math.min(10, r.getCreatedAt().length())) : "");

        bindImages(h.images, r.getImages());

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

    /** 리뷰 첨부 사진 썸네일을 가로로 렌더링한다. */
    private void bindImages(LinearLayout container, List<ReviewImageDto> images) {
        container.removeAllViews();
        if (images == null || images.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        int sizePx = dp(container, 88);
        int marginPx = dp(container, 8);
        for (ReviewImageDto img : images) {
            if (img.getImage() == null) continue;
            ImageView iv = new ImageView(container.getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMarginEnd(marginPx);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            // 다른 이미지들과 동일하게 ImageLoader로 로드 → 상대경로(/media/...)에 서버 호스트를 붙여준다.
            // (기존엔 raw Glide로 상대경로를 그대로 로드해 리뷰 사진이 안 보였음)
            com.hackmin.app.util.ImageLoader.load(iv, img.getImage());
            container.addView(iv);
        }
    }

    private int dp(View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView rating, content, date, reply, delete;
        final LinearLayout images;

        VH(@NonNull View v) {
            super(v);
            rating = v.findViewById(R.id.tvReviewRating);
            content = v.findViewById(R.id.tvReviewContent);
            date = v.findViewById(R.id.tvReviewDate);
            reply = v.findViewById(R.id.tvReviewReply);
            delete = v.findViewById(R.id.tvReviewDelete);
            images = v.findViewById(R.id.llReviewImages);
        }
    }
}
