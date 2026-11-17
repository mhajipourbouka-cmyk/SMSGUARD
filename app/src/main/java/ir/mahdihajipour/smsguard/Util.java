package ir.mahdihajipour.smsguard;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Util {

    private Util() {}

    static String fa(long n) {
        return toFaDigits(String.valueOf(n));
    }

    static String toFaDigits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') ch = (char) ('۰' + (ch - '0'));
            b.append(ch);
        }
        return b.toString();
    }

    /** Solar Hijri date, e.g. ۱۴۰۵/۰۸/۲۶  ۱۲:۳۰ (falls back to Gregorian if ICU is unavailable). */
    static String time(long ts) {
        try {
            android.icu.text.SimpleDateFormat f = new android.icu.text.SimpleDateFormat(
                    "yyyy/MM/dd  HH:mm", new android.icu.util.ULocale("fa_IR@calendar=persian"));
            return toFaDigits(f.format(new Date(ts)));
        } catch (RuntimeException e) {
            return toFaDigits(new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US).format(new Date(ts)));
        }
    }

    static boolean listenerEnabled(Context c) {
        String flat = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        ComponentName me = new ComponentName(c, GuardListener.class);
        for (String part : flat.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (me.equals(cn)) return true;
        }
        return false;
    }
}
