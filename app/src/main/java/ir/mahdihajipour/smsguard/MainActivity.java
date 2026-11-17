package ir.mahdihajipour.smsguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private static final int C_BG = Color.parseColor("#F4F6FB");
    private static final int C_CARD = Color.WHITE;
    private static final int C_TEXT = Color.parseColor("#14182B");
    private static final int C_MUTED = Color.parseColor("#5B6785");
    private static final int C_ACCENT = Color.parseColor("#6D4AE8");
    private static final int C_OK = Color.parseColor("#14915A");
    private static final int C_BAD = Color.parseColor("#C8372D");

    private static final int REQ_SMS = 1;
    private static final int REQ_CONTACTS = 2;

    private FrameLayout content;
    private final Button[] tabs = new Button[3];
    private int current = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(C_BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(4));
        String[] names = {"وضعیت", "قوانین", "آرشیو"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setOnClickListener(v -> show(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            bar.addView(b, lp);
            tabs[i] = b;
        }
        root.addView(bar);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        if (state != null) current = state.getInt("tab", 0);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", current);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show(current);
    }

    private void show(int i) {
        current = i;
        for (int k = 0; k < tabs.length; k++) {
            boolean on = k == i;
            tabs[k].setBackground(round(on ? C_ACCENT : Color.parseColor("#E3E6F0"), 12));
            tabs[k].setTextColor(on ? Color.WHITE : C_TEXT);
        }
        content.removeAllViews();
        View v = i == 0 ? statusView() : i == 1 ? rulesView() : archiveView();
        content.addView(v);
    }

    // ------------------------------------------------------------------ status tab

    private View statusView() {
        LinearLayout col = column();

        LinearLayout head = card(col);
        TextView title = text("پیامک‌گیر", 22, C_TEXT, true);
        head.addView(title);
        head.addView(text("پیامک‌های تبلیغاتی رو بی‌صدا می‌کنه و یه نسخه توی آرشیو نگه می‌داره.", 14, C_MUTED, false));
        Switch sw = new Switch(this);
        sw.setText("فیلتر روشن باشه");
        sw.setTextSize(16);
        sw.setTextColor(C_TEXT);
        sw.setChecked(Rules.enabled(this));
        sw.setOnCheckedChangeListener((b, on) -> Rules.prefs(this).edit().putBoolean(Rules.K_ENABLED, on).apply());
        sw.setPadding(0, dp(12), 0, 0);
        head.addView(sw);

        // Permissions
        LinearLayout perms = card(col);
        perms.addView(text("دسترسی‌ها", 17, C_TEXT, true));

        boolean listener = Util.listenerEnabled(this);
        perms.addView(statusLine(listener, "دسترسی به اعلان‌ها (لازم)",
                "برای بی‌صدا کردن اعلان پیامک‌های تبلیغاتی"));
        if (!listener) {
            perms.addView(button("فعال کردن دسترسی اعلان", v ->
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
            perms.addView(text("اگه گزینه خاکستری بود: تنظیمات ← برنامه‌ها ← پیامک‌گیر ← منوی ⋮ بالا ← «Allow restricted settings» رو بزن و دوباره امتحان کن.",
                    13, C_MUTED, false));
        }

        boolean sms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        perms.addView(statusLine(sms, "دریافت پیامک (پیشنهادی)",
                "شماره‌ی واقعی فرستنده رو می‌بینه، حتی وقتی اسم مخاطب نشون داده میشه"));
        if (!sms) {
            perms.addView(button("اجازه‌ی دریافت پیامک", v ->
                    requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, REQ_SMS)));
        }

        boolean contacts = Rules.hasContactsPermission(this);
        perms.addView(statusLine(contacts, "مخاطبین (برای لیست سفید)",
                "پیامک کسایی که توی مخاطبینت هستن هیچ‌وقت بلاک نمیشه"));
        if (!contacts) {
            perms.addView(button("اجازه‌ی خوندن مخاطبین", v ->
                    requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS)));
        }

        // Stats
        ArchiveDb db = ArchiveDb.get(this);
        LinearLayout stats = card(col);
        stats.addView(text("آمار", 17, C_TEXT, true));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(statBox("امروز", db.countToday()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(statBox("۷ روز", db.countLastDays(7)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(statBox("کل", db.countSince(0)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(row);
        List<String> top = db.topSenders(5);
        if (!top.isEmpty()) {
            stats.addView(text("پرتکرارترین فرستنده‌ها:", 14, C_MUTED, false));
            for (String s : top) stats.addView(text("• " + s, 14, C_TEXT, false));
        }

        // Rule tester
        LinearLayout test = card(col);
        test.addView(text("تست قانون‌ها", 17, C_TEXT, true));
        final EditText tSender = input("شماره یا اسم فرستنده، مثلاً 30001234", false);
        final EditText tBody = input("متن پیامک", true);
        final TextView tOut = text("", 15, C_TEXT, true);
        test.addView(tSender);
        test.addView(tBody);
        test.addView(button("تست کن", v -> {
            Rules.Result r = Rules.classify(this, tSender.getText().toString(), tBody.getText().toString());
            tOut.setText((r.spam ? "⛔ بلاک میشه — " : "✅ رد میشه — ") + r.reason);
            tOut.setTextColor(r.spam ? C_BAD : C_OK);
        }));
        test.addView(tOut);

        LinearLayout note = card(col);
        note.addView(text("نکته", 15, C_TEXT, true));
        note.addView(text("اندروید فقط به برنامه‌ی پیش‌فرض پیامک اجازه میده پیام رو پاک کنه. پیامک‌گیر اعلان پیام تبلیغاتی رو حذف می‌کنه (بدون صدا، بنر و عدد روی آیکون)، ولی خود پیام توی برنامه‌ی پیامک می‌مونه.",
                13, C_MUTED, false));

        return scroll(col);
    }

    private View statusLine(boolean ok, String title, String sub) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, dp(10), 0, dp(2));
        l.addView(text((ok ? "✅ " : "❌ ") + title, 15, ok ? C_OK : C_BAD, true));
        l.addView(text(sub, 13, C_MUTED, false));
        return l;
    }

    private View statBox(String label, int n) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(10), 0, dp(10));
        TextView num = text(Util.fa(n), 26, C_ACCENT, true);
        num.setGravity(Gravity.CENTER);
        TextView lab = text(label, 13, C_MUTED, false);
        lab.setGravity(Gravity.CENTER);
        b.addView(num);
        b.addView(lab);
        return b;
    }

    // ------------------------------------------------------------------ rules tab

    private View rulesView() {
        LinearLayout col = column();

        final EditText prefixes = ruleCard(col, "سرشماره‌های تبلیغاتی",
                "هر خط یه سرشماره. پیامکی که شماره‌ش با این‌ها شروع بشه بلاک میشه. شماره‌ی موبایل معمولی (۰۹...) هیچ‌وقت با این قانون بلاک نمیشه.",
                Rules.K_PREFIXES, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final EditText keywords = ruleCard(col, "کلمه‌های تبلیغاتی",
                "هر خط یه کلمه یا عبارت. فاصله، نیم‌فاصله، ی/ي و عدد فارسی/انگلیسی فرقی نمی‌کنه.",
                Rules.K_KEYWORDS, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final EditText wlNumbers = ruleCard(col, "لیست سفید: شماره‌ها",
                "هر خط یه شماره (مثلاً شماره‌ی پیامک بانکت). این شماره‌ها هیچ‌وقت بلاک نمیشن.",
                Rules.K_WL_NUMBERS, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final EditText wlKeywords = ruleCard(col, "لیست سفید: کلمه‌ها",
                "پیامکی که یکی از این کلمه‌ها رو داشته باشه بلاک نمیشه؛ برای رمز پویا و پیامک بانک.",
                Rules.K_WL_KEYWORDS, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        LinearLayout opt = card(col);
        Switch sw = new Switch(this);
        sw.setText("پیامک مخاطبین هیچ‌وقت بلاک نشه");
        sw.setTextSize(15);
        sw.setTextColor(C_TEXT);
        sw.setChecked(Rules.allowContacts(this));
        sw.setOnCheckedChangeListener((b, on) -> {
            Rules.prefs(this).edit().putBoolean(Rules.K_ALLOW_CONTACTS, on).apply();
            if (on && !Rules.hasContactsPermission(this)) {
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
            }
        });
        opt.addView(sw);

        LinearLayout actions = card(col);
        actions.addView(button("ذخیره", v -> {
            Rules.prefs(this).edit()
                    .putString(Rules.K_PREFIXES, prefixes.getText().toString())
                    .putString(Rules.K_KEYWORDS, keywords.getText().toString())
                    .putString(Rules.K_WL_NUMBERS, wlNumbers.getText().toString())
                    .putString(Rules.K_WL_KEYWORDS, wlKeywords.getText().toString())
                    .apply();
            Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show();
        }));
        Button reset = button("برگردوندن به پیش‌فرض", v -> new AlertDialog.Builder(this)
                .setMessage("همه‌ی لیست‌ها به حالت اول برگردن؟")
                .setPositiveButton("آره", (d, w) -> {
                    Rules.prefs(this).edit()
                            .remove(Rules.K_PREFIXES).remove(Rules.K_KEYWORDS)
                            .remove(Rules.K_WL_NUMBERS).remove(Rules.K_WL_KEYWORDS).apply();
                    show(1);
                })
                .setNegativeButton("نه", null)
                .show());
        reset.setBackground(round(Color.parseColor("#E3E6F0"), 12));
        reset.setTextColor(C_TEXT);
        actions.addView(reset);

        return scroll(col);
    }

    private EditText ruleCard(LinearLayout col, String title, String help, String key, int type) {
        LinearLayout c = card(col);
        c.addView(text(title, 16, C_TEXT, true));
        c.addView(text(help, 13, C_MUTED, false));
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setSingleLine(false);
        e.setMinLines(3);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        e.setText(Rules.raw(this, key));
        e.setTextSize(15);
        e.setBackground(round(Color.parseColor("#F1F3F9"), 10));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        c.addView(e, lp);
        return e;
    }

    // ------------------------------------------------------------------ archive tab

    private View archiveView() {
        final ArchiveDb db = ArchiveDb.get(this);
        final List<ArchiveDb.Entry> items = db.list(500);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(4), dp(12), dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = text(Util.fa(items.size()) + " پیام بلاک‌شده", 16, C_TEXT, true);
        head.addView(count, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!items.isEmpty()) {
            Button clear = button("پاک کردن همه", v -> new AlertDialog.Builder(this)
                    .setMessage("کل آرشیو پاک بشه؟ این کار برگشت نداره. (پیامک‌ها توی برنامه‌ی پیامک می‌مونن.)")
                    .setPositiveButton("پاک کن", (d, w) -> {
                        db.clear();
                        show(2);
                    })
                    .setNegativeButton("نه", null)
                    .show());
            clear.setBackground(round(Color.parseColor("#FBE3E1"), 12));
            clear.setTextColor(C_BAD);
            head.addView(clear, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        wrap.addView(head);

        if (items.isEmpty()) {
            TextView empty = text("هنوز چیزی بلاک نشده.", 15, C_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            wrap.addView(empty);
            return wrap;
        }

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int p) { return items.get(p); }
            @Override public long getItemId(int p) { return items.get(p).id; }

            @Override
            public View getView(int p, View convert, ViewGroup parent) {
                ArchiveDb.Entry e = items.get(p);
                LinearLayout c = new LinearLayout(MainActivity.this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                c.setPadding(dp(14), dp(12), dp(14), dp(12));
                c.setBackground(round(C_CARD, 14));
                c.addView(text(e.sender, 15, C_TEXT, true));
                TextView body = text(e.body, 14, C_TEXT, false);
                body.setMaxLines(3);
                body.setEllipsize(android.text.TextUtils.TruncateAt.END);
                c.addView(body);
                c.addView(text(Util.time(e.ts) + "  •  " + e.reason, 12, C_MUTED, false));
                FrameLayout f = new FrameLayout(MainActivity.this);
                f.setPadding(0, dp(5), 0, dp(5));
                f.addView(c);
                return f;
            }
        });
        list.setOnItemClickListener((parent, view, pos, id) -> openEntry(items.get(pos)));
        wrap.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return wrap;
    }

    private void openEntry(final ArchiveDb.Entry e) {
        String[] actions = {"کپی متن", "این فرستنده رو به لیست سفید اضافه کن", "حذف از آرشیو"};
        TextView msg = text(e.body + "\n\n" + Util.time(e.ts) + "\nدلیل: " + e.reason, 15, C_TEXT, false);
        msg.setPadding(dp(20), dp(12), dp(20), dp(4));
        msg.setTextIsSelectable(true);
        new AlertDialog.Builder(this)
                .setTitle(e.sender)
                .setView(msg)
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("sms", e.body));
                        Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        Rules.addLine(this, Rules.K_WL_NUMBERS, e.sender);
                        Toast.makeText(this, "به لیست سفید اضافه شد", Toast.LENGTH_SHORT).show();
                    } else {
                        ArchiveDb.get(this).delete(e.id);
                        show(2);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    // ------------------------------------------------------------------ helpers

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        show(current);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        col.setPadding(dp(12), dp(4), dp(12), dp(24));
        return col;
    }

    private ScrollView scroll(View v) {
        ScrollView s = new ScrollView(this);
        s.addView(v);
        return s;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(C_CARD, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        parent.addView(c, lp);
        return c;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        t.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(C_ACCENT, 12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private EditText input(String hint, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(2);
        }
        return e;
    }
}
