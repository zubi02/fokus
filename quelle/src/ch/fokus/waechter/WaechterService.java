package ch.fokus.waechter;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Beobachtet YouTube, Snapchat und Chrome. Sobald eine gesperrte Seite sichtbar ist,
 * wechselt der Dienst auf eine erlaubte Seite: in YouTube auf "Home", in Snapchat auf "Chat".
 * Die App selbst bleibt offen. Nur wenn das nicht geht, drueckt er "Zurueck".
 */
public class WaechterService extends AccessibilityService {

    static final String PREFS = "fokus";
    static final String TAG = "Fokus";
    static final String YOUTUBE = "com.google.android.youtube";
    static final String SNAPCHAT = "com.snapchat.android";
    static final String CHROME = "com.android.chrome";

    private static final long SCAN_DELAY_MS = 150;
    private static final long ACTION_PAUSE_MS = 900;
    private static final long BACK_PAUSE_MS = 2500;
    private static final int MAX_NODES = 4000;
    private static final int MAX_DIAG_LINES = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scanTask = this::scan;
    private boolean scanPending;
    private long lastAction, lastBack;
    private final LinkedHashSet<String> diag = new LinkedHashSet<>();

    @Override
    protected void onServiceConnected() {
        Log.i(TAG, "Dienst verbunden");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        if (!scanPending) {
            scanPending = true;
            handler.postDelayed(scanTask, SCAN_DELAY_MS);
        }
    }

    @Override
    public void onInterrupt() { }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void scan() {
        scanPending = false;
        try {
            scanNow();
        } catch (RuntimeException e) {
            Log.w(TAG, "Fehler beim Lesen des Bildschirms", e);
        }
    }

    private void scanNow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        String pkg = root.getPackageName().toString();
        if (!pkg.equals(YOUTUBE) && !pkg.equals(SNAPCHAT) && !pkg.equals(CHROME)) return;

        SharedPreferences p = prefs();
        boolean diagOn = System.currentTimeMillis() < p.getLong("diag_until", 0);
        if (!diagOn && !diag.isEmpty()) diag.clear();

        String reason = null;

        // Schnelle, gezielte Pruefung ueber bekannte Namen.
        if (pkg.equals(SNAPCHAT)) {
            if (p.getBoolean("sc_spotlight", true) && visible(root, SNAPCHAT + ":id/spotlight_container")) reason = "Spotlight";
            else if (p.getBoolean("sc_stories", false) && visible(root, SNAPCHAT + ":id/df_large_story")) reason = "Stories-Seite";
        }

