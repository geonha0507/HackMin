package com.hackmin.connect.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;
import java.util.Locale;

/**
 * 실시간 GPS 위치 추적 유틸.
 *
 * <p><b>기본은 FusedLocationProvider</b>(GPS+와이파이+기지국+센서를 융합해 더 정확하고
 * 배터리 효율적)를 쓴다. Google Play Services 가 없는 기기에서는 플랫폼
 * {@link LocationManager}(GPS·네트워크 프로바이더)로 자동 폴백해 어디서나 동작한다.</p>
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

        /** [방어 ②] 모의 위치(가짜 GPS 앱)가 감지됐을 때. 기본은 무시. */
        default void onMockDetected() {}
    }

    // 최소 갱신 간격(ms)과 최소 이동 거리(m). "실시간" 체감을 위해 짧게 잡는다.
    private static final long UPDATE_INTERVAL_MS = 2000L;
    private static final long FASTEST_INTERVAL_MS = 1000L;
    private static final float MIN_DISTANCE_M = 3f;

    private final Context appContext;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Geocoder geocoder;
    private boolean running = false;

    // ── Fused 경로 ──
    private final boolean useFused;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback fusedCallback;

    // ── 폴백(LocationManager) 경로 ──
    private final LocationManager lm;

    public LocationTracker(Context context, Callback callback) {
        this.appContext = context.getApplicationContext();
        this.callback = callback;
        this.lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        this.geocoder = Geocoder.isPresent() ? new Geocoder(appContext, Locale.KOREA) : null;
        this.useFused = isPlayServicesAvailable(appContext);
        if (useFused) {
            this.fusedClient = LocationServices.getFusedLocationProviderClient(appContext);
        }
    }

    private static boolean isPlayServicesAvailable(Context context) {
        try {
            return GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 위치 구독을 시작한다. 권한이 없으면 아무 것도 하지 않고 false 반환. */
    public boolean start() {
        if (running || !hasPermission(appContext)) return false;
        boolean ok = useFused ? startFused() : startManager();
        running = ok;
        return ok;
    }

    /** 구독을 중단한다(백그라운드 전환·화면 종료 시 호출해 배터리 절약). */
    public void stop() {
        if (!running) return;
        try {
            if (useFused) {
                if (fusedClient != null && fusedCallback != null) {
                    fusedClient.removeLocationUpdates(fusedCallback);
                }
            } else if (lm != null) {
                lm.removeUpdates(this);
            }
        } catch (SecurityException ignored) {
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    // ── Fused 경로 구현 ──────────────────────────────────────

    private boolean startFused() {
        try {
            // 마지막 위치를 먼저 흘려 초기 표시 지연을 줄인다.
            fusedClient.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) deliver(loc);
            });

            LocationRequest request = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
                    .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                    .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
                    .build();

            fusedCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult result) {
                    Location loc = result.getLastLocation();
                    if (loc != null) deliver(loc);
                }
            };
            fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    // ── 폴백(LocationManager) 경로 구현 ─────────────────────────

    private boolean startManager() {
        if (lm == null) return false;
        try {
            Location last = lastKnown();
            if (last != null) deliver(last);

            if (isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        UPDATE_INTERVAL_MS, MIN_DISTANCE_M, this, Looper.getMainLooper());
            }
            if (isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        UPDATE_INTERVAL_MS, MIN_DISTANCE_M, this, Looper.getMainLooper());
            }
            return true;
        } catch (SecurityException e) {
            return false;
        }
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
        deliver(location); // LocationManager 폴백 경로에서 호출
    }

    // ── 공통 ────────────────────────────────────────────────

    private void deliver(Location location) {
        // [방어 ②] 모의 위치(가짜 GPS 앱)로 만든 좌표는 신뢰하지 않는다 — 실제 센서 위치만 사용.
        // → 무루팅 '가짜GPS 앱' 경로가 막힌다. 우회하려면 Frida로 isMock/isFromMockProvider 를
        //   후킹해 'false'로 거짓말시켜야 한다(= 루팅 필요). 그래서 GPS 조작에 루팅이 강제된다.
        if (isMockLocation(location)) {
            main.post(callback::onMockDetected);
            return;
        }
        callback.onLocation(location.getLatitude(), location.getLongitude(),
                location.hasAccuracy() ? location.getAccuracy() : 0f);
        reverseGeocode(location.getLatitude(), location.getLongitude());
    }

    /** 이 위치가 모의 위치 제공자(가짜 GPS 앱)에서 왔는지. API 31+ 는 isMock(), 이하는 isFromMockProvider(). */
    private static boolean isMockLocation(Location loc) {
        if (loc == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return loc.isMock();
            }
            return loc.isFromMockProvider();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 좌표를 한글 주소로 역지오코딩한다(백그라운드 스레드). Android 33+ 비동기 API가 없어 스레드 사용. */
    private void reverseGeocode(double lat, double lng) {
        if (geocoder == null) return;
        new Thread(() -> {
            try {
                List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                if (results == null || results.isEmpty()) return;
                Address a = results.get(0);
                String line = a.getMaxAddressLineIndex() >= 0 ? a.getAddressLine(0) : null;
                if (line == null) return;
                // "대한민국 " 접두어는 지저분해서 떼어낸다.
                final String cleaned = line.replaceFirst("^대한민국\\s*", "");
                main.post(() -> callback.onAddress(cleaned));
            } catch (Exception ignored) {
                // 네트워크 지오코더 실패는 조용히 무시(좌표는 이미 표시됨).
            }
        }).start();
    }

    // LocationManager 폴백 경로용 하위 API 호환 빈 구현.
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
}
