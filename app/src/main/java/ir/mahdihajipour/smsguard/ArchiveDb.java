package ir.mahdihajipour.smsguard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Local archive of every message the app blocked. Nothing leaves the phone. */
public final class ArchiveDb extends SQLiteOpenHelper {

    static final class Entry {
        long id;
        long ts;
        String sender;
        String body;
        String reason;
    }

    private static ArchiveDb instance;

    static synchronized ArchiveDb get(Context c) {
        if (instance == null) instance = new ArchiveDb(c.getApplicationContext());
        return instance;
    }

    private ArchiveDb(Context c) {
        super(c, "archive.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE blocked (_id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, "
                + "sender TEXT, body TEXT, norm TEXT, reason TEXT)");
        db.execSQL("CREATE INDEX blocked_ts ON blocked(ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // version 1 only
    }

    /** Stores a blocked message unless the same text was already stored in the last 10 minutes. */
    synchronized boolean insertIfNew(String sender, String body, String reason, long ts) {
        String norm = Rules.normText(body);
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor c = db.rawQuery("SELECT _id, norm FROM blocked WHERE ts > ? ORDER BY ts DESC LIMIT 50",
                new String[]{String.valueOf(ts - 10 * 60 * 1000L)})) {
            while (c.moveToNext()) {
                if (Recent.sameText(norm, c.getString(1))) return false;
            }
        }
        ContentValues v = new ContentValues();
        v.put("ts", ts);
        v.put("sender", sender == null ? "" : sender);
        v.put("body", body == null ? "" : body);
        v.put("norm", norm);
        v.put("reason", reason);
        db.insert("blocked", null, v);
        return true;
    }

    List<Entry> list(int limit) {
        List<Entry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id, ts, sender, body, reason FROM blocked ORDER BY ts DESC LIMIT " + limit, null)) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.id = c.getLong(0);
                e.ts = c.getLong(1);
                e.sender = c.getString(2);
                e.body = c.getString(3);
                e.reason = c.getString(4);
                out.add(e);
            }
        }
        return out;
    }

    void delete(long id) {
        getWritableDatabase().delete("blocked", "_id = ?", new String[]{String.valueOf(id)});
    }

    void clear() {
        getWritableDatabase().delete("blocked", null, null);
    }

    int countSince(long ts) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM blocked WHERE ts >= ?",
                new String[]{String.valueOf(ts)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    int countToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return countSince(cal.getTimeInMillis());
    }

    int countLastDays(int days) {
        return countSince(System.currentTimeMillis() - days * 24L * 60 * 60 * 1000);
    }

    /** "sender  ×count" for the senders blocked most often. */
    List<String> topSenders(int n) {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT sender, COUNT(*) AS k FROM blocked GROUP BY sender ORDER BY k DESC LIMIT " + n, null)) {
            while (c.moveToNext()) {
                out.add(c.getString(0) + "  ×" + Util.fa(c.getInt(1)));
            }
        }
        return out;
    }
}
