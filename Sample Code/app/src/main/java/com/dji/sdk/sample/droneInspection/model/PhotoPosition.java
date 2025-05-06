package com.dji.sdk.sample.droneInspection.model;

/**
 * Represents a photo position relative to an inspection point
 */
public class PhotoPosition {
    private double relativeX;      // Relative X position (meters), positive is East
    private double relativeY;      // Relative Y position (meters), positive is North
    private double relativeZ;      // Relative Z position (meters), positive is Up
    private double cameraPitch;    // Camera pitch angle (degrees), negative is downward
    private double cameraYaw;      // Camera yaw angle (degrees), 0 is North
    private boolean isCaptured;    // Whether a photo has been taken at this position

    public PhotoPosition(double relativeX, double relativeY, double relativeZ, double cameraPitch, double cameraYaw) {
        this.relativeX = relativeX;
        this.relativeY = relativeY;
        this.relativeZ = relativeZ;
        this.cameraPitch = cameraPitch;
        this.cameraYaw = cameraYaw;
        this.isCaptured = false;
    }

    public double getRelativeX() {
        return relativeX;
    }

    public double getRelativeY() {
        return relativeY;
    }

    public double getRelativeZ() {
        return relativeZ;
    }

    public double getCameraPitch() {
        return cameraPitch;
    }

    public double getCameraYaw() {
        return cameraYaw;
    }

    public boolean isCaptured() {
        return isCaptured;
    }

    public void setCaptured(boolean captured) {
        isCaptured = captured;
    }
}