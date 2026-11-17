package ir.mahdihajipour.smsguard;

import android.app.Notification;
import android.app.Person;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Watches notifications from SMS apps and silently removes the ones that match the block rules.
 * The SMS itself stays in the messaging app; only the alert (sound, banner, badge) goes away.
 */
public class GuardListener extends NotificationListenerService {

    private static final Set<String> SMS_APPS = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging",   // Google Messages
            "com.samsung.android.messaging",       // Samsung Messages
            "com.android.mms",                     // AOSP / Xiaomi / Huawei / many others
            "com.android.messaging",
            "com.oneplus.mms",
            "com.motorola.messaging",
            "com.sonyericsson.conversations",
            "com.textra",
            "com.moez.QKSMS",
            "org.fossify.messages",
            "com.simplemobiletools.smsmessenger"
    ));

    private static volatile GuardListener instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static void requestSweep() {
        final GuardListener g = instance;
        if (g != null) {
            g.handler.post(g::sweep);
            g.handler.postDelayed(g::sweep, 1500);   // the SMS app may post its alert a bit later
        }
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        sweep();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn, true);
    }

    private void sweep() {
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all == null) return;
            for (StatusBarNotification s : all) handle(s, false);
        } catch (RuntimeException ignored) {
            // listener not bound yet
        }
    }

    private void recheck(String key) {
        try {
            StatusBarNotification[] a = getActiveNotifications(new String[]{key});
            if (a == null) return;
            for (StatusBarNotification s : a) handle(s, false);
        } catch (RuntimeException ignored) {
        }
    }

    private boolean isSmsApp(String pkg) {
        if (SMS_APPS.contains(pkg)) return true;
        String def = Telephony.Sms.getDefaultSmsPackage(this);
        return pkg.equals(def);
    }

    private void handle(StatusBarNotification sbn, boolean firstTime) {
        if (sbn == null || !Rules.enabled(this)) return;
        if (!isSmsApp(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || sbn.isOngoing()) return;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        String[] sm = extract(n);
        if (sm == null) return;
        String sender = sm[0];
        String text = sm[1];

        boolean spam;
        String reason;
        Recent.Decision d = Recent.find(Rules.normText(text));
        if (d != null) {
            // The SMS receiver already judged this message with the real sender number.
            spam = d.spam;
            reason = d.reason;
            if (d.sender != null && !d.sender.isEmpty()) sender = d.sender;
        } else {
            Rules.Result r = Rules.classify(this, sender, text);
            spam = r.spam;
            reason = r.reason;
            if (!spam && firstTime) {
                // The SMS broadcast may arrive after the notification; look again shortly.
                final String key = sbn.getKey();
                handler.postDelayed(() -> recheck(key), 2000);
            }
        }

        if (spam) {
            cancelNotification(sbn.getKey());
            ArchiveDb.get(this).insertIfNew(sender, text, reason, System.currentTimeMillis());
            cleanupSummary(sbn.getPackageName(), n.getGroup());
        }
    }

    /** Returns {sender, text} of the newest message in the notification, or null. */
    private static String[] extract(Notification n) {
        Bundle ex = n.extras;
        if (ex == null) return null;
        String sender = null;
        String text = null;

        Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null && msgs.length > 0 && msgs[msgs.length - 1] instanceof Bundle) {
            Bundle last = (Bundle) msgs[msgs.length - 1];
            CharSequence t = last.getCharSequence("text");
            if (t != null) text = t.toString();
            CharSequence s = last.getCharSequence("sender");
            if (s != null) sender = s.toString();
            if (sender == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Parcelable p = last.getParcelable("sender_person");
                if (p instanceof Person && ((Person) p).getName() != null) {
                    sender = ((Person) p).getName().toString();
                }
            }
            if (sender == null) {
                CharSequence ct = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
                if (ct != null) sender = ct.toString();
            }
        }
        if (text == null) {
            CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
            CharSequence t = big != null ? big : ex.getCharSequence(Notification.EXTRA_TEXT);
            if (t != null) text = t.toString();
        }
        if (sender == null) {
            CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
            if (title != null) sender = title.toString();
        }
        if (text == null || text.trim().isEmpty()) return null;
        return new String[]{sender == null ? "" : sender, text};
    }

    /** If every message of a group was removed, remove the empty group header too. */
    private void cleanupSummary(final String pkg, final String group) {
        if (group == null) return;
        handler.postDelayed(() -> {
            try {
                StatusBarNotification[] all = getActiveNotifications();
                if (all == null) return;
                boolean childLeft = false;
                for (StatusBarNotification s : all) {
                    if (!pkg.equals(s.getPackageName())) continue;
                    Notification n = s.getNotification();
                    if (n == null || !group.equals(n.getGroup())) continue;
                    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) == 0) childLeft = true;
                }
                if (childLeft) return;
                for (StatusBarNotification s : all) {
                    Notification n = s.getNotification();
                    if (pkg.equals(s.getPackageName()) && n != null && group.equals(n.getGroup())
                            && (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
                        cancelNotification(s.getKey());
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }, 400);
    }
}
