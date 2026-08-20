package com.hackmin.connect.util;

import android.app.Activity;
import android.app.Dialog;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.hackmin.connect.ui.common.ClickGuard;

/**
 * 다음(카카오) 우편번호 서비스를 WebView로 띄워 '희망 배달 지역'을 고르게 하는 헬퍼.
 *
 * <p>배달앱의 주소 검색과 같은 UI지만, 정확한 번지 대신 <b>시/도 · 시/군/구 · 동</b>까지만
 * 조합해 돌려준다(예: "서울특별시 강남구 역삼동"). 별도 API 키가 필요 없다.</p>
 */
public final class RegionSearch {

    private RegionSearch() {}

    /** 지역 선택 결과 콜백. region = "시도 시군구 동" 형태. */
    public interface OnRegionSelected {
        void onSelected(String region);
    }

    private static final String PAGE =
            "<!DOCTYPE html><html><head>"
            + "<meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width, initial-scale=1, user-scalable=no'>"
            + "</head><body style='margin:0;'>"
            + "<div id='wrap' style='width:100%;height:100vh;'></div>"
            + "<script src='//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'></script>"
            + "<script>"
            + "new daum.Postcode({"
            + "  oncomplete: function(data){"
            // 법정동(bname)이 없으면 시군구까지만. 동 이상 정보는 버려 개인정보 최소화.
            + "    var parts = [data.sido, data.sigungu, data.bname].filter(function(s){return s && s.length;});"
            + "    AndroidBridge.onComplete(parts.join(' '));"
            + "  },"
            + "  width:'100%', height:'100%'"
            + "}).embed(document.getElementById('wrap'));"
            + "</script></body></html>";

    /** 지역 검색 다이얼로그를 띄운다. */
    public static void show(Activity activity, OnRegionSelected callback) {
        if (!ClickGuard.allow()) return; // 연타 → 다이얼로그 중복 방지
        WebView webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webView.setWebViewClient(new WebViewClient());

        Dialog dialog = new Dialog(activity);
        dialog.setContentView(webView);
        if (dialog.getWindow() != null) {
            int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.85);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
        }

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onComplete(String region) {
                activity.runOnUiThread(() -> {
                    callback.onSelected(region);
                    dialog.dismiss();
                });
            }
        }, "AndroidBridge");

        webView.loadDataWithBaseURL(
                "https://t1.daumcdn.net", PAGE, "text/html", "UTF-8", null);
        dialog.show();
    }
}
