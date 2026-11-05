package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class WidgetThemeModeTest {
    private boolean nightWith(int mode, String systemMode) {
        RuntimeEnvironment.setQualifiers(systemMode);
        Context context = RuntimeEnvironment.getApplication();
        new Prefs(context).setThemeMode(mode);
        return WidgetTheme.of(WidgetTheme.themed(context)).night;
    }

    @Test public void autoFollowsTheHeadUnit() {
        assertTrue(nightWith(WidgetTheme.AUTO, "+night"));
        assertFalse(nightWith(WidgetTheme.AUTO, "+notnight"));
    }

    @Test public void fixedThemeIgnoresTheHeadUnit() {
        assertTrue(nightWith(WidgetTheme.FIXED_NIGHT, "+notnight"));
        assertFalse(nightWith(WidgetTheme.FIXED_DAY, "+night"));
    }

    @Test public void fixedThemeAlsoPicksTheResources() {
        RuntimeEnvironment.setQualifiers("+notnight");
        Context context = RuntimeEnvironment.getApplication();
        new Prefs(context).setThemeMode(WidgetTheme.FIXED_NIGHT);
        assertEquals(WidgetTheme.NIGHT.card,
                WidgetTheme.themed(context).getColor(R.color.widget_surface));
    }

    @Test public void unknownModeIsAuto() {
        Context context = RuntimeEnvironment.getApplication();
        new Prefs(context).setThemeMode(7);
        assertEquals(WidgetTheme.AUTO, new Prefs(context).themeMode());
    }
}
