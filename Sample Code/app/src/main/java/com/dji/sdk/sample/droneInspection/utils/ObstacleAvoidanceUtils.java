package com.dji.sdk.sample.droneInspection.utils;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;

/**
 * Utility class for implementing obstacle avoidance in waypoint missions
 */
public class ObstacleAvoidanceUtils {
    private static final String TAG = ObstacleAvoidanceUtils.class.getName();

    // Minimum distance between waypoints (meters)
    private static final double MIN_WAYPOINT_DISTANCE = 2.0;

    // Maximum distance between waypoints (meters)
    private static final double MAX_WAYPOINT_DISTANCE = 500.0;

    // Default obstacle avoidance height (meters)
    private static final double DEFAULT_AVOIDANCE_HEIGHT = 5.0;

    /**
     * Calculate intermediate waypoints between two points considering a safe height for obstacle avoidance
     *
     * @param startLat Start latitude
     * @param startLng Start longitude
     * @param startAlt Start altitude
     * @param endLat End latitude
     * @param endLng End longitude
     * @param endAlt End altitude
     * @param avoidanceHeight Height to add for obstacle avoidance (meters)
     * @return List of waypoints including intermediate points for obstacle avoidance
     */
    public static List<Waypoint> calculateIntermediateWaypoints(
            double startLat, double startLng, double startAlt,
            double endLat, double endLng, double endAlt,
            double avoidanceHeight) {

        List<Waypoint> waypoints = new ArrayList<>();

        // Calculate distance between points
        double distance = calculateDistance(startLat, startLng, endLat, endLng);

        // If distance is too small, just return the end point
        if (distance < MIN_WAYPOINT_DISTANCE) {
            waypoints.add(new Waypoint(endLat, endLng, (float) endAlt));
            return waypoints;
        }

        // If distance is too large, add intermediate waypoints
        if (distance > MAX_WAYPOINT_DISTANCE) {
            // Calculate number of intermediate points needed
            int numPoints = (int) Math.ceil(distance / MAX_WAYPOINT_DISTANCE);

            // Add intermediate points at safe height
            for (int i = 1; i <= numPoints; i++) {
                double ratio = (double) i / (numPoints + 1);
                double lat = startLat + ratio * (endLat - startLat);
                double lng = startLng + ratio * (endLng - startLng);
                double alt = Math.max(startAlt, endAlt) + avoidanceHeight;

                waypoints.add(new Waypoint(lat, lng, (float) alt));
            }
        } else {
            // For shorter distances, add one intermediate point at safe height
            double lat = (startLat + endLat) / 2;
            double lng = (startLng + endLng) / 2;
            double alt = Math.max(startAlt, endAlt) + avoidanceHeight;

            waypoints.add(new Waypoint(lat, lng, (float) alt));
        }

        // Add the end point
        waypoints.add(new Waypoint(endLat, endLng, (float) endAlt));

        return waypoints;
    }

    /**
     * Overloaded method with default avoidance height
     */
    public static List<Waypoint> calculateIntermediateWaypoints(
            double startLat, double startLng, double startAlt,
            double endLat, double endLng, double endAlt) {

        return calculateIntermediateWaypoints(
                startLat, startLng, startAlt,
                endLat, endLng, endAlt,
                DEFAULT_AVOIDANCE_HEIGHT);
    }

    /**
     * Calculate the distance between two geographic points in meters
     */
    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        // Earth radius in meters
        final double R = 6371000;

        // Convert latitudes and longitudes to radians
        double lat1Rad = Math.toRadians(lat1);
        double lng1Rad = Math.toRadians(lng1);
        double lat2Rad = Math.toRadians(lat2);
        double lng2Rad = Math.toRadians(lng2);

        // Calculate differences
        double dLat = lat2Rad - lat1Rad;
        double dLng = lng2Rad - lng1Rad;

        // Haversine formula
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Check if there's a potential obstacle ahead using vision sensors
     * This method would normally use the DJI SDK's vision sensing APIs
     *
     * @param state The current flight controller state
     * @return true if an obstacle is detected, false otherwise
     */
    public static boolean isObstacleDetected(FlightControllerState state) {
        // This is a placeholder implementation
        // In a real application, you would use the DJI SDK's vision sensing APIs
        // to detect obstacles using the drone's vision sensors

        // Example: Check if vision sensors detect any obstacle within X meters
        // if (state.getVisionSensorState().getDetectionDistance() < X) {
        //     return true;
        // }

        // For now, just return false (no obstacle)
        return false;
    }

    /**
     * Calculate a waypoint to avoid an obstacle
     *
     * @param currentLat Current latitude
     * @param currentLng Current longitude
     * @param currentAlt Current altitude
     * @param state Current flight controller state
     * @return A waypoint to navigate around the obstacle
     */
    public static Waypoint calculateAvoidanceWaypoint(
            double currentLat, double currentLng, double currentAlt,
            FlightControllerState state) {

        // This is a placeholder implementation
        // In a real application, you would use more advanced algorithms to calculate
        // an avoidance path based on the drone's sensor data

        // For now, just increase altitude by the default avoidance height
        return new Waypoint(currentLat, currentLng, (float) (currentAlt + DEFAULT_AVOIDANCE_HEIGHT));
    }
}