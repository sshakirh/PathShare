package com.pathshare.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pathshare.app.models.Route;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages saving / loading routes locally (SharedPreferences) and
 * publishing / fetching them via Firebase Firestore.
 */
public class RouteRepository {

    private static final String TAG     = "RouteRepository";
    private static final String PREFS   = "pathshare_routes";
    private static final String KEY_ALL = "all_routes";
    private static final String CHARS   = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private FirebaseFirestore db;

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public RouteRepository(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            db = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            Log.w(TAG, "Firebase not configured – sharing disabled");
        }
    }

    // ──────────────────────────────────────────────
    //  Local persistence
    // ──────────────────────────────────────────────

    public void saveRoute(Route route) {
        List<Route> all = getAllRoutes();
        // Replace existing or add new
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(route.id)) {
                all.set(i, route);
                found = true;
                break;
            }
        }
        if (!found) all.add(route);
        prefs.edit().putString(KEY_ALL, gson.toJson(all)).apply();
    }

    public List<Route> getAllRoutes() {
        String json = prefs.getString(KEY_ALL, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<Route>>() {}.getType();
        List<Route> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public Route getRouteById(String id) {
        for (Route r : getAllRoutes()) {
            if (r.id.equals(id)) return r;
        }
        return null;
    }

    public void deleteRoute(String id) {
        List<Route> all = getAllRoutes();
        all.removeIf(r -> r.id.equals(id));
        prefs.edit().putString(KEY_ALL, gson.toJson(all)).apply();
    }

    // ──────────────────────────────────────────────
    //  Firebase sharing
    // ──────────────────────────────────────────────

    /**
     * Upload route to Firestore and return a 6-char share code.
     */
    public void shareRoute(Route route, Callback<String> callback) {
        if (db == null) {
            callback.onError(new Exception("Firebase not available"));
            return;
        }

        String code = generateShareCode();
        route.shareCode = code;
        route.isShared  = true;

        db.collection("shared_routes")
          .document(code)
          .set(routeToMap(route))
          .addOnSuccessListener(unused -> {
              saveRoute(route);          // persist updated share status
              callback.onSuccess(code);
          })
          .addOnFailureListener(callback::onError);
    }

    /**
     * Fetch a shared route from Firestore using the 6-char code.
     */
    public void fetchSharedRoute(String code, Callback<Route> callback) {
        if (db == null) {
            callback.onError(new Exception("Firebase not available"));
            return;
        }

        db.collection("shared_routes")
          .document(code.toUpperCase())
          .get()
          .addOnSuccessListener(doc -> {
              if (doc.exists()) {
                  String json = gson.toJson(doc.getData());
                  Route route = gson.fromJson(json, Route.class);
                  callback.onSuccess(route);
              } else {
                  callback.onError(new Exception("Route not found for code: " + code));
              }
          })
          .addOnFailureListener(callback::onError);
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private String generateShareCode() {
        Random rng = new Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(CHARS.charAt(rng.nextInt(CHARS.length())));
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> routeToMap(Route route) {
        // Serialise via Gson → Map so Firestore can store it
        String json = gson.toJson(route);
        Type type = new TypeToken<java.util.Map<String, Object>>() {}.getType();
        return gson.fromJson(json, type);
    }
}
