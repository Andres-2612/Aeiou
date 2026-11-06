package com.recco.launcher;

import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee las "recomendaciones" que las apps de TV publican como notificaciones
 * (categoría recommendation) y las guarda para mostrarlas en el launcher.
 */
public class RecoService extends NotificationListenerService {

    public static class Reco {
        public String key;
        public String pkg;
        public CharSequence title;
        public CharSequence text;
        public Drawable image;
        public PendingIntent intent;
    }

    private static final Map<String, Reco> ALL = new LinkedHashMap<>();
    private static Runnable listener;

    public static synchronized void setListener(Runnable r) {
        listener = r;
    }

    public static synchronized List<Reco> forPackage(String pkg) {
        List<Reco> out = new ArrayList<>();
        for (Reco r : ALL.values()) {
            if (pkg.equals(r.pkg)) out.add(r);
        }
        return out;
    }

    private static void fire() {
        Runnable r;
        synchronized (RecoService.class) {
            r = listener;
        }
        if (r != null) r.run();
    }

    @Override
    public void onListenerConnected() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) add(sbn);
            }
        } catch (Exception e) {
            // sin acceso todavía
        }
        fire();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (add(sbn)) fire();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        boolean removed;
        synchronized (RecoService.class) {
            removed = ALL.remove(sbn.getKey()) != null;
        }
        if (removed) fire();
    }

    private boolean add(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null || !Notification.CATEGORY_RECOMMENDATION.equals(n.category)) return false;

        Reco r = new Reco();
        r.key = sbn.getKey();
        r.pkg = sbn.getPackageName();
        Bundle ex = n.extras;
        if (ex != null) {
            r.title = ex.getCharSequence(Notification.EXTRA_TITLE);
            r.text = ex.getCharSequence(Notification.EXTRA_TEXT);
            Object pic = ex.get(Notification.EXTRA_PICTURE);
            if (pic instanceof Bitmap) {
                r.image = new BitmapDrawable(getResources(), (Bitmap) pic);
            }
        }
        if (r.image == null) {
            try {
                Icon ic = n.getLargeIcon();
                if (ic != null) r.image = ic.loadDrawable(this);
            } catch (Exception e) {
                r.image = null;
            }
        }
        r.intent = n.contentIntent;

        synchronized (RecoService.class) {
            ALL.put(r.key, r);
        }
        return true;
    }
}
