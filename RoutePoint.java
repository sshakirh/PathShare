package com.pathshare.app.models;

/**
 * Represents a single GPS point in a recorded route.
 */
public class RoutePoint {
    public double latitude;
    public double longitude;
    public float accuracy;       // meters
    public float speed;          // m/s
    public float bearing;        // degrees
    public long timestamp;       // Unix millis
    public double altitude;      // meters

    public RoutePoint() {}

    public RoutePoint(double latitude, double longitude, float accuracy,
                      float speed, float bearing, long timestamp, double altitude) {
        this.latitude  = latitude;
        this.longitude = longitude;
        this.accuracy  = accuracy;
        this.speed     = speed;
        this.bearing   = bearing;
        this.timestamp = timestamp;
        this.altitude  = altitude;
    }

    /** Distance in metres to another point (Haversine). */
    public double distanceTo(RoutePoint other) {
        double R = 6371000;
        double dLat = Math.toRadians(other.latitude  - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(this.latitude))
                 * Math.cos(Math.toRadians(other.latitude))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
