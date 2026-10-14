package com.mmwtl.atlasdim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** После загрузки ГУ или обновления APK возвращает на приборку последнюю применённую композицию. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action);
        if (!boot && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        if (SceneService.isActive(context) && (!boot
                || SceneService.prefs(context).getBoolean(SceneService.KEY_BOOT, true))) {
            SceneService.start(context);
        }
    }
}
