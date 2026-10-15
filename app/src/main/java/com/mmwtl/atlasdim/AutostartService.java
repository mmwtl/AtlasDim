package com.mmwtl.atlasdim;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

/**
 * Резервный автозапуск через спецвозможности (идея — AutoApplyAccessibilityService
 * AtlasCodecFix): на Ситирее (G426_J1) BOOT_COMPLETED до приложения доходит не всегда,
 * а включённый сервис спецвозможностей система поднимает сама после загрузки и держит.
 * Окна не читает и жестов не делает — только возвращает применённую композицию, если
 * её сервис не идёт: при подключении, при включении экрана и при смене окон.
 */
public final class AutostartService extends AccessibilityService {
    /** Проверка дешёвая, но смена окон бывает часто — не чаще раза в 10 с. */
    private static final long CHECK_MS = 10_000;

    private long checkedAt;
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ensureScene();
        }
    };

    /** Включён ли сервис пользователем в спецвозможностях. */
    static boolean enabled(Context context) {
        String list = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) {
            return false;
        }
        ComponentName self = new ComponentName(context, AutostartService.class);
        TextUtils.SimpleStringSplitter split = new TextUtils.SimpleStringSplitter(':');
        split.setString(list);
        for (String item : split) {
            if (self.equals(ComponentName.unflattenFromString(item))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        ensureScene();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        if (SystemClock.elapsedRealtime() - checkedAt >= CHECK_MS) {
            ensureScene();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screenOn);
        } catch (IllegalArgumentException ignored) {
            // Сервис не успел подключиться.
        }
        super.onDestroy();
    }

    /** Как BootReceiver: композиция включена, возврат после загрузки не выключен, сервис не идёт. */
    private void ensureScene() {
        checkedAt = SystemClock.elapsedRealtime();
        if (SceneService.isActive(this) && !SceneService.isRunning()
                && SceneService.prefs(this).getBoolean(SceneService.KEY_BOOT, true)) {
            SceneService.start(this);
        }
    }
}
