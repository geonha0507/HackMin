package com.hackmin.connect.ui.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.hackmin.connect.BuildConfig;
import com.hackmin.connect.R;
import com.hackmin.connect.ui.common.BaseActivity;
import com.hackmin.connect.util.LocationTracker;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Locale;

/**
 * 실제 지도(OpenStreetMap) 위에 내 실시간 위치를 마커로 표시한다.
 * 위치 소스는 앱 공용 {@link LocationTracker}(Fused/폴백)를 재사용한다. API 키 불필요.
 */
public class MapActivity extends BaseActivity {

    private static final int REQ_LOCATION = 2001;

    private MapView map;
    private Marker marker;
    private TextView tvState, tvAddress, tvCoords;
    private LocationTracker tracker;
    private boolean centeredOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // osmdroid 설정(캐시 경로·User-Agent). setContentView 전에 호출해야 한다.
        Configuration.getInstance().load(this,
                getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        setContentView(R.layout.activity_map);

        tvState = findViewById(R.id.tv_map_state);
        tvAddress = findViewById(R.id.tv_map_address);
        tvCoords = findViewById(R.id.tv_map_coords);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);

        marker = new Marker(map);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle("내 위치");
        map.getOverlays().add(marker);

        tracker = new LocationTracker(this, new LocationTracker.Callback() {
            @Override
            public void onLocation(double latitude, double longitude, float accuracyMeters) {
                GeoPoint p = new GeoPoint(latitude, longitude);
                marker.setPosition(p);
                if (!centeredOnce) {
                    map.getController().setCenter(p);
                    centeredOnce = true;
                } else {
                    map.getController().animateTo(p);
                }
                map.invalidate();
                tvState.setText(accuracyMeters > 0
                        ? "정확도 ±" + Math.round(accuracyMeters) + "m" : "위치 확인됨");
                tvCoords.setText(String.format(Locale.US, "%.6f, %.6f", latitude, longitude));
            }

            @Override
            public void onAddress(String address) {
                tvAddress.setText(address);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        startTracking();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        tracker.stop();
    }

    private void startTracking() {
        if (!LocationTracker.hasPermission(this)) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            }, REQ_LOCATION);
            return;
        }
        if (!tracker.start()) {
            tvState.setText("위치 사용 불가");
            tvAddress.setText("기기 위치(GPS)를 켜주세요");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        boolean granted = false;
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_GRANTED) granted = true;
        }
        if (granted) {
            startTracking();
        } else {
            tvState.setText("권한 거부됨");
            tvAddress.setText("설정에서 위치 권한을 허용해 주세요");
        }
    }
}
