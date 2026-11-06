package com.recco.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int WHITE_DIM = 0xA6FFFFFF;

    private static class App {
        String label;
        Drawable icon;
        Drawable banner;
        int[] tileColors;
        ComponentName component;
    }

    private LinearLayout appsRow;
    private LinearLayout recoRow;
    private TextView heroTitle;
    private TextView heroSub;
    private App currentApp;

    private float density;
    private int widthPx;
    private int heightPx;
    private int tileW;
    private int tileH;
    private int recoW;
    private int recoH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;
        widthPx = dm.widthPixels;
        heightPx = dm.heightPixels;
        tileW = (int) (widthPx * 0.23f);
        tileH = (int) (tileW * 0.6f);
        recoW = (int) (widthPx * 0.16f);
        recoH = (int) (recoW * 9f / 16f);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        RecoService.setListener(() -> runOnUiThread(this::refreshHero));
        loadApps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        RecoService.setListener(null);
    }

    @Override
    public void onBackPressed() {
        // Un launcher no debe cerrarse con "atrás".
    }

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientRadius(dp(1100));
        bg.setGradientCenter(0.28f, 0.05f);
        bg.setColors(new int[]{0xFF1D55B8, 0xFF0A1C42, 0xFF02050D});
        root.setBackground(bg);
        setContentView(root);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildHero(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout center = new FrameLayout(this);
        center.setClipChildren(false);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);

        appsRow = new LinearLayout(this);
        appsRow.setOrientation(LinearLayout.HORIZONTAL);
        appsRow.setClipChildren(false);
        appsRow.setPadding(dp(56), dp(24), dp(56), dp(40));
        scroller.addView(appsRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        center.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));
        root.addView(center, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private View buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(56), dp(24), dp(56), dp(0));

        // Nombre: mayúsculas, trazo fino y mucho espaciado entre letras
        String name = getString(R.string.app_name).toUpperCase();
        int space = name.indexOf(' ');
        SpannableStringBuilder sb = new SpannableStringBuilder(name);
        if (space > 0) {
            sb.setSpan(new TypefaceSpan("sans-serif-medium"), 0, space,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new TypefaceSpan("sans-serif-thin"), space, name.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(WHITE_DIM), space, name.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView title = new TextView(this);
        title.setText(sb);
        title.setTextColor(WHITE);
        title.setTextSize(20);
        title.setLetterSpacing(0.32f);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        addIcon(top, R.drawable.ic_settings, v -> openSettings(Settings.ACTION_SETTINGS));
        addIcon(top, R.drawable.ic_hdmi, v -> showInputs());
        addIcon(top, R.drawable.ic_account, v -> openSettings(Settings.ACTION_SYNC_SETTINGS));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("HH:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(WHITE);
        clock.setTextSize(24);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(20);
        top.addView(clock, clp);
        return top;
    }

    private void addIcon(LinearLayout parent, int res, View.OnClickListener l) {
        final ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setColorFilter(WHITE);
        v.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.OVAL);
        focused.setColor(0xF2FFFFFF);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        v.setBackground(sl);

        v.setFocusable(true);
        v.setOnFocusChangeListener((view, has) -> {
            v.setColorFilter(has ? 0xFF0A1C42 : WHITE);
            view.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(140).start();
        });
        v.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
        lp.leftMargin = dp(6);
        parent.addView(v, lp);
    }

    // -------------------------------------------- Zona de recomendaciones

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setClipChildren(false);
        hero.setPadding(dp(56), dp(18), dp(56), dp(0));

        heroTitle = new TextView(this);
        heroTitle.setTextColor(WHITE);
        heroTitle.setTextSize(34);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hero.addView(heroTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        heroSub = new TextView(this);
        heroSub.setTextColor(WHITE_DIM);
        heroSub.setTextSize(15);
        heroSub.setSingleLine(true);
        heroSub.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(heroSub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(14), 0, dp(14));

        recoRow = new LinearLayout(this);
        recoRow.setOrientation(LinearLayout.HORIZONTAL);
        recoRow.setClipChildren(false);
        scroll.addView(recoRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        hero.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, recoH + dp(28)));
        return hero;
    }

    private boolean listenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void refreshHero() {
        if (currentApp != null && !recoRow.hasFocus()) showHero(currentApp);
    }

    private void showHero(App a) {
        currentApp = a;
        heroTitle.setText(a.label);
        recoRow.removeAllViews();

        List<RecoService.Reco> list = RecoService.forPackage(a.component.getPackageName());
        if (list.isEmpty()) {
            if (!listenerEnabled()) {
                heroSub.setText("Activa el acceso a notificaciones para ver recomendaciones aquí");
                recoRow.addView(buildEnableButton());
            } else {
                heroSub.setText("Sin recomendaciones por ahora");
            }
        } else {
            heroSub.setText("Recomendado en " + a.label);
            for (int i = 0; i < list.size() && i < 8; i++) {
                recoRow.addView(buildRecoCard(list.get(i)));
            }
        }
    }

    private View buildEnableButton() {
        TextView b = new TextView(this);
        b.setText("Activar recomendaciones");
        b.setTextColor(WHITE);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(24), 0, dp(24), 0);

        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(40));
        normal.setColor(0x33FFFFFF);
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(dp(40));
        focused.setColor(0x66FFFFFF);
        focused.setStroke(dp(3), WHITE);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        b.setBackground(sl);

        makeFocusable(b, 1.06f);
        b.setOnClickListener(v -> openNotificationAccess());
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
        return b;
    }

    private View buildRecoCard(final RecoService.Reco r) {
        final FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0xFF1E2A4A);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(dp(2));

        if (r.image != null) {
            ImageView img = new ImageView(this);
            img.setImageDrawable(r.image);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        TextView t = new TextView(this);
        t.setText(r.title != null ? r.title : "");
        t.setTextColor(WHITE);
        t.setTextSize(13);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(10), dp(22), dp(10), dp(8));
        t.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xCC000000, 0x00000000}));
        card.addView(t, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(12));
        ring.setStroke(dp(3), WHITE);
        card.setFocusable(true);
        card.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(150).start();
            card.setForeground(has ? ring : null);
        });
        card.setOnClickListener(v -> openReco(r));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(recoW, recoH);
        lp.rightMargin = dp(18);
        card.setLayoutParams(lp);
        return card;
    }

    private void openReco(RecoService.Reco r) {
        try {
            if (r.intent != null) {
                r.intent.send();
            }
        } catch (PendingIntent.CanceledException e) {
            Toast.makeText(this, "Esta recomendación ya no está disponible", Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotificationAccess() {
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this,
                    "Abre Ajustes y busca \"Acceso a notificaciones\" para activar Androgena",
                    Toast.LENGTH_LONG).show();
            openSettings(Settings.ACTION_SETTINGS);
        }
    }

    private void makeFocusable(View v, final float focusScale) {
        v.setFocusable(true);
        v.setOnFocusChangeListener((view, has) ->
                view.animate().scaleX(has ? focusScale : 1f).scaleY(has ? focusScale : 1f)
                        .setDuration(150).start());
    }

    // -------------------------------------------------------------- Apps

    private void loadApps() {
        appsRow.removeAllViews();
        PackageManager pm = getPackageManager();

        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String c : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(c);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || found.containsKey(p)) continue;
                found.put(p, ri);
            }
        }

        List<App> apps = new ArrayList<>();
        for (ResolveInfo ri : found.values()) {
            App a = new App();
            a.label = String.valueOf(ri.loadLabel(pm));
            a.icon = ri.loadIcon(pm);
            try {
                a.banner = ri.activityInfo.loadBanner(pm);
            } catch (Exception e) {
                a.banner = null;
            }
            a.tileColors = tileColors(a.icon);
            a.component = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            apps.add(a);
        }
        Collections.sort(apps, (x, y) -> x.label.compareToIgnoreCase(y.label));

        for (App a : apps) {
            appsRow.addView(buildAppCard(a));
        }
        if (appsRow.getChildCount() > 0) {
            appsRow.getChildAt(0).requestFocus();
        }
    }

    // Color del mosaico tomado del icono de la app (como en tvOS).
    private int[] tileColors(Drawable icon) {
        int[] fallback = new int[]{0xFF3A5287, 0xFF1F2F55};
        try {
            Drawable.ConstantState cs = icon.getConstantState();
            if (cs == null) return fallback;
            Drawable d = cs.newDrawable().mutate();
            Bitmap bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, 24, 24);
            d.draw(c);
            long r = 0, g = 0, b = 0;
            int n = 0;
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    int p = bmp.getPixel(x, y);
                    if (Color.alpha(p) > 200) {
                        r += Color.red(p);
                        g += Color.green(p);
                        b += Color.blue(p);
                        n++;
                    }
                }
            }
            bmp.recycle();
            if (n > 20) {
                float[] hsv = new float[3];
                Color.colorToHSV(Color.rgb((int) (r / n), (int) (g / n), (int) (b / n)), hsv);
                if (hsv[1] > 0.25f) {
                    hsv[2] = 0.62f;
                    int top = Color.HSVToColor(hsv);
                    hsv[2] = 0.34f;
                    int bottom = Color.HSVToColor(hsv);
                    return new int[]{top, bottom};
                }
            }
        } catch (Exception e) {
            // se usa el color por defecto
        }
        return fallback;
    }

    private View buildAppCard(final App a) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setClipChildren(false);

        final FrameLayout tile = new FrameLayout(this);
        GradientDrawable tileBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, a.tileColors);
        tileBg.setCornerRadius(dp(16));
        tile.setBackground(tileBg);
        tile.setClipToOutline(true);
        tile.setElevation(dp(2));

        ImageView img = new ImageView(this);
        if (a.banner != null) {
            img.setImageDrawable(a.banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            img.setImageDrawable(a.icon);
            int s = (int) (tileH * 0.52f);
            tile.addView(img, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }
        card.addView(tile, new LinearLayout.LayoutParams(tileW, tileH));

        final TextView label = new TextView(this);
        label.setText(a.label);
        label.setTextColor(WHITE);
        label.setTextSize(16);
        label.setAlpha(0.6f);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                tileW, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(12);
        card.addView(label, llp);

        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(16));
        ring.setStroke(dp(3), WHITE);

        card.setFocusable(true);
        card.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(160).start();
            tile.animate().translationZ(has ? dp(16) : 0).setDuration(160).start();
            label.animate().alpha(has ? 1f : 0.6f).setDuration(160).start();
            tile.setForeground(has ? ring : null);
            if (has) showHero(a);
        });
        card.setOnClickListener(v -> launch(a.component));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(28);
        card.setLayoutParams(lp);
        return card;
    }

    // ----------------------------------------------------------- Acciones

    private void launch(ComponentName cn) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir la app", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                Toast.makeText(this, "No se pudo abrir ajustes", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showInputs() {
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        final List<TvInputInfo> inputs = new ArrayList<>();
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) inputs.add(info);
            }
        }
        if (inputs.isEmpty()) {
            Toast.makeText(this, "No se encontraron entradas HDMI", Toast.LENGTH_LONG).show();
            return;
        }
        CharSequence[] names = new CharSequence[inputs.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = String.valueOf(inputs.get(i).loadLabel(this));
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Entradas")
                .setItems(names, (d, which) -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW,
                                TvContract.buildChannelUriForPassthroughInput(inputs.get(which).getId()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(this, "No se pudo abrir la entrada", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }
}
