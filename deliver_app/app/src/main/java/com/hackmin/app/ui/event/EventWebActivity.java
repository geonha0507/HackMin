package com.hackmin.app.ui.event;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.SessionManager;

/**
 * 이벤트/프로모션 상세 웹뷰.
 *
 * <p>이벤트·쿠폰 프로모션 페이지는 서버에서 운영·변경되므로, 앱 배포 없이 내용을
 * 바꿀 수 있도록 네이티브 화면 대신 서버가 내려주는 웹 페이지를 WebView로 로드한다.
 *
 * <p>웹 페이지가 "내 쿠폰함/멤버십 등급/응모 내역"을 개인화해서 보여주려면 로그인
 * 상태로 프로모션 API를 호출해야 한다. 이를 위해 현재 세션의 액세스 토큰과 사용자
 * 식별자를 JS 브리지({@code HackminBridge})로 웹 페이지에 전달한다.
 */
public class EventWebActivity extends AppCompatActivity {

    /** 열고자 하는 이벤트 페이지 경로. 예: "/events/latenight". 없으면 이벤트 홈. */
    public static final String EXTRA_PATH = "event_path";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        // 이벤트 웹페이지가 로그인 상태로 프로모션 API를 호출할 수 있도록
        // 세션 정보를 전달한다. (웹뷰 하이브리드 연동)
        webView.addJavascriptInterface(new WebBridge(), "HackminBridge");

        setContentView(webView);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null || path.isEmpty()) {
            // nginx 가 이미 프록시하는 /api/ 밑에 이벤트 페이지가 있다(별도 nginx 라우팅 불필요).
            path = "/api/v1/events/";
        }
        webView.loadUrl(ApiClient.mediaBaseUrl() + path);
    }

    /** 이벤트 웹페이지 ↔ 앱 세션 연동 브리지. */
    private final class WebBridge {

        /** 이벤트 페이지의 개인화 API 호출용 액세스 토큰(Authorization: Bearer). */
        @JavascriptInterface
        public String getAuthToken() {
            return SessionManager.getInstance(EventWebActivity.this).getAccessToken();
        }

        /** 이벤트 응모/쿠폰 발급에 쓰는 로그인 사용자 식별자. */
        @JavascriptInterface
        public long getUserId() {
            return SessionManager.getInstance(EventWebActivity.this).getUserId();
        }
    }
}
