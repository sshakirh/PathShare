package com.pathshare.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.pathshare.app.R;
import com.pathshare.app.activities.MainActivity;
import com.pathshare.app.models.Route;
import com.pathshare.app.models.RoutePoint;
import com.pathshare.app.utils.RouteRepository;

/**
 * Foreground service that continuously records GPS waypoints into a Route.
 * Activities bind to this service to observe the current recording state.
 */
public class LocationTrackingService extends Service {

    public static final String ACTION_START = "com.pathshare.START_TRACKING";
    public static final String ACTION_STOP  = "com.pathshare.STOP_TRACKING";
    public static final String EXTRA_ROUTE_NAME = "route_name";

    private static final String CHANNEL_ID   = "location_tracking";
    private static final int    NOTIF_ID     = 1001;
    private static final long   INTERVAL_MS  = 3000;   // 3 seconds
    private static final float  MIN_ACCURACY = 30f;    // ignore if > 30 m accuracy

    // Listeners so the UI gets live updates
    public interface TrackingListener {
        void onPointAdded(RoutePoint point, Route currentRoute);
        void onTrackingStopped(Route finishedRoute);
    }

    // ── Binder ──────────────────────────────────
    public class LocalBinder extends Binder {
        public LocationTrackingService getService() { return LocationTrackingService.this; }
    }
    private final IBinder binder = new LocalBinder();

    @Nullable @Override
    public IBinder onBind(Intent intent) { return binder; }

    // ── State ────────────────────────────────────
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private Route currentRoute;
    private RouteRepository repository;
    private TrackingListener listener;
    public boolean isTracking = false;

    // ── Lifecycle ────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        repository  = new RouteRepository(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            String name = intent.getStringExtra(EXTRA_ROUTE_NAME);
            if (name == null) name = "Route " + System.currentTimeMillis();
            startTracking(name);
        } else if (ACTION_STOP.equals(action)) {
            stopTracking();
        }
        return START_STICKY;
    }

    // ── Tracking control ─────────────────────────

    public void startTracking(String routeName) {
        if (isTracking) return;
        currentRoute = new Route(routeName);
        isTracking   = true;

        startForeground(NOTIF_ID, buildNotification("Recording route…"));
        requestLocationUpdates();
    }

    public void stopTracking() {
        if (!isTracking) return;
        isTracking = false;

        fusedClient.removeLocationUpdates(locationCallback);

        if (currentRoute != null) {
            currentRoute.finish();
            repository.saveRoute(currentRoute);
            if (listener != null) listener.onTrackingStopped(currentRoute);
        }

        stopForeground(true);
        stopSelf();
    }

    @SuppressWarnings("MissingPermission")
    private void requestLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(1000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null || currentRoute == null) return;
                android.location.Location loc = result.getLastLocation();
                if (loc == null || loc.getAccuracy() > MIN_ACCURACY) return;

                RoutePoint point = new RoutePoint(
                        loc.getLatitude(),
                        loc.getLongitude(),
                        loc.getAccuracy(),
                        loc.getSpeed(),
                        loc.getBearing(),
                        loc.getTime(),
                        loc.getAltitude()
                );
                currentRoute.addPoint(point);

                // Update notification with live stats
                updateNotification(currentRoute);

                if (listener != null) listener.onPointAdded(point, currentRoute);
            }
        };

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    // ── Public accessors ─────────────────────────

    public Route getCurrentRoute()            { return currentRoute; }
    public void setListener(TrackingListener l) { this.listener = l; }

    // ── Notification helpers ─────────────────────

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Used while recording your route");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Intent tapIntent = new Intent(this, MainActivity.class);
        PendingIntent pi  = PendingIntent.getActivity(this, 0, tapIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PathShare – Recording")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_record)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(Route route) {
        String text = String.format("%.2f km  •  %d pts",
                route.totalDistanceMetres / 1000.0,
                route.points.size());
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(text));
    }
}
