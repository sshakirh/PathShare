package com.pathshare.app.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A complete recorded route consisting of ordered GPS waypoints.
 */
public class Route {
    public String id;
    public String name;
    public long startTime;
    public long endTime;
    public List<RoutePoint> points;
    public double totalDistanceMetres;
    public String shareCode;        // short code stored in Firebase
    public boolean isShared;

    public Route() {
        this.id     = UUID.randomUUID().toString();
        this.points = new ArrayList<>();
    }

    public Route(String name) {
        this();
        this.name      = name;
        this.startTime = System.currentTimeMillis();
    }

    /** Append a new point and update total distance. */
    public void addPoint(RoutePoint point) {
        if (!points.isEmpty()) {
            totalDistanceMetres += points.get(points.size() - 1).distanceTo(point);
        }
        points.add(point);
    }

    /** Finish recording. */
    public void finish() {
        this.endTime = System.currentTimeMillis();
    }

    /** Duration in seconds. */
    public long getDurationSeconds() {
        long end = (endTime > 0) ? endTime : System.currentTimeMillis();
        return (end - startTime) / 1000;
    }

    /** Average speed in km/h. */
    public double getAverageSpeedKmh() {
        long dur = getDurationSeconds();
        if (dur == 0) return 0;
        return (totalDistanceMetres / 1000.0) / (dur / 3600.0);
    }

    public RoutePoint getStartPoint() {
        return points.isEmpty() ? null : points.get(0);
    }

    public RoutePoint getEndPoint() {
        return points.isEmpty() ? null : points.get(points.size() - 1);
    }
}
