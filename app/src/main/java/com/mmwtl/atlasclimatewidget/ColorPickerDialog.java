package com.mmwtl.atlasclimatewidget;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** RGB picker with a live swatch, as in AtlasAppWidget; the result is opaque. */
final class ColorPickerDialog {
    interface Listener {
        void onColorSelected(int color);
    }

    private ColorPickerDialog() {
    }

    static AlertDialog show(Context context, String title, int initialColor,
            Listener listener) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(context, 24), Ui.dp(context, 10),
                Ui.dp(context, 24), Ui.dp(context, 4));

        View preview = new View(context);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 70));
        previewParams.bottomMargin = Ui.dp(context, 14);
        content.addView(preview, previewParams);

        int[] channels = {Color.red(initialColor), Color.green(initialColor),
                Color.blue(initialColor)};
        TextView[] labels = new TextView[3];
        String[] names = {
                context.getString(R.string.color_red),
                context.getString(R.string.color_green),
                context.getString(R.string.color_blue)
        };

        Runnable update = () -> {
            preview.setBackground(Ui.rounded(Color.rgb(channels[0], channels[1], channels[2]),
                    Ui.dp(context, 8)));
            for (int index = 0; index < 3; index++) {
                labels[index].setText(context.getString(R.string.color_channel,
                        names[index], channels[index]));
            }
        };

        for (int index = 0; index < 3; index++) {
            int channelIndex = index;
            labels[index] = Ui.text(context, "", 14, Ui.TEXT);
            content.addView(labels[index]);
            SeekBar slider = new SeekBar(context);
            slider.setMax(255);
            slider.setProgress(channels[index]);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    channels[channelIndex] = progress;
                    update.run();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            content.addView(slider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        update.run();

        return new AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setView(content)
                .setNegativeButton(R.string.setup_cancel, null)
                .setPositiveButton(R.string.color_apply, (dialog, which) ->
                        listener.onColorSelected(Color.rgb(channels[0], channels[1], channels[2])))
                .show();
    }
}
