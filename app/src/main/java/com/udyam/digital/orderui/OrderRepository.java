package com.udyam.digital.orderui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.udyam.digital.order.Order;
import com.udyam.digital.order.OrderJson;

import java.io.IOException;
import java.security.GeneralSecurityException;

/** One active order per device, saved as JSON in EncryptedSharedPreferences (Android Keystore). */
public final class OrderRepository {

    private static final String PREFS = "order_secure";
    private static final String KEY_ORDER = "order";
    private static final String KEY_OFFSET = "clock_offset_days";
    private static final String KEY_FAIL = "debug_fail_payment";
    private static final String KEY_NOTIF_ASKED = "notif_asked";

    private final SharedPreferences prefs;

    public OrderRepository(Context ctx) {
        try {
            MasterKey key = new MasterKey.Builder(ctx.getApplicationContext()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            prefs = EncryptedSharedPreferences.create(ctx.getApplicationContext(), PREFS, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Secure storage unavailable", e);
        }
    }

    public Order load() {
        String s = prefs.getString(KEY_ORDER, null);
        if (s == null) return null;
        try {
            return OrderJson.read(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void save(Order o) { prefs.edit().putString(KEY_ORDER, OrderJson.write(o)).apply(); }

    public void clear() { prefs.edit().remove(KEY_ORDER).apply(); }

    public boolean hasOrder() { return prefs.contains(KEY_ORDER); }

    public int clockOffset() { return prefs.getInt(KEY_OFFSET, 0); }

    public void setClockOffset(int days) { prefs.edit().putInt(KEY_OFFSET, days).apply(); }

    public boolean failNextPayment() { return prefs.getBoolean(KEY_FAIL, false); }

    public void setFailNextPayment(boolean f) { prefs.edit().putBoolean(KEY_FAIL, f).apply(); }

    public boolean notificationsAsked() { return prefs.getBoolean(KEY_NOTIF_ASKED, false); }

    public void setNotificationsAsked() { prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply(); }
}
