package com.hackmin.app.util;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.hackmin.app.R;
import com.hackmin.app.network.ApiClient;

/**
 * 서버가 내려준 이미지 URL을 ImageView에 로드하는 공용 헬퍼.
 *
 * <p>백엔드 흐름: 점주 웹 업로드 → Django ImageField 저장(로컬 /media 또는 S3)
 * → API 응답의 image 필드 → 이 헬퍼가 Glide로 로드.
 *
 * <p>URL이 절대경로(http/https)면 그대로, 상대경로(/media/...)면
 * {@link ApiClient#mediaBaseUrl()}를 앞에 붙여 절대경로로 만든다.
 * 값이 비어 있으면 placeholder만 표시한다.
 */
public final class ImageLoader {

    private ImageLoader() {}

    /** 음식점/메뉴 등 이미지를 로드한다. url이 null/빈값이면 placeholder 표시. */
    public static void load(ImageView view, String url) {
        Glide.with(view.getContext())
                .load(resolve(url))
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .centerCrop()
                .into(view);
    }

    /** 상대경로면 서버 오리진을 붙여 절대 URL로 변환한다. 비었으면 null. */
    public static String resolve(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String base = ApiClient.mediaBaseUrl();
        return url.startsWith("/") ? base + url : base + "/" + url;
    }
}
