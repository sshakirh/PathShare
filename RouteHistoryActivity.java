package com.pathshare.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pathshare.app.R;
import com.pathshare.app.databinding.ActivityRouteHistoryBinding;
import com.pathshare.app.models.Route;
import com.pathshare.app.utils.RouteRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shows all previously saved routes; tap one to replay / share it.
 */
public class RouteHistoryActivity extends AppCompatActivity {

    private ActivityRouteHistoryBinding binding;
    private RouteRepository repository;
    private List<Route> routes;
    private RouteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding    = ActivityRouteHistoryBinding.inflate(getLayoutInflater());
        repository = new RouteRepository(this);
        setContentView(binding.getRoot());

        binding.rvRoutes.setLayoutManager(new LinearLayoutManager(this));
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        routes  = repository.getAllRoutes();
        adapter = new RouteAdapter(routes);
        binding.rvRoutes.setAdapter(adapter);
        binding.tvEmpty.setVisibility(routes.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── Adapter ──────────────────────────────────

    private class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.VH> {
        final List<Route> data;
        RouteAdapter(List<Route> d) { this.data = d; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_route, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Route r = data.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

            h.tvName.setText(r.name != null ? r.name : "Unnamed Route");
            h.tvDate.setText(sdf.format(new Date(r.startTime)));
            h.tvStats.setText(String.format(Locale.getDefault(),
                    "%.2f km  •  %ds  •  avg %.1f km/h",
                    r.totalDistanceMetres / 1000.0,
                    r.getDurationSeconds(),
                    r.getAverageSpeedKmh()));
            h.tvShared.setVisibility(r.isShared ? View.VISIBLE : View.GONE);
            if (r.isShared) h.tvShared.setText("Code: " + r.shareCode);

            h.itemView.setOnClickListener(v -> showRouteOptions(r));
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvDate, tvStats, tvShared;
            VH(@NonNull View v) {
                super(v);
                tvName   = v.findViewById(R.id.tv_route_name);
                tvDate   = v.findViewById(R.id.tv_route_date);
                tvStats  = v.findViewById(R.id.tv_route_stats);
                tvShared = v.findViewById(R.id.tv_route_shared);
            }
        }
    }

    // ── Route options dialog ─────────────────────

    private void showRouteOptions(Route route) {
        String[] options = {"▶ Replay / Follow", "📤 Share Route", "🗑 Delete"};
        new AlertDialog.Builder(this)
                .setTitle(route.name)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: openPlayback(route); break;
                        case 1: shareRoute(route);   break;
                        case 2: deleteRoute(route);  break;
                    }
                }).show();
    }

    private void openPlayback(Route route) {
        Intent i = new Intent(this, RoutePlaybackActivity.class);
        i.putExtra(RoutePlaybackActivity.EXTRA_ROUTE_ID, route.id);
        startActivity(i);
    }

    private void shareRoute(Route route) {
        if (route.isShared && route.shareCode != null) {
            sendShareIntent(route.shareCode);
            return;
        }
        repository.shareRoute(route, new RouteRepository.Callback<String>() {
            @Override public void onSuccess(String code) {
                runOnUiThread(() -> {
                    refresh();
                    sendShareIntent(code);
                });
            }
            @Override public void onError(Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(RouteHistoryActivity.this)
                        .setTitle("Share Failed")
                        .setMessage(e.getMessage())
                        .setPositiveButton("OK", null).show());
            }
        });
    }

    private void sendShareIntent(String code) {
        String text = "Follow my route on PathShare!\nCode: " + code
                + "\nOr tap: pathshare://route?code=" + code;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, "Share Route"));
    }

    private void deleteRoute(Route route) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Route?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    repository.deleteRoute(route.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null).show();
    }
}
