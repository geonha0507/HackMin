package com.hackmin.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;

/**
 * 홈 상단 이벤트 배너(ViewPager2)용 어댑터.
 * 좌우 스와이프로 배너를 넘기고, 탭하면 위치별 동작을 콜백으로 넘긴다.
 */
public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {

    /** 배너 탭 시 위치(index)를 전달받는 콜백. */
    public interface OnBannerClickListener {
        void onBannerClick(int position);
    }

    // 무한 순환(1→2→3→1→…) 느낌을 주기 위해 실제 배너 수보다 훨씬 큰 개수를 노출하고,
    // 바인딩 시 position을 실제 인덱스(position % 실제개수)로 환산한다.
    private static final int LOOP_MULTIPLIER = 10000;

    private final int[] bannerResIds;
    private final OnBannerClickListener listener;

    public BannerAdapter(int[] bannerResIds, OnBannerClickListener listener) {
        this.bannerResIds = bannerResIds;
        this.listener = listener;
    }

    /** 무한 스크롤 시작 위치(가운데를 첫 배너에 정렬). */
    public int firstBannerStartPosition() {
        int mid = getItemCount() / 2;
        return mid - (mid % bannerResIds.length);
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_home_banner, parent, false);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        int real = position % bannerResIds.length;
        holder.image.setImageResource(bannerResIds[real]);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBannerClick(real);
            }
        });
    }

    @Override
    public int getItemCount() {
        return bannerResIds.length * LOOP_MULTIPLIER;
    }

    static class BannerViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.iv_banner);
        }
    }
}
