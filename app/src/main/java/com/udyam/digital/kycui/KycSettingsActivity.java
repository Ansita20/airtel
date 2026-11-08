package com.udyam.digital.kycui;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.udyam.digital.R;
import com.udyam.digital.kyc.KycApplication;

/** Consent record and "Withdraw consent and delete my documents" (really deletes the files). */
public class KycSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // FLAG_SECURE comes from SecureScreens (app-wide policy).
        KycStore store = new KycStore(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        MaterialToolbar bar = new MaterialToolbar(this);
        bar.setTitle(R.string.settings_title);
        bar.setNavigationIcon(R.drawable.ic_arrow_back);
        bar.setNavigationOnClickListener(v -> finish());
        com.udyam.digital.ui.HelpButton.addTo(bar, this);
        root.addView(bar);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        root.addView(body);
        TextView info = new TextView(this);
        info.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        KycApplication a = store.load();
        info.setText(a == null || !a.kycConsent ? getString(R.string.settings_none)
                : getString(R.string.settings_consent, a.consentTimestamp, a.noticeVersion,
                        getString(a.marketingConsent ? R.string.yes : R.string.no)));
        if (store.retainedSummary() != null) info.append("\n\n" + getString(R.string.settings_retained, store.retainedSummary()));
        body.addView(info);
        MaterialButton withdraw = new MaterialButton(this);
        withdraw.setText(R.string.settings_withdraw);
        withdraw.setAllCaps(false);
        withdraw.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.settings_withdraw_confirm)
                .setPositiveButton(R.string.settings_withdraw, (d, w) -> {
                    int n = store.withdrawAndDeleteAll();
                    info.setText(getString(R.string.settings_deleted, n));
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
        body.addView(withdraw);
        setContentView(root);
    }
}
