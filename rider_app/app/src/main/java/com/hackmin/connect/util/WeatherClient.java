package com.hackmin.connect.util;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;

/**
 * 현재 위치의 실제 날씨를 가져온다. 무료·API 키 불필요한 Open-Meteo를 사용한다.
 *
 * <p>앱의 암호화 OkHttp 클라이언트(CryptoInterceptor)를 쓰면 외부 API 호출이 깨지므로,
 * 여기서는 별도의 {@link HttpURLConnection}으로 평문 HTTPS 요청을 보낸다.</p>
 */
public final class WeatherClient {

    public enum Weather { CLEAR, CLOUDY, RAIN, SNOW }

    public interface Callback {
        void onResult(Weather weather);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private WeatherClient() {}

    /** 위경도로 현재 날씨를 조회한다. 실패하면 콜백이 호출되지 않는다(호출 측 기본값 유지). */
    public static void fetch(double lat, double lng, Callback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=weather_code",
                        lat, lng);
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() != 200) return;

                String body;
                try (InputStream in = conn.getInputStream();
                     Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
                    body = s.hasNext() ? s.next() : "";
                }
                int code = new JSONObject(body).getJSONObject("current").getInt("weather_code");
                Weather w = classify(code);
                MAIN.post(() -> callback.onResult(w));
            } catch (Exception ignored) {
                // 네트워크/파싱 실패는 조용히 무시 — 호출 측이 기본 배경을 유지한다.
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /** WMO weather_code → 4개 카테고리. */
    private static Weather classify(int code) {
        if (code == 0 || code == 1) return Weather.CLEAR;                 // 맑음/대체로 맑음
        if (code == 2 || code == 3 || code == 45 || code == 48) return Weather.CLOUDY; // 구름/흐림/안개
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Weather.SNOW; // 눈
        // 이슬비·비·소나기·뇌우
        return Weather.RAIN;
    }
}
