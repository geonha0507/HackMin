package com.hackmin.app.util;

import android.app.Activity;
import android.app.Dialog;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 다음(카카오) 우편번호 서비스를 WebView로 띄워 실제 주소를 검색하게 하는 헬퍼.
 *
 * <p>별도 API 키가 필요 없는 공개 임베드 스크립트(postcode.v2.js)를 사용한다.
 * 사용자가 주소를 선택하면 JS가 {@code AndroidBridge.onComplete(...)}를 호출하고,
 * 그 값을 {@link OnAddressSelected} 콜백으로 전달한 뒤 다이얼로그를 닫는다.
 */
public final class PostcodeSearch {

    private PostcodeSearch() {}

    /** 주소 선택 결과 콜백. zonecode=우편번호, address=도로명(없으면 지번) 주소. */
    public interface OnAddressSelected {
        void onSelected(String zonecode, String address);
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
            + "    var addr = data.roadAddress || data.jibunAddress;"
            + "    AndroidBridge.onComplete(data.zonecode, addr);"
            + "  },"
            + "  width:'100%', height:'100%'"
            + "}).embed(document.getElementById('wrap'));"
            + "</script></body></html>";

    /** 우편번호 검색 다이얼로그를 띄운다. */
    public static void show(Activity activity, OnAddressSelected callback) {
        if (!com.hackmin.app.ui.common.ClickGuard.allow()) return; // 주소검색 연타 → 다이얼로그 중복 방지
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
            public void onComplete(String zonecode, String address) {
                // JS 스레드에서 호출되므로 UI 작업은 메인 스레드로 넘긴다.
                activity.runOnUiThread(() -> {
                    callback.onSelected(zonecode, address);
                    dialog.dismiss();
                });
            }
        }, "AndroidBridge");

        // 프로토콜 상대경로(//t1...)가 https로 해석되도록 baseUrl을 지정한다.
        webView.loadDataWithBaseURL(
                "https://t1.daumcdn.net", PAGE, "text/html", "UTF-8", null);
        dialog.show();
    }
}
