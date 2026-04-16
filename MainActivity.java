package com.pathshare.app.activities;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.pathshare.app.R;
import com.pathshare.app.databinding.ActivityMainBinding;
import com.pathshare.app.models.Route;
import com.pathshare.app.models.RoutePoint;
import com.pathshare.app.services.LocationTrackingService;
import com.pathshare.app.utils.RouteRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Main screen: record a new route, see it drawn live on Google Maps,
 * stop recording, and share the route code with friends.
 */
public class MainActivity extends AppCompatActivity
        implements OnMapReadyCallback, LocationTrackingService.TrackingListener {

    private ActivityMainBinding binding;
    private GoogleMap map;
    private Polyline currentPolyline;
    private List<LatLng> routeLatLngs = new ArrayList<>();

    private LocationTrackingService trackingService;
    private boolean serviceBound = false;
    private RouteRepository repository;

    // ── Permission launcher ──────────────────────
    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                if (fine) onPermissionsGranted();
                else Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show();
            });

    // ── Service connection ───────────────────────
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            LocationTrackingService.LocalBinder lb = (LocationTrackingService.LocalBinder) binder;
            trackingService = lb.getService();
            trackingService.setListener(MainActivity.this);
            serviceBound = true;

            // Restore UI state if service was already tracking
            if (trackingService.isTracking) {
                setRecordingUI(true);
                Route current = trackingService.getCurrentRoute();
                if (current != null) restoreRouteOnMap(current);
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    // ── Lifecycle ────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding    = ActivityMainBinding.inflate(getLayoutInflater());
        repository = new RouteRepository(this);
        setContentView(binding.getRoot());

        // Map
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // Button listeners
        binding.btnRecord.setOnClickListener(v -> onRecordClicked());
        binding.btnStop.setOnClickListener(v -> onStopClicked());
        binding.btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, RouteHistoryActivity.class)));
        binding.btnEnterCode.setOnClickListener(v -> showEnterCodeDialog());

        // Handle deep link (pathshare://route?code=XXXXXX)
        handleDeepLink(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, LocationTrackingService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound) {
            trackingService.setListener(null);
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);
    }

    // ── Map ready ────────────────────────────────

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setCompassEnabled(true);

        checkAndRequestPermissions();
    }

    // ── Permission handling ──────────────────────

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            onPermissionsGranted();
        } else {
            permLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @SuppressWarnings("MissingPermission")
    private void onPermissionsGranted() {
        if (map != null) {
            map.setMyLocationEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);
        }
    }

    // ── Recording control ────────────────────────

    private void onRecordClicked() {
        if (!hasLocationPermission()) {
            checkAndRequestPermissions();
            return;
        }
        // Ask for route name
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("e.g. Morning walk to market");
        new AlertDialog.Builder(this)
                .setTitle("Name this route")
                .setView(et)
                .setPositiveButton("Start", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) name = "My Route";
                    startRecording(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startRecording(String name) {
        // Clear old polyline
        routeLatLngs.clear();
        if (currentPolyline != null) currentPolyline.remove();

        Intent serviceIntent = new Intent(this, LocationTrackingService.class);
        serviceIntent.setAction(LocationTrackingService.ACTION_START);
        serviceIntent.putExtra(LocationTrackingService.EXTRA_ROUTE_NAME, name);
        ContextCompat.startForegroundService(this, serviceIntent);

        setRecordingUI(true);
        Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show();
    }

    private void onStopClicked() {
        new AlertDialog.Builder(this)
                .setTitle("Stop Recording?")
                .setMessage("Your route will be saved. You can share it afterwards.")
                .setPositiveButton("Stop & Save", (d, w) -> stopRecording())
                .setNegativeButton("Continue", null)
                .show();
    }

    private void stopRecording() {
        Intent serviceIntent = new Intent(this, LocationTrackingService.class);
        serviceIntent.setAction(LocationTrackingService.ACTION_STOP);
        startService(serviceIntent);
        setRecordingUI(false);
    }

    // ── TrackingListener callbacks (from service) ─

    @Override
    public void onPointAdded(RoutePoint point, Route currentRoute) {
        runOnUiThread(() -> {
            LatLng ll = new LatLng(point.latitude, point.longitude);
            routeLatLngs.add(ll);

            if (currentPolyline == null) {
                currentPolyline = map.addPolyline(new PolylineOptions()
                        .color(0xFF1A73E8)   // Google blue
                        .width(12)
                        .geodesic(true));
            }
            currentPolyline.setPoints(routeLatLngs);
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 17f));

            // Update stats overlay
            binding.tvStats.setText(String.format("%.2f km  •  %ds",
                    currentRoute.totalDistanceMetres / 1000.0,
                    currentRoute.getDurationSeconds()));
        });
    }

    @Override
    public void onTrackingStopped(Route finishedRoute) {
        runOnUiThread(() -> {
            setRecordingUI(false);
            showShareDialog(finishedRoute);
        });
    }

    // ── Share route ──────────────────────────────

    private void showShareDialog(Route route) {
        new AlertDialog.Builder(this)
                .setTitle("Route Saved!")
                .setMessage(String.format(
                        "Distance: %.2f km\nDuration: %ds\n\nShare this route with friends?",
                        route.totalDistanceMetres / 1000.0, route.getDurationSeconds()))
                .setPositiveButton("Share Code", (d, w) -> uploadAndShare(route))
                .setNegativeButton("Later", null)
                .show();
    }

    private void uploadAndShare(Route route) {
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.shareRoute(route, new RouteRepository.Callback<String>() {
            @Override
            public void onSuccess(String code) {
                binding.progressBar.setVisibility(View.GONE);
                showShareCode(route, code);
            }
            @Override
            public void onError(Exception e) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this,
                        "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showShareCode(Route route, String code) {
        String shareText = "Follow my route on PathShare!\nCode: " + code
                + "\nOr tap: pathshare://route?code=" + code;

        new AlertDialog.Builder(this)
                .setTitle("Share Code: " + code)
                .setMessage("Send this code to your friends. They can enter it in PathShare to follow your exact path.")
                .setPositiveButton("Share via Apps", (d, w) -> {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, shareText);
                    startActivity(Intent.createChooser(share, "Share Route"));
                })
                .setNegativeButton("Done", null)
                .show();
    }

    // ── Enter code (friend flow) ─────────────────

    private void showEnterCodeDialog() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("Enter 6-character code");
        et.setInputType(android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(this)
                .setTitle("Load Shared Route")
                .setView(et)
                .setPositiveButton("Load", (d, w) -> {
                    String code = et.getText().toString().trim().toUpperCase();
                    if (code.length() == 6) loadSharedRoute(code);
                    else Toast.makeText(this, "Code must be 6 characters", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadSharedRoute(String code) {
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.fetchSharedRoute(code, new RouteRepository.Callback<Route>() {
            @Override
            public void onSuccess(Route route) {
                binding.progressBar.setVisibility(View.GONE);
                // Save locally, then open playback
                repository.saveRoute(route);
                openPlayback(route.id);
            }
            @Override
            public void onError(Exception e) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this,
                        "Could not load route: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Deep link ────────────────────────────────

    private void handleDeepLink(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null && "pathshare".equals(data.getScheme())) {
            String code = data.getQueryParameter("code");
            if (code != null && !code.isEmpty()) loadSharedRoute(code);
        }
    }

    // ── Helpers ──────────────────────────────────

    private void restoreRouteOnMap(Route route) {
        if (map == null || route.points.isEmpty()) return;
        routeLatLngs.clear();
        for (RoutePoint p : route.points) routeLatLngs.add(new LatLng(p.latitude, p.longitude));
        if (currentPolyline == null) {
            currentPolyline = map.addPolyline(new PolylineOptions()
                    .color(0xFF1A73E8).width(12).geodesic(true));
        }
        currentPolyline.setPoints(routeLatLngs);
        LatLng last = routeLatLngs.get(routeLatLngs.size() - 1);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(last, 17f));
    }

    private void openPlayback(String routeId) {
        Intent i = new Intent(this, RoutePlaybackActivity.class);
        i.putExtra(RoutePlaybackActivity.EXTRA_ROUTE_ID, routeId);
        startActivity(i);
    }

    private void setRecordingUI(boolean recording) {
        binding.btnRecord.setVisibility(recording ? View.GONE  : View.VISIBLE);
        binding.btnStop.setVisibility(recording   ? View.VISIBLE : View.GONE);
        binding.tvStats.setVisibility(recording   ? View.VISIBLE : View.GONE);
        if (!recording) binding.tvStats.setText("");
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
