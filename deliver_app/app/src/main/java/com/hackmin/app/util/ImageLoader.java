package com.hackmin.app.util;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
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
 *
 * <p><b>S3 presigned URL 캐싱:</b> S3 서명 URL은 요청마다 쿼리스트링(서명·만료)이
 * 바뀌어, 기본 설정으로는 Glide가 매번 다른 이미지로 보고 재다운로드한다(목록에선
 * 느려서 안 보이는 원인). 그래서 쿼리스트링을 뺀 경로를 캐시 키로 고정해 목록·상세가
 * 캐시를 공유하도록 한다.
 */
public final class ImageLoader {

    private ImageLoader() {}

    /** 음식점/메뉴 등 이미지를 로드한다. url이 null/빈값이면 placeholder 표시. */
    public static void load(ImageView view, String url) {
        Glide.with(view.getContext())
                .load(toModel(resolve(url)))
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

    /**
     * Glide 로드 모델을 만든다. 쿼리스트링이 있는 URL(S3 presigned 등)은
     * 서명·만료 파라미터를 뺀 경로를 캐시 키로 쓰는 {@link GlideUrl}로 감싼다.
     * → 서명이 바뀌어도 같은 이미지로 인식해 캐시를 재사용한다.
     */
    static Object toModel(String url) {
        if (url == null) {
            return null;
        }
        int q = url.indexOf('?');
        if (q <= 0) {
            return url; // 쿼리 없으면 URL 문자열 그대로(캐시 키 = URL)
        }
        final String cacheKey = url.substring(0, q);
        return new GlideUrl(url) {
            @Override
            public String getCacheKey() {
                return cacheKey;
            }
        };
    }
}
