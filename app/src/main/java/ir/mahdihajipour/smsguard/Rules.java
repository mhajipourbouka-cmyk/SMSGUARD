package ir.mahdihajipour.smsguard;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** All user-editable rules and the classifier that applies them. */
public final class Rules {

    private Rules() {}

    static final String PREFS = "rules";
    static final String K_ENABLED = "enabled";
    static final String K_PREFIXES = "prefixes";
    static final String K_KEYWORDS = "keywords";
    static final String K_WL_NUMBERS = "wl_numbers";
    static final String K_WL_KEYWORDS = "wl_keywords";
    static final String K_ALLOW_CONTACTS = "allow_contacts";

    static final String DEF_PREFIXES = "1000\n2000\n3000\n5000\n9000";
    static final String DEF_KEYWORDS = "لغو11\nتخفیف\nکد تخفیف\nجشنواره\nقرعه کشی\nفروش ویژه\nحراج\nجایزه\nهدیه";
    static final String DEF_WL_NUMBERS = "";
    static final String DEF_WL_KEYWORDS = "رمز\nکد تایید\nکد تأیید\nکد ورود\nرمز پویا\nبرداشت\nواریز\nمانده\nOTP\ncode";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(K_ENABLED, true);
    }

    static boolean allowContacts(Context c) {
        return prefs(c).getBoolean(K_ALLOW_CONTACTS, true);
    }

    static String raw(Context c, String key) {
        return prefs(c).getString(key, defaultFor(key));
    }

    static String defaultFor(String key) {
        switch (key) {
            case K_PREFIXES: return DEF_PREFIXES;
            case K_KEYWORDS: return DEF_KEYWORDS;
            case K_WL_NUMBERS: return DEF_WL_NUMBERS;
            case K_WL_KEYWORDS: return DEF_WL_KEYWORDS;
            default: return "";
        }
    }

    static List<String> list(Context c, String key) {
        List<String> out = new ArrayList<>();
        String v = raw(c, key);
        if (v == null) return out;
        for (String line : v.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    static void addLine(Context c, String key, String value) {
        String cur = raw(c, key);
        for (String line : cur.split("\n")) {
            if (line.trim().equals(value.trim())) return;
        }
        String next = cur.trim().isEmpty() ? value.trim() : cur.trim() + "\n" + value.trim();
        prefs(c).edit().putString(key, next).apply();
    }

    // ---------- normalisation ----------

    /** Persian (۰-۹) and Arabic-Indic (٠-٩) digits to ASCII. */
    static String latinDigits(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '۰' && ch <= '۹') ch = (char) ('0' + (ch - '۰'));
            else if (ch >= '٠' && ch <= '٩') ch = (char) ('0' + (ch - '٠'));
            b.append(ch);
        }
        return b.toString();
    }

    /** Digits only, without +98 / 0098 / 98 country code and without a leading 0. */
    static String normNumber(String s) {
        String latin = latinDigits(s).trim();
        String d = latin.replaceAll("[^0-9]", "");
        boolean plus = latin.startsWith("+");
        if (d.startsWith("0098")) d = d.substring(4);
        else if (d.startsWith("98") && (plus || d.length() >= 12)) d = d.substring(2);
        if (d.startsWith("0")) d = d.substring(1);
        return d;
    }

    /** Text form used for keyword matching: unified letters/digits, no spaces or ZWNJ, lower case. */
    static String normText(String s) {
        if (s == null) return "";
        String t = latinDigits(s)
                .replace('ي', 'ی')   // Arabic yeh -> Persian yeh
                .replace('ى', 'ی')   // alef maksura -> Persian yeh
                .replace('ك', 'ک')   // Arabic kaf -> Persian kaf
                .replace("أ", "ا")   // أ -> ا
                .replace("إ", "ا")   // إ -> ا
                .replace("‌", "")
                .replace("‏", "")
                .replace("‎", "");
        t = t.replaceAll("\\s+", "");
        return t.toLowerCase(Locale.ROOT);
    }

    // ---------- classification ----------

    static final class Result {
        final boolean spam;
        final String reason;

        Result(boolean spam, String reason) {
            this.spam = spam;
            this.reason = reason;
        }
    }

    static Result classify(Context c, String sender, String body) {
        if (!enabled(c)) return new Result(false, "فیلتر خاموشه");

        String num = normNumber(sender);
        String text = normText(body);
        String senderText = normText(sender);

        for (String w : list(c, K_WL_NUMBERS)) {
            String wn = normNumber(w);
            if (!wn.isEmpty() && wn.equals(num)) return new Result(false, "لیست سفید: " + w);
            String wt = normText(w);
            if (wn.isEmpty() && !wt.isEmpty() && wt.equals(senderText)) return new Result(false, "لیست سفید: " + w);
        }
        for (String k : list(c, K_WL_KEYWORDS)) {
            String kn = normText(k);
            if (!kn.isEmpty() && text.contains(kn)) return new Result(false, "کلمه‌ی مجاز: " + k);
        }
        if (allowContacts(c) && isContact(c, sender)) return new Result(false, "از مخاطبین");

        // A normal Iranian mobile (09xx xxx xxxx -> 9xxxxxxxxx) is never treated as an ad line,
        // otherwise prefix 9000 would also hit real 0900 numbers.
        boolean mobile = num.length() == 10 && num.startsWith("9");
        for (String p : list(c, K_PREFIXES)) {
            String pn = normNumber(p);
            if (!mobile && !pn.isEmpty() && num.startsWith(pn) && num.length() > pn.length()) {
                return new Result(true, "سرشماره " + p);
            }
        }
        for (String k : list(c, K_KEYWORDS)) {
            String kn = normText(k);
            if (kn.isEmpty()) continue;
            if (text.contains(kn) || senderText.contains(kn)) return new Result(true, "کلمه‌ی «" + k + "»");
        }
        return new Result(false, "با هیچ قانونی جور نبود");
    }

    // ---------- contacts ----------

    static boolean hasContactsPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isContact(Context c, String sender) {
        if (sender == null || sender.trim().isEmpty() || !hasContactsPermission(c)) return false;
        String digits = normNumber(sender);
        try {
            if (digits.length() >= 7) {
                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(latinDigits(sender)));
                try (Cursor cur = c.getContentResolver().query(uri,
                        new String[]{ContactsContract.PhoneLookup._ID}, null, null, null)) {
                    if (cur != null && cur.getCount() > 0) return true;
                }
            }
            // The notification title is often the contact's saved name rather than a number.
            if (digits.isEmpty()) {
                Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI,
                        Uri.encode(sender.trim()));
                try (Cursor cur = c.getContentResolver().query(uri,
                        new String[]{ContactsContract.Contacts.DISPLAY_NAME}, null, null, null)) {
                    if (cur != null) {
                        String want = normText(sender);
                        while (cur.moveToNext()) {
                            if (want.equals(normText(cur.getString(0)))) return true;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A broken contacts provider must never crash the filter.
        }
        return false;
    }
}
