package ir.mahdihajipour.smsguard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Short-lived memory of decisions made by {@link SmsReceiver} (which sees the real sender number),
 * so {@link GuardListener} can match the notification for the same message.
 */
final class Recent {

    private Recent() {}

    static final class Decision {
        final String norm;
        final String sender;
        final boolean spam;
        final String reason;
        final long ts;

        Decision(String norm, String sender, boolean spam, String reason, long ts) {
            this.norm = norm;
            this.sender = sender;
            this.spam = spam;
            this.reason = reason;
            this.ts = ts;
        }
    }

    private static final long TTL = 5 * 60 * 1000L;
    private static final List<Decision> ITEMS = new ArrayList<>();

    static synchronized void add(String norm, String sender, boolean spam, String reason) {
        prune();
        ITEMS.add(new Decision(norm, sender, spam, reason, System.currentTimeMillis()));
    }

    static synchronized Decision find(String norm) {
        prune();
        for (int i = ITEMS.size() - 1; i >= 0; i--) {
            Decision d = ITEMS.get(i);
            if (sameText(norm, d.norm)) return d;
        }
        return null;
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        Iterator<Decision> it = ITEMS.iterator();
        while (it.hasNext()) {
            if (now - it.next().ts > TTL) it.remove();
        }
    }

    /** Notification text can be shortened by the messaging app, so compare loosely. */
    static boolean sameText(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (a.length() < 8 || b.length() < 8) return false;
        if (a.contains(b) || b.contains(a)) return true;
        int n = Math.min(30, Math.min(a.length(), b.length()));
        return n >= 20 && a.regionMatches(0, b, 0, n);
    }
}
