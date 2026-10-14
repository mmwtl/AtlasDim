package com.mmwtl.atlasdim;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Voluntary donation window, shared by the Atlas apps: the CloudTips QR code to scan with a
 * phone, since head units often have no browser, and the link itself. Copied from
 * AtlasClimateWidget; needs only {@link Ui},
 * the {@code donate_*} strings, {@code ic_donate_qr} and a {@code VIEW https} query.
 */
final class DonateDialog {
    private static final int QR_SIZE_DP = 260;

    private DonateDialog() {
    }

    static void show(Activity activity) {
        Uri link = Uri.parse(activity.getString(R.string.donate_url));
        Intent view = new Intent(Intent.ACTION_VIEW, link);
        boolean browser = view.resolveActivity(activity.getPackageManager()) != null;

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(Ui.dp(activity, 24), Ui.dp(activity, 8), Ui.dp(activity, 24),
                Ui.dp(activity, 4));

        TextView message = Ui.text(activity, R.string.donate_message, 15, Ui.TEXT_SECONDARY);
        message.setLineSpacing(0, 1.2f);
        body.addView(message);

        // The code keeps its white quiet zone; rounded corners only soften it on graphite.
        ImageView qr = new ImageView(activity);
        qr.setImageResource(R.drawable.ic_donate_qr);
        qr.setBackground(Ui.rounded(0xFFFFFFFF, Ui.dp(activity, 12)));
        qr.setClipToOutline(true);
        qr.setContentDescription(link.toString());
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(
                Ui.dp(activity, QR_SIZE_DP), Ui.dp(activity, QR_SIZE_DP));
        qrParams.topMargin = Ui.dp(activity, 16);
        body.addView(qr, qrParams);

        TextView scan = Ui.text(activity, R.string.donate_scan, 14, Ui.TEXT);
        scan.setGravity(Gravity.CENTER);
        body.addView(scan, centred(activity, 12));
        TextView url = Ui.text(activity, link.toString(), 14, Ui.ACCENT);
        url.setGravity(Gravity.CENTER);
        url.setTextIsSelectable(true);
        body.addView(url, centred(activity, 4));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity,
                android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.donate_title)
                .setView(body)
                .setNegativeButton(R.string.donate_close, null);
        if (browser) {
            builder.setPositiveButton(R.string.donate_open, (dialog, which) -> {
                try {
                    activity.startActivity(view);
                } catch (ActivityNotFoundException error) {
                    Toast.makeText(activity, R.string.donate_no_browser, Toast.LENGTH_LONG)
                            .show();
                }
            });
        }
        AlertDialog dialog = builder.show();
        // The scaled activity density widens default dialogs past the screen edge.
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout(Math.min(screen - Ui.dp(activity, 48),
                Ui.dp(activity, 520)), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams centred(Activity activity, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(activity, topMarginDp);
        return params;
    }
}
