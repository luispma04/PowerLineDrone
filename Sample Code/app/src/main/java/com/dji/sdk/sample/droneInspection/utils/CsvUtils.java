package com.dji.sdk.sample.droneInspection.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.dji.sdk.sample.droneInspection.model.InspectionPoint;
import com.dji.sdk.sample.droneInspection.model.PhotoPosition;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing CSV files
 */
public class CsvUtils {
    private static final String TAG = CsvUtils.class.getName();

    /**
     * Parse a CSV file containing inspection points
     * @param context The application context
     * @param uri The URI of the CSV file
     * @return A list of InspectionPoint objects
     */
    public static List<InspectionPoint> parseInspectionPointsCsv(Context context, Uri uri) {
        List<InspectionPoint> inspectionPoints = new ArrayList<>();

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            // Skip header line
            String line = reader.readLine();

            // Read data lines
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length >= 4) {
                    try {
                        double latitude = Double.parseDouble(values[0]);
                        double longitude = Double.parseDouble(values[1]);
                        double groundAltitude = Double.parseDouble(values[2]);
                        double structureHeight = Double.parseDouble(values[3]);

                        inspectionPoints.add(new InspectionPoint(latitude, longitude, groundAltitude, structureHeight));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing inspection point values: " + e.getMessage());
                    }
                }
            }

            reader.close();
            inputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Error reading inspection points CSV: " + e.getMessage());
        }

        return inspectionPoints;
    }

    /**
     * Parse a CSV file containing photo positions
     * @param context The application context
     * @param uri The URI of the CSV file
     * @return A list of PhotoPosition objects
     */
    public static List<PhotoPosition> parsePhotoPositionsCsv(Context context, Uri uri) {
        List<PhotoPosition> photoPositions = new ArrayList<>();

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            // Skip header line
            String line = reader.readLine();

            // Read data lines
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length >= 5) {
                    try {
                        double relativeX = Double.parseDouble(values[0]);
                        double relativeY = Double.parseDouble(values[1]);
                        double relativeZ = Double.parseDouble(values[2]);
                        double cameraPitch = Double.parseDouble(values[3]);
                        double cameraYaw = Double.parseDouble(values[4]);

                        photoPositions.add(new PhotoPosition(relativeX, relativeY, relativeZ, cameraPitch, cameraYaw));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing photo position values: " + e.getMessage());
                    }
                }
            }

            reader.close();
            inputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Error reading photo positions CSV: " + e.getMessage());
        }

        return photoPositions;
    }
}
