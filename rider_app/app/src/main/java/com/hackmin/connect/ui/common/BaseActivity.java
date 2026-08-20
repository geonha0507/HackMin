package com.hackmin.connect.ui.common;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 모든 화면의 공용 부모 액티비티.
 *
 * <p>startActivity를 가로채 연타로 인한 중복 화면 오픈을 막는다. 버튼/메뉴/목록 항목을
 * 아무리 빨리·오래 눌러도 이 화면에서 다음 화면은 한 번만 열린다. 두 오버로드를 모두
 * 재정의하되, 실제 가드는 {@code startActivity(Intent, Bundle)} 한 곳에서만 소비한다.</p>
 *
 * <p>가드는 두 겹이다.</p>
 * <ol>
 *   <li><b>전역 디바운스</b>({@link ClickGuard}): 서로 다른 화면의 거의 동시 전환을 막는다.</li>
 *   <li><b>화면별 latch</b>({@link #navigatingAway}): 한 번 다음 화면으로 넘어가면, 이 화면으로
 *       다시 <b>돌아올 때({@link #onResume})까지</b> 추가 전환을 모두 무시한다. 전역 디바운스의
 *       600ms 창이 지나도록 이어지는 지속 연타에도 중복 화면이 쌓이지 않는다.</li>
 * </ol>
 */
public class BaseActivity extends AppCompatActivity {

    /** 이 화면에서 이미 다음 화면으로 전환했는지. 화면 복귀({@link #onResume}) 시 해제된다. */
    private boolean navigatingAway = false;

    @Override
    protected void onResume() {
        super.onResume();
        // 다음 화면에서 돌아왔으면(또는 최초 진입) 다시 전환할 수 있게 latch 해제.
        navigatingAway = false;
    }

    @Override
    public void startActivity(Intent intent) {
        // 인자 1개 오버로드도 반드시 가드가 걸린 아래 오버로드를 거치게 한다.
        startActivity(intent, null);
    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        // 이 화면에서 이미 전환했거나(latch), 직전 전환 직후(전역 디바운스)면 무시.
        if (navigatingAway || !ClickGuard.allow()) {
            return; // 연타로 인한 중복 화면 오픈 차단
        }
        navigatingAway = true;
        try {
            super.startActivity(intent, options);
        } catch (RuntimeException e) {
            // 전환에 실패하면(대상 없음 등) latch를 풀어 재시도 가능하게 둔다.
            navigatingAway = false;
            throw e;
        }
    }
}
