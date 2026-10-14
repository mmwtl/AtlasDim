package com.mmwtl.atlasdim;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

/**
 * Applies the app's own interface scale without changing the head unit density.
 * Copied from AtlasClimateWidget; the scale lives in the {@link SceneService#PREFS} prefs.
 */
abstract class ScaledActivity extends Activity {
    static final String KEY_UI_SCALE_TENTHS = "ui.scale";
    static final int MIN_SCALE_TENTHS = 10;
    static final int MAX_SCALE_TENTHS = 20;
    static final int DEFAULT_SCALE_TENTHS = 15;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(scaledContext(newBase));
    }

    static int configuredScaleTenths(Context context) {
        return Math.max(MIN_SCALE_TENTHS, Math.min(MAX_SCALE_TENTHS,
                SceneService.prefs(context).getInt(KEY_UI_SCALE_TENTHS, DEFAULT_SCALE_TENTHS)));
    }

    static void setScaleTenths(Context context, int value) {
        SceneService.prefs(context).edit().putInt(KEY_UI_SCALE_TENTHS, value).apply();
    }

    private static Context scaledContext(Context base) {
        Configuration configuration = new Configuration(
                base.getResources().getConfiguration());
        int baseDensityDpi = configuration.densityDpi;
        if (baseDensityDpi == Configuration.DENSITY_DPI_UNDEFINED) {
            baseDensityDpi = base.getResources().getDisplayMetrics().densityDpi;
        }
        configuration.densityDpi = Math.round(
                baseDensityDpi * configuredScaleTenths(base) / 10f);
        return base.createConfigurationContext(configuration);
    }
}
