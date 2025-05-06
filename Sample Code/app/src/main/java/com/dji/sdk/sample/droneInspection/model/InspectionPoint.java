package com.dji.sdk.sample.droneInspection.model;

/**
 * Represents an inspection point (a structure to be inspected)
 */
public class InspectionPoint {
    private double latitude;
    private double longitude;
    private double groundAltitude;  // Ground altitude at this point (meters)
    private double structureHeight; // Height of the structure (meters)
    private boolean isCompleted;    // Whether this inspection point has been completed

    public InspectionPoint(double latitude, double longitude, double groundAltitude, double structureHeight) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.groundAltitude = groundAltitude;
        this.structureHeight = structureHeight;
        this.isCompleted = false;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getGroundAltitude() {
        return groundAltitude;
    }

    public double getStructureHeight() {
        return structureHeight;
    }

    public double getSafeAltitude() {
        // Safe altitude is the ground altitude plus the structure height plus a safety margin (e.g., 5 meters)
        return groundAltitude + structureHeight + 5.0;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }
}