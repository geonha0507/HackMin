package com.hackmin.app.ui.restaurant;

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
import com.hackmin.app.data.model.restaurant.RestaurantReviewDto;
import com.hackmin.app.util.RatingFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * 음식점 상세의 리뷰 목록 어댑터.
 * 다른 사용자가 작성한 리뷰까지 공개로 보여준다 (GET /restaurants/{id}/reviews).
 * 사진은 로딩 라이브러리 없이 장수만 표시한다.
 */
public class RestaurantReviewAdapter extends RecyclerView.Adapter<RestaurantReviewAdapter.VH> {

    private final List<RestaurantReviewDto> items = new ArrayList<>();

    public void submit(List<RestaurantReviewDto> newItems) {
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
                .inflate(R.layout.item_restaurant_review, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RestaurantReviewDto r = items.get(position);

        h.author.setText(r.getAuthorName() != null ? r.getAuthorName() : "익명");
        h.rating.setText(RatingFormat.stars(r.getRating()));
        h.content.setText(r.getContent());
        h.date.setText(r.getCreatedAt() != null
                ? r.getCreatedAt().substring(0, Math.min(10, r.getCreatedAt().length()))
                : "");

        bindImages(h.images, r.getImageUrls());

        if (r.getOwnerReply() != null && !r.getOwnerReply().isEmpty()) {
            h.reply.setVisibility(View.VISIBLE);
            h.reply.setText("사장님 답변: " + r.getOwnerReply());
        } else {
            h.reply.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 리뷰 사진 URL 목록을 가로 썸네일로 렌더링한다. */
    private void bindImages(LinearLayout container, List<String> urls) {
        container.removeAllViews();
        if (urls == null || urls.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        int sizePx = dp(container, 88);
        int marginPx = dp(container, 8);
        for (String url : urls) {
            if (url == null) continue;
            ImageView iv = new ImageView(container.getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMarginEnd(marginPx);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(container.getContext())
                    .load(url)
                    .into(iv);
            container.addView(iv);
        }
    }

    private int dp(View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView author, date, rating, content, reply;
        final LinearLayout images;

        VH(@NonNull View v) {
            super(v);
            author = v.findViewById(R.id.tv_review_author);
            date = v.findViewById(R.id.tv_review_date);
            rating = v.findViewById(R.id.tv_review_rating);
            content = v.findViewById(R.id.tv_review_content);
            reply = v.findViewById(R.id.tv_review_reply);
            images = v.findViewById(R.id.ll_review_images);
        }
    }
}
