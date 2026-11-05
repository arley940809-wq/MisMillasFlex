package com.arley.millasflex;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

// Servicio en primer plano: mantiene el GPS vivo aunque la pantalla se
// apague o la app pase a segundo plano, y suma las millas recorridas.
public class TrackingService extends Service {

    public static final String ACTION_START = "com.arley.millasflex.START";
    public static final String ACTION_STOP = "com.arley.millasflex.STOP";
    public static final String BROADCAST_UPDATE = "com.arley.millasflex.UPDATE";
    public static final String BROADCAST_SAVED = "com.arley.millasflex.SAVED";
    public static final String EXTRA_MILES = "miles";
    public static final String EXTRA_ELAPSED_MIN = "elapsedMin";

    private static final String CHANNEL_ID = "seguimiento_millas";
    private static final int NOTIF_ID = 1;
    private static final double METERS_PER_MILE = 1609.34;

    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private Location lastLocation;
    private double miles = 0;
    private long startTime = 0;
    private String tripType = "Trabajo";
    private boolean tracking = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            tripType = intent.getStringExtra("type");
            if (tripType == null) tripType = "Trabajo";
            startTracking();
        } else if (ACTION_STOP.equals(action)) {
            stopTrackingAndSave();
        }
        return START_STICKY;
    }

    private void startTracking() {
        if (tracking) return;
        tracking = true;
        miles = 0;
        lastLocation = null;
        startTime = System.currentTimeMillis();

        // Recuerda que hay un viaje en curso (por si el sistema mata el servicio).
        SharedPreferences prefs = getSharedPreferences("tracking", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("active", true)
                .putLong("startTime", startTime)
                .putString("type", tripType)
                .putFloat("miles", 0f)
                .apply();

        createChannel();
        Notification notif = buildNotification(0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, notif);
        }

        fused = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000) // cada 5 segundos
                .setMinUpdateDistanceMeters(10)       // o cada 10 metros
                .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null || !tracking) return;
                for (Location loc : result.getLocations()) {
                    if (loc == null) continue;
                    // Descarta lecturas malas del GPS.
                    if (loc.hasAccuracy() && loc.getAccuracy() > 50) continue;
                    if (lastLocation != null) {
                        float d = lastLocation.distanceTo(loc);
                        // Descarta saltos imposibles del GPS (más de 400 m en 5 s).
                        if (d < 400) {
                            miles += d / METERS_PER_MILE;
                        }
                    }
                    lastLocation = loc;
                }
                // Guarda el progreso por si el sistema mata el servicio.
                getSharedPreferences("tracking", MODE_PRIVATE).edit()
                        .putFloat("miles", (float) miles)
                        .apply();
                updateNotification();
                sendUpdate();
            }
        };

        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper());
        } catch (SecurityException e) {
            // Sin permiso de ubicación no se puede registrar: avisa y para.
            tracking = false;
            getSharedPreferences("tracking", MODE_PRIVATE).edit()
                    .putBoolean("active", false)
                    .apply();
            stopForeground(true);
            stopSelf();
        }
        sendUpdate();
    }

    private void stopTrackingAndSave() {
        if (!tracking) {
            stopSelf();
            return;
        }
        tracking = false;
        if (fused != null && callback != null) {
            fused.removeLocationUpdates(callback);
        }
        long endTime = System.currentTimeMillis();
        Trip trip = new Trip(startTime, startTime, endTime, miles, tripType, false);
        TripStore.add(this, trip);

        getSharedPreferences("tracking", MODE_PRIVATE).edit()
                .putBoolean("active", false)
                .apply();

        stopForeground(true);
        sendBroadcast(new Intent(BROADCAST_SAVED));
        stopSelf();
    }

    // Si el sistema mata el servicio a la fuerza, guarda el viaje parcial.
    @Override
    public void onDestroy() {
        if (tracking) {
            tracking = false;
            if (fused != null && callback != null) {
                fused.removeLocationUpdates(callback);
            }
            Trip trip = new Trip(startTime, startTime, System.currentTimeMillis(),
                    miles, tripType, false);
            TripStore.add(this, trip);
            getSharedPreferences("tracking", MODE_PRIVATE).edit()
                    .putBoolean("active", false)
                    .apply();
        }
        super.onDestroy();
    }

    private void sendUpdate() {
        Intent i = new Intent(BROADCAST_UPDATE);
        i.putExtra(EXTRA_MILES, miles);
        long elapsedMin = (System.currentTimeMillis() - startTime) / 60000;
        i.putExtra(EXTRA_ELAPSED_MIN, elapsedMin);
        sendBroadcast(i);
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        long elapsedMin = (System.currentTimeMillis() - startTime) / 60000;
        nm.notify(NOTIF_ID, buildNotification(miles, elapsedMin));
    }

    private Notification buildNotification(double miles, long elapsedMin) {
        String text = String.format(java.util.Locale.US,
                "Registrando millas: %.2f mi - %d min", miles, elapsedMin);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("Mis Millas Flex")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Registro de millas",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Aviso mientras se registran las millas del viaje");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }
}
