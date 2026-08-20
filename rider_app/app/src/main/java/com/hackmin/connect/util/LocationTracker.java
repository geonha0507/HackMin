package com.hackmin.connect.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

/**
 * 실시간 GPS 위치 추적 유틸.
 *
 * <p>플랫폼 {@link LocationManager}로 GPS·네트워크 프로바이더를 함께 구독해
 * 위치가 바뀔 때마다 콜백을 준다. Google Play Services 의존성이 없어 LDPlayer 등
 * 에뮬레이터에서도 (모의 위치 주입 시) 동작한다.</p>
 *
 * <p>권한(ACCESS_FINE/COARSE_LOCATION)은 호출 측 Activity가 런타임에 확보해야 한다.
 * {@link #hasPermission(Context)}로 확인하고 없으면 요청한 뒤 {@link #start()} 한다.</p>
 */
public final class LocationTracker implements LocationListener {

    /** 위치·주소 갱신 콜백. UI 스레드에서 호출된다. */
    public interface Callback {
        void onLocation(double latitude, double longitude, float accuracyMeters);

        /** 역지오코딩된 도로명/지번 주소. 실패하면 호출되지 않는다. */
        default void onAddress(String address) {}
    }

    // 최소 갱신 간격(ms)과 최소 이동 거리(m). "실시간" 체감을 위해 짧게 잡는다.
    private static final long MIN_INTERVAL_MS = 2000L;
    private static final float MIN_DISTANCE_M = 3f;

    private final Context appContext;
    private final LocationManager lm;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Geocoder geocoder;
    private boolean running = false;

    public LocationTracker(Context context, Callback callback) {
        this.appContext = context.getApplicationContext();
        this.lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        this.callback = callback;
        this.geocoder = Geocoder.isPresent()
                ? new Geocoder(appContext, Locale.KOREA) : null;
    }

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 위치 구독을 시작한다. 권한이 없으면 아무 것도 하지 않고 false 반환. */
    public boolean start() {
        if (running || lm == null || !hasPermission(appContext)) return false;
        try {
            // 마지막으로 알려진 위치를 먼저 한 번 흘려 초기 표시 지연을 줄인다.
            Location last = lastKnown();
            if (last != null) deliver(last);

            if (isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        MIN_INTERVAL_MS, MIN_DISTANCE_M, this, Looper.getMainLooper());
            }
            if (isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        MIN_INTERVAL_MS, MIN_DISTANCE_M, this, Looper.getMainLooper());
            }
            running = true;
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** 구독을 중단한다(백그라운드 전환·화면 종료 시 호출해 배터리 절약). */
    public void stop() {
        if (!running || lm == null) return;
        try {
            lm.removeUpdates(this);
        } catch (SecurityException ignored) {
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    private Location lastKnown() {
        try {
            Location gps = isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) : null;
            Location net = isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    ? lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) : null;
            if (gps == null) return net;
            if (net == null) return gps;
            return gps.getTime() >= net.getTime() ? gps : net;
        } catch (SecurityException e) {
            return null;
        }
    }

    private boolean isProviderEnabled(String provider) {
        try {
            return lm.isProviderEnabled(provider);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        deliver(location);
    }

    private void deliver(Location location) {
        callback.onLocation(location.getLatitude(), location.getLongitude(),
                location.hasAccuracy() ? location.getAccuracy() : 0f);
        reverseGeocode(location.getLatitude(), location.getLongitude());
    }

    /** 좌표를 한글 주소로 역지오코딩한다(백그라운드 스레드). Android 33+ 비동기 API가 없어 스레드 사용. */
    private void reverseGeocode(double lat, double lng) {
        if (geocoder == null) return;
        new Thread(() -> {
            try {
                List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                if (results == null || results.isEmpty()) return;
                Address a = results.get(0);
                String line = a.getMaxAddressLineIndex() >= 0
                        ? a.getAddressLine(0) : null;
                if (line == null) return;
                // "대한민국 " 접두어는 지저분해서 떼어낸다.
                final String cleaned = line.replaceFirst("^대한민국\\s*", "");
                main.post(() -> callback.onAddress(cleaned));
            } catch (Exception ignored) {
                // 네트워크 지오코더 실패는 조용히 무시(좌표는 이미 표시됨).
            }
        }).start();
    }

    // 하위 API 호환용 빈 구현(일부 단말이 호출).
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
}
