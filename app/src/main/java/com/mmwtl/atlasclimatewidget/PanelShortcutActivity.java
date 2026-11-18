package com.mmwtl.atlasclimatewidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * «Окно климата» in the system shortcut pickers. GInputBridge binds a steering wheel button to
 * a picked shortcut («Запуск ярлыка»), and a launcher may place it on the home screen; both start the
 * returned intent, which opens or closes {@link ClimatePanelActivity}.
 */
public final class PanelShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction())) {
            setResult(RESULT_OK, shortcut());
        }
        finish();
    }

    @SuppressWarnings("deprecation") // The legacy extras are what GInputBridge reads.
    private Intent shortcut() {
        return new Intent()
                .putExtra(Intent.EXTRA_SHORTCUT_INTENT, ClimatePanelActivity.intent(this))
                .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.panel_shortcut))
                .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher));
    }
}
