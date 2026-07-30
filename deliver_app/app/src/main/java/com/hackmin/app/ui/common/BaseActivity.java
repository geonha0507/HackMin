package com.hackmin.app.ui.common;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 모든 화면의 공용 부모 액티비티.
 *
 * <p>startActivity를 가로채 {@link ClickGuard}로 디바운스한다. 버튼/메뉴/목록 항목을
 * 연타해도 화면이 한 번만 열린다. 두 오버로드를 모두 재정의하되, 실제 가드는
 * {@code startActivity(Intent, Bundle)} 한 곳에서만 소비해 이중 차감을 방지한다.</p>
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    public void startActivity(Intent intent) {
        // 인자 1개 오버로드도 반드시 가드가 걸린 아래 오버로드를 거치게 한다.
        startActivity(intent, null);
    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        if (!ClickGuard.allow()) {
            return; // 연타로 인한 중복 화면 오픈 차단
        }
        super.startActivity(intent, options);
    }
}
