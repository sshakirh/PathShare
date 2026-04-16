package com.pathshare.app.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.pathshare.app.R;
import com.pathshare.app.databinding.ActivityRoutePlaybackBinding;
import com.pathshare.app.models.Route;
import com.pathshare.app.models.RoutePoint;
import com.pathshare.app.utils.RouteRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a shared route on the map and guides the user along it in real-time.
 *
 * Features:
 * – Full route drawn in grey
 * – Completed portion drawn in green
 * – "You are here" marker updated every 3 seconds
 * – Nearest point on route highlighted; distance + instruction shown
 * – Vibration + toast on arrival at destination
 */
public class RoutePlaybackActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_ROUTE_ID = "route_id";

    // How close (metres) counts as "on route"
    private static final double ON_ROUTE_THRESHOLD = 30.0;
    // How close to the final point counts as "arrived"
    private static final double ARRIVAL_THRESHOLD = 25.0;

    private ActivityRoutePlaybackBinding binding;
    private GoogleMap map;
    private Route route;
    private RouteRepository repository;

    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;

    private Polyline fullPolyline;
    private Polyline completedPolyline;
    private Marker userMarker;
    private Marker nextWaypointMarker;
    private int currentTargetIndex = 0;
    private boolean arrived = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding    = ActivityRoutePlaybackBinding.inflate(getLayoutInflater());
        repository = new RouteRepository(this);
        setContentView(binding.getRoot());

        String routeId = getIntent().getStringExtra(EXTRA_ROUTE_ID);
        if (routeId == null) { finish(); return; }

        route = repository.getRouteById(routeId);
        if (route == null || route.points.isEmpty()) {
            Toast.makeText(this, "Route data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.tvRouteName.setText(route.name);
        binding.tvRouteInfo.setText(String.format("%.2f km  •  %d waypoints",
                route.totalDistanceMetres / 1000.0, route.points.size()));

        SupportMapFragment mapFrag = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map_playback);
        if (mapFrag != null) mapFrag.getMapAsync(this);

        binding.btnStartFollowing.setOnClickListener(v -> startFollowing());
        binding.btnStopFollowing.setOnClickListener(v -> stopFollowing());

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopFollowing();
    }

    // ── Map ready ────────────────────────────────

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled(true);
        drawFullRoute();
    }

    private void drawFullRoute() {
        if (map == null || route.points.isEmpty()) return;

        List<LatLng> allPoints = new ArrayList<>();
        for (RoutePoint p : route.points) allPoints.add(new LatLng(p.latitude, p.longitude));

        // Full route in grey
        fullPolyline = map.addPolyline(new PolylineOptions()
                .addAll(allPoints)
                .color(0xFFBDBDBD)
                .width(10)
                .geodesic(true));

        // Completed portion placeholder (empty, green)
        completedPolyline = map.addPolyline(new PolylineOptions()
                .color(0xFF34A853)
                .width(10)
                .geodesic(true));

        // Start marker
        map.addMarker(new MarkerOptions()
                .position(allPoints.get(0))
                .title("Start")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // End marker
        map.addMarker(new MarkerOptions()
                .position(allPoints.get(allPoints.size() - 1))
                .title("Destination")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Fit camera to route
        com.google.android.gms.maps.model.LatLngBounds.Builder bounds =
                new com.google.android.gms.maps.model.LatLngBounds.Builder();
        for (LatLng ll : allPoints) bounds.include(ll);
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80));
    }

    // ── Following ────────────────────────────────

    @SuppressWarnings("MissingPermission")
    private void startFollowing() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission needed", Toast.LENGTH_SHORT).show();
            return;
        }

        currentTargetIndex = 0;
        arrived = false;
        binding.btnStartFollowing.setVisibility(View.GONE);
        binding.btnStopFollowing.setVisibility(View.VISIBLE);
        binding.cardGuidance.setVisibility(View.VISIBLE);
        binding.tvGuidance.setText("Getting your location…");

        map.setMyLocationEnabled(true);

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1000).build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                Location loc = result.getLastLocation();
                if (loc != null) onUserLocationUpdated(loc);
            }
        };

        fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
    }

    private void stopFollowing() {
        if (locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
        binding.btnStartFollowing.setVisibility(View.VISIBLE);
        binding.btnStopFollowing.setVisibility(View.GONE);
    }

    // ── Live guidance ────────────────────────────

    private void onUserLocationUpdated(Location loc) {
        LatLng userLatLng = new LatLng(loc.getLatitude(), loc.getLongitude());

        // Update user marker
        if (userMarker == null) {
            userMarker = map.addMarker(new MarkerOptions()
                    .position(userLatLng)
                    .title("You")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        } else {
            userMarker.setPosition(userLatLng);
        }

        // Find nearest route point index ahead of current target
        int nearestIndex = findNearestPointIndex(loc);
        if (nearestIndex > currentTargetIndex) {
            currentTargetIndex = nearestIndex;
        }

        // Update completed polyline
        updateCompletedPolyline(currentTargetIndex);

        // Check arrival
        RoutePoint destination = route.points.get(route.points.size() - 1);
        float[] dist = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                destination.latitude, destination.longitude, dist);

        if (!arrived && dist[0] <= ARRIVAL_THRESHOLD) {
            arrived = true;
            onArrived();
            return;
        }

        // Guidance to next waypoint
        updateGuidance(loc);

        // Keep camera centred on user
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 17f));
    }

    private int findNearestPointIndex(Location loc) {
        double minDist = Double.MAX_VALUE;
        int nearest = currentTargetIndex;
        int searchFrom = Math.max(0, currentTargetIndex - 5);
        int searchTo   = Math.min(route.points.size() - 1, currentTargetIndex + 30);

        for (int i = searchFrom; i <= searchTo; i++) {
            RoutePoint p = route.points.get(i);
            float[] d = new float[1];
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                    p.latitude, p.longitude, d);
            if (d[0] < minDist) {
                minDist = d[0];
                nearest = i;
            }
        }
        return nearest;
    }

    private void updateCompletedPolyline(int upToIndex) {
        List<LatLng> done = new ArrayList<>();
        for (int i = 0; i <= Math.min(upToIndex, route.points.size() - 1); i++) {
            RoutePoint p = route.points.get(i);
            done.add(new LatLng(p.latitude, p.longitude));
        }
        completedPolyline.setPoints(done);
    }

    private void updateGuidance(Location loc) {
        // Look 5-10 points ahead as "next target"
        int ahead = Math.min(currentTargetIndex + 8, route.points.size() - 1);
        RoutePoint target = route.points.get(ahead);

        float[] bearingDist = new float[2];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                target.latitude, target.longitude, bearingDist);

        float distToTarget = bearingDist[0];
        float bearingToTarget = bearingDist[1];
        float currentBearing = loc.getBearing();

        float relativeBearing = (bearingToTarget - currentBearing + 360) % 360;
        String direction;
        if (relativeBearing < 30 || relativeBearing > 330)       direction = "⬆ Go straight";
        else if (relativeBearing >= 30  && relativeBearing < 75)  direction = "↗ Bear right";
        else if (relativeBearing >= 75  && relativeBearing < 120) direction = "➡ Turn right";
        else if (relativeBearing >= 120 && relativeBearing < 180) direction = "↘ Sharp right";
        else if (relativeBearing >= 180 && relativeBearing < 240) direction = "↙ Sharp left";
        else if (relativeBearing >= 240 && relativeBearing < 285) direction = "⬅ Turn left";
        else if (relativeBearing >= 285 && relativeBearing < 330) direction = "↖ Bear left";
        else direction = "⬆ Go straight";

        // Remaining distance
        double remaining = 0;
        for (int i = currentTargetIndex; i < route.points.size() - 1; i++) {
            remaining += route.points.get(i).distanceTo(route.points.get(i + 1));
        }

        binding.tvGuidance.setText(direction);
        binding.tvDistance.setText(String.format("Next point: %.0f m  •  Remaining: %.2f km",
                distToTarget, remaining / 1000.0));
        binding.progressRoute.setMax(route.points.size());
        binding.progressRoute.setProgress(currentTargetIndex);

        // Update next waypoint marker
        LatLng targetLL = new LatLng(target.latitude, target.longitude);
        if (nextWaypointMarker == null) {
            nextWaypointMarker = map.addMarker(new MarkerOptions()
                    .position(targetLL)
                    .title("Next")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
        } else {
            nextWaypointMarker.setPosition(targetLL);
        }
    }

    private void onArrived() {
        stopFollowing();
        binding.tvGuidance.setText("🎉 You have arrived!");
        binding.tvDistance.setText("Destination reached");

        // Vibrate
        android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) v.vibrate(android.os.VibrationEffect.createOneShot(500,
                android.os.VibrationEffect.DEFAULT_AMPLITUDE));

        Toast.makeText(this, "You have arrived at the destination!", Toast.LENGTH_LONG).show();
    }
}