        // Gruendliche Pruefung: ganzen Bildschirm durchgehen.
        if (reason == null || diagOn) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            Rect b = new Rect();
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            int seen = 0;
            while (!queue.isEmpty() && seen < MAX_NODES) {
                AccessibilityNodeInfo n = queue.poll();
                seen++;
                if (n == null) continue;
                for (int i = 0; i < n.getChildCount(); i++) {
                    AccessibilityNodeInfo c = n.getChild(i);
                    if (c != null) queue.add(c);
                }
                if (!n.isVisibleToUser()) continue;
                String id = shortId(n);
                n.getBoundsInScreen(b);
                boolean big = b.width() >= dm.widthPixels * 0.9f && b.height() >= dm.heightPixels * 0.6f;
                if (diagOn && !id.isEmpty() && diag.size() < MAX_DIAG_LINES) {
                    diag.add(shortPkg(pkg) + " " + id + (big ? " [gross]" : "") + (n.isSelected() ? " [aktiv " + shortLabel(n) + "]" : ""));
                }
                if (reason == null) reason = check(pkg, n, id, big, p);
                if (reason != null && !diagOn) break;
            }
            if (diagOn) p.edit().putString("diag_text", String.join("\n", diag)).apply();
        }

        if (reason != null) leave(pkg, root, reason);
    }

    /** Liefert den Grund, falls dieser Knoten zu einer gesperrten Seite gehoert, sonst null. */
    private String check(String pkg, AccessibilityNodeInfo n, String id, boolean big, SharedPreferences p) {
        if (pkg.equals(YOUTUBE) && p.getBoolean("yt_shorts", true)) {
            if (id.equals("reel_recycler") || id.equals("reel_player_page_container")
                    || id.equals("reel_watch_fragment_root") || id.equals("reel_watch_player")
                    || id.equals("shorts_player_fragment")) return "Shorts";
            // Reiter "Shorts" in der unteren Leiste ist aktiv.
            if (n.isSelected() && shortLabel(n).equalsIgnoreCase("Shorts")) return "Shorts";
        }
        if (pkg.equals(SNAPCHAT) && p.getBoolean("sc_spotlight", true)) {
            if (id.equals("spotlight_container") || (big && id.contains("spotlight"))) return "Spotlight";
        }
        if (pkg.equals(CHROME) && p.getBoolean("yt_shorts", true) && id.equals("url_bar")) {
            CharSequence t = n.getText();
            if (t != null && t.toString().toLowerCase(Locale.ROOT).contains("youtube.com/shorts")) return "Shorts";
        }
        return null;
    }

    /** Verlaesst die gesperrte Seite, ohne die App zu schliessen. */
    private void leave(String pkg, AccessibilityNodeInfo root, String what) {
        long now = SystemClock.uptimeMillis();
        if (now - lastAction < ACTION_PAUSE_MS) return;

        boolean done = false;
        String how = "";
        if (pkg.equals(YOUTUBE)) {
            done = click(findByLabel(root, "Home", "Startseite"));
            how = "Home";
        } else if (pkg.equals(SNAPCHAT)) {
            done = click(first(root, SNAPCHAT + ":id/ngs_chat_icon_container"));
            how = "Chat";
            if (!done) {
                done = click(first(root, SNAPCHAT + ":id/ngs_camera_icon_container"));
                how = "Kamera";
            }
        }
        if (!done) {
            // Kein Ausweg gefunden: einmal "Zurueck", aber nicht mehrmals kurz hintereinander,
            // sonst wuerde die App geschlossen.
            if (now - lastBack < BACK_PAUSE_MS) return;
            lastBack = now;
            done = performGlobalAction(GLOBAL_ACTION_BACK);
            how = "Zurueck";
        }
        lastAction = now;
        Log.i(TAG, what + " erkannt in " + shortPkg(pkg) + " -> " + how + (done ? "" : " (fehlgeschlagen)"));
        SharedPreferences p = prefs();
        p.edit().putInt("blocked", p.getInt("blocked", 0) + 1).apply();
        Toast.makeText(this, what + " ist gesperrt", Toast.LENGTH_SHORT).show();
    }

    private static boolean visible(AccessibilityNodeInfo root, String fullId) {
        return first(root, fullId) != null;
    }

    private static AccessibilityNodeInfo first(AccessibilityNodeInfo root, String fullId) {
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(fullId);
        if (l == null) return null;
        for (AccessibilityNodeInfo n : l) if (n != null && n.isVisibleToUser()) return n;
        return null;
    }

    /** Sucht einen sichtbaren, antippbaren Knopf mit genau dieser Beschriftung. */
    private static AccessibilityNodeInfo findByLabel(AccessibilityNodeInfo root, String... labels) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int seen = 0;
        while (!queue.isEmpty() && seen < MAX_NODES) {
            AccessibilityNodeInfo n = queue.poll();
            seen++;
            if (n == null) continue;
            if (n.isVisibleToUser() && n.isClickable()) {
                String l = shortLabel(n);
                for (String want : labels) if (l.equalsIgnoreCase(want)) return n;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) queue.add(c);
            }
        }
        return null;
    }

    /** Tippt den Knoten an, oder das naechste antippbare Element darueber. */
    private static boolean click(AccessibilityNodeInfo n) {
        for (int i = 0; n != null && i < 4; i++) {
            if (n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            n = n.getParent();
        }
        return false;
    }

    private static String shortId(AccessibilityNodeInfo n) {
        String full = n.getViewIdResourceName();
        if (full == null) return "";
        int i = full.indexOf(":id/");
        return (i >= 0 ? full.substring(i + 4) : full).toLowerCase(Locale.ROOT);
    }

    // Nur kurze Beschriftungen (Reiter-Namen), nie Nachrichten oder Namen.
    private static String shortLabel(AccessibilityNodeInfo n) {
        CharSequence c = n.getContentDescription();
        if (c == null || c.length() == 0) c = n.getText();
        if (c == null) return "";
        String s = c.toString().trim();
        return s.length() <= 14 ? s : "";
    }

    private static String shortPkg(String pkg) {
        if (pkg.equals(YOUTUBE)) return "YouTube";
        if (pkg.equals(SNAPCHAT)) return "Snapchat";
        return "Chrome";
    }
}
