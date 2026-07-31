package com.hackmin.app.network;

import android.content.Context;
import android.content.SharedPreferences;

import com.hackmin.app.security.SecureStore;

/**
 * 앱 전역 세션 저장소.
 *
 * <p>액세스/리프레시 토큰과 로그인 사용자 정보를 한 곳에서 관리한다.
 * {@link AuthInterceptor.TokenProvider}를 그대로 구현하므로, 각 화면에서
 * 람다를 새로 만들 필요 없이 이 싱글톤을 인터셉터에 바로 넘길 수 있다.
 *
 * <p>사용 예시(각 Activity):
 * <pre>{@code
 *   SessionManager session = SessionManager.getInstance(this);
 *   ApiClient.restaurantApi(this).searchRestaurants(...).enqueue(...);
 *   session.saveTokens(access, refresh);   // 로그인 성공 시
 *   session.clear();                        // 로그아웃 시
 * }</pre>
 */
public final class SessionManager
        implements AuthInterceptor.TokenProvider {

    private static final String PREFS_NAME = "HackminPrefs";

    // 기존 화면들이 쓰던 키와 동일하게 유지(하위 호환).
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_SAVED_ID = "saved_id";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_NICKNAME = "nickname";

    private static volatile SessionManager instance;

    private final SharedPreferences prefs;


    private SessionManager(Context context) {
        // Activity가 destroy돼도 살아있도록 applicationContext 사용.
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager(context);
                }
            }
        }
        return instance;
    }

    // ── 토큰 ──────────────────────────────────────────────

    /**
     * 로그인/회원가입 성공 시 발급받은 토큰을 저장한다.
     *
     * <p>access/refresh 토큰(JWT)은 Android Keystore(AES-256/GCM)로 암호화해 저장한다.
     * 따라서 루팅 단말에서 {@code shared_prefs/HackminPrefs.xml}를 덤프해도 평문 토큰이
     * 아니라 암호문만 노출된다. 키는 키스토어 밖으로 나오지 않는다.</p>
     */
    public void saveTokens(String accessToken, String refreshToken) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, SecureStore.encrypt(accessToken))
                .putString(KEY_REFRESH_TOKEN, SecureStore.encrypt(refreshToken))
                .apply();
    }

    /** {@link AuthInterceptor.TokenProvider} 구현. 헤더에 실릴 액세스 토큰. */
    @Override
    public String getAccessToken() {
        return SecureStore.decrypt(prefs.getString(KEY_ACCESS_TOKEN, ""));
    }

    public String getRefreshToken() {
        return SecureStore.decrypt(prefs.getString(KEY_REFRESH_TOKEN, ""));
    }

    /** 액세스 토큰이 있으면 로그인 상태로 간주. */
    public boolean isLoggedIn() {
        String token = getAccessToken();
        return token != null && !token.isEmpty();
    }

    // ── 사용자 정보 ────────────────────────────────────────

    /** 로그인 응답의 사용자 정보를 저장한다(마이페이지 등에서 사용). */
    public void saveUser(long userId, String username, String nickname) {
        prefs.edit()
                .putLong(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username == null ? "" : username)
                .putString(KEY_NICKNAME, nickname == null ? "" : nickname)
                .apply();
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1L);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public String getNickname() {
        return prefs.getString(KEY_NICKNAME, "");
    }

    // ── 아이디 저장(로그인 화면 편의) ─────────────────────────

    public void setSavedId(String username) {
        prefs.edit().putString(KEY_SAVED_ID, username == null ? "" : username).apply();
    }

    public void clearSavedId() {
        prefs.edit().remove(KEY_SAVED_ID).apply();
    }

    public String getSavedId() {
        return prefs.getString(KEY_SAVED_ID, "");
    }

    // ── 로그아웃 ──────────────────────────────────────────

    /**
     * 세션을 초기화한다(로그아웃). 저장된 아이디(saved_id)는 편의를 위해 남긴다.
     */
    public void clear() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_NICKNAME)
                .apply();
    }
}
