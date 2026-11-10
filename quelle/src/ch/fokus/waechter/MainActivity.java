package ch.fokus.waechter;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private TextView status, counter, diagText;
    private Button enableBtn;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(WaechterService.PREFS, MODE_PRIVATE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        text("Fokus Wächter", 26, true);
        status = text("", 16, false);
        enableBtn = button("Bedienungshilfe einschalten", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        text("Falls der Schalter dort grau ist: Einstellungen → Apps → Fokus Wächter → oben rechts ⋮ → «Eingeschränkte Einstellungen zulassen». Danach nochmals versuchen.", 13, false);

        heading("Was gesperrt wird");
        toggle("YouTube Shorts", "yt_shorts", true);
        toggle("Snapchat Spotlight", "sc_spotlight", true);
        toggle("Snapchat Stories-Seite (mit Discover)", "sc_stories", false);
        text("Chats bleiben immer offen. Stories von Freunden siehst du in der Chat-Liste: auf das Profilbild mit Ring tippen.", 13, false);
        counter = text("", 14, false);

        heading("Diagnose");
        text("Falls etwas nicht gesperrt wird: Diagnose starten, dann in YouTube oder Snapchat die Seite öffnen, die gesperrt sein sollte. Komm zurück und kopiere die Liste. Sie enthält nur technische Namen von Bildschirmteilen, keine Nachrichten.", 13, false);
        button("Diagnose starten (60 Sekunden)", v -> {
            prefs.edit().putLong("diag_until", System.currentTimeMillis() + 60_000).remove("diag_text").apply();
            Toast.makeText(this, "Diagnose läuft. Öffne jetzt die App.", Toast.LENGTH_LONG).show();
            refresh();
        });
        button("Liste kopieren", v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Fokus Diagnose", prefs.getString("diag_text", "")));
            Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show();
        });
        diagText = text("", 11, false);
        diagText.setTypeface(Typeface.MONOSPACE);
        diagText.setTextIsSelectable(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean on = serviceEnabled();
        status.setText(on ? "Aktiv. Shorts und Spotlight werden sofort wieder geschlossen."
                : "Noch nicht aktiv. Schalte «Fokus Wächter» in den Bedienungshilfen ein.");
        enableBtn.setText(on ? "Bedienungshilfen öffnen" : "Bedienungshilfe einschalten");
        counter.setText("Bisher gesperrt: " + prefs.getInt("blocked", 0) + " Mal");
        String d = prefs.getString("diag_text", "");
        boolean running = System.currentTimeMillis() < prefs.getLong("diag_until", 0);
        diagText.setText(d.isEmpty() ? (running ? "Diagnose läuft …" : "") : d);
    }

    private boolean serviceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, WaechterService.class);
        for (String s : enabled.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) return true;
        }
        return false;
    }

    /* ---------- kleine Helfer für die Oberfläche ---------- */

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        return lp;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(t, spaced());
        return t;
    }

    private void heading(String s) {
        TextView t = text(s, 18, true);
        ((LinearLayout.LayoutParams) t.getLayoutParams()).topMargin = dp(28);
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        root.addView(b, spaced());
        return b;
    }

    private void toggle(String label, String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(16);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setMinHeight(dp(48));
        sw.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(key, on).apply());
        root.addView(sw, spaced());
    }
}
