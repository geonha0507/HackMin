package com.hackmin.app.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.concurrent.TimeUnit;

/**
 * 주문 상태 실시간 수신을 위한 WebSocket 클라이언트.
 *
 * <p>서버 엔드포인트: {@code wss://hackmin.com/ws/orders/<order_id>/status/?token=<jwt>}
 *
 * <p>사용법:
 * <pre>{@code
 *   client = new OrderWebSocketClient(orderId, accessToken, status -> {
 *       // UI 업데이트 (메인 스레드에서 호출됨)
 *   });
 *   client.connect();     // onStart 또는 onCreate
 *   client.disconnect();  // onStop 또는 onDestroy
 * }</pre>
 */
public class OrderWebSocketClient {

    private static final String TAG = "OrderWS";

    /** 서버 WebSocket 베이스. ApiClient.BASE_URL 에서 파생한다. */
    private static final String WS_BASE = "wss://hackmin.com";

    private static final int NORMAL_CLOSE = 1000;
    private static final long INITIAL_RETRY_MS = 2_000;
    private static final long MAX_RETRY_MS = 30_000;

    /** 메인 스레드 콜백 */
    public interface StatusListener {
        void onStatusChanged(String status, String statusDisplay);
    }

    private final long orderId;
    private final String token;
    private final StatusListener listener;

    private final OkHttpClient httpClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    @Nullable private WebSocket webSocket;
    private boolean stopped = false;
    private long retryDelay = INITIAL_RETRY_MS;

    public OrderWebSocketClient(long orderId, String token, StatusListener listener) {
        this.orderId = orderId;
        this.token = token;
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)   // WebSocket은 무한 대기
                .pingInterval(30, TimeUnit.SECONDS)       // 연결 유지용 ping
                .build();
    }

    /** WebSocket 연결을 시작한다. 이미 연결 중이면 무시. */
    public void connect() {
        if (webSocket != null) return;
        stopped = false;

        String url = WS_BASE + "/ws/orders/" + orderId + "/status/?token=" + token;
        Log.d(TAG, "Connecting: " + url);

        Request request = new Request.Builder().url(url).build();
        webSocket = httpClient.newWebSocket(request, new WsListener());
    }

    /** WebSocket 연결을 종료한다. 재연결도 중단된다. */
    public void disconnect() {
        stopped = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (webSocket != null) {
            webSocket.close(NORMAL_CLOSE, "activity stopped");
            webSocket = null;
        }
    }

    // ── 내부 리스너 ─────────────────────────────────────────

    private class WsListener extends WebSocketListener {

        @Override
        public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
            Log.d(TAG, "Connected (order=" + orderId + ")");
            retryDelay = INITIAL_RETRY_MS;   // 연결 성공 → 지연 초기화
        }

        @Override
        public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
            Log.d(TAG, "Message: " + text);
            try {
                JsonObject json = gson.fromJson(text, JsonObject.class);
                String status = json.has("status") ? json.get("status").getAsString() : null;
                String display = json.has("status_display") ? json.get("status_display").getAsString() : null;
                if (status != null && listener != null) {
                    mainHandler.post(() -> listener.onStatusChanged(status, display));
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse message", e);
            }
        }

        @Override
        public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
            Log.d(TAG, "Server closing: " + code + " " + reason);
            ws.close(NORMAL_CLOSE, null);
        }

        @Override
        public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
            Log.d(TAG, "Closed: " + code + " " + reason);
            webSocket = null;
            scheduleReconnect();
        }

        @Override
        public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t,
                              @Nullable Response response) {
            Log.w(TAG, "Connection failed", t);
            webSocket = null;
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (stopped) return;
        Log.d(TAG, "Reconnecting in " + retryDelay + "ms");
        mainHandler.postDelayed(() -> {
            if (!stopped) {
                connect();
            }
        }, retryDelay);
        retryDelay = Math.min(retryDelay * 2, MAX_RETRY_MS);
    }
}
