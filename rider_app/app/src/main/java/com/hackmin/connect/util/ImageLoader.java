package com.hackmin.connect.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.hackmin.connect.R;
import com.hackmin.connect.network.ApiClient;

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

    /**
     * 매장 이미지를 로드한다. 서버 URL이면 {@link #load(ImageView, String)}로 위임하고,
     * {@code "res:이름"} 형태면 앱 번들 drawable을 표시한다(미리보기/오프라인 대체용;
     * 실서버 응답은 항상 http/상대경로라 이 분기는 타지 않는다).
     */
    public static void loadStore(ImageView view, String value) {
        if (value != null && value.startsWith("res:")) {
            String name = value.substring("res:".length());
            int resId = view.getContext().getResources()
                    .getIdentifier(name, "drawable", view.getContext().getPackageName());
            view.setImageResource(resId != 0 ? resId : R.drawable.ic_image_placeholder);
            return;
        }
        load(view, value);
    }

    /** 음식점/메뉴 등 이미지를 로드한다. url이 null/빈값이면 placeholder 표시. */
    public static void load(ImageView view, String url) {
        // 비동기 응답이 화면이 닫힌 뒤 도착하면 Glide.with()가 "destroyed activity" 예외로 크래시한다.
        // 파괴/종료 중인 액티비티면 로드를 건너뛴다.
        Activity activity = findActivity(view.getContext());
        if (activity != null && (activity.isDestroyed() || activity.isFinishing())) {
            return;
        }
        Glide.with(view.getContext())
                .load(toModel(resolve(url)))
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .centerCrop()
                .into(view);
    }

    /** 뷰의 Context에서 실제 Activity를 찾는다(ContextWrapper로 감싸진 경우 포함). */
    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /**
     * 이미지를 화면에 표시하지 않고 Glide 캐시에만 미리 받아둔다(프리로드).
     * 앱 실행 초기에 호출해두면 이후 목록/상세에서 즉시 표시된다.
     */
    public static void preload(Context context, String url) {
        String resolved = resolve(url);
        if (resolved == null) {
            return;
        }
        Glide.with(context.getApplicationContext())
                .load(toModel(resolved))
                .preload();
    }

    /** 상대경로면 서버 오리진을 붙여 절대 URL로 변환한다. 비었으면 null. */
    public static String resolve(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        String resolved;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            resolved = url;
        } else {
            String base = ApiClient.mediaBaseUrl();
            resolved = url.startsWith("/") ? base + url : base + "/" + url;
        }
        // 서버(Django가 HTTPS 프록시 뒤인 걸 모를 때)가 우리 도메인 이미지를 http://로 주는 경우가 있다.
        // Android는 cleartext(http) 트래픽을 막아 이미지가 안 뜨므로, 우리 미디어 도메인에 한해 https로 승격한다.
        String secureBase = ApiClient.mediaBaseUrl();            // 예) https://hackmin.com
        if (secureBase.startsWith("https://")) {
            String httpBase = "http://" + secureBase.substring("https://".length()); // 예) http://hackmin.com
            if (resolved.startsWith(httpBase)) {
                resolved = secureBase + resolved.substring(httpBase.length());
            }
        }
        return resolved;
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
