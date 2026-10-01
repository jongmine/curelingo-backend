package com.curelingo.curelingo.publicdata.mysql.api;

import java.util.List;

public final class LocationBounds {
    private static final double EARTH_RADIUS_METERS = 6_371_008.7714;

    private LocationBounds() {
    }

    public static List<String> polygons(double latitude, double longitude, double radiusMeters) {
        double angularRadius = radiusMeters / EARTH_RADIUS_METERS + 1e-7;
        double latitudeRadians = Math.toRadians(latitude);
        double latitudeDelta = Math.toDegrees(angularRadius);
        double minLatitude = Math.max(-90.0, latitude - latitudeDelta);
        double maxLatitude = Math.min(90.0, latitude + latitudeDelta);

        double longitudeDelta;
        if (minLatitude <= -90.0 || maxLatitude >= 90.0) {
            longitudeDelta = 180.0;
        } else {
            double farthestLatitude = Math.max(Math.abs(minLatitude), Math.abs(maxLatitude));
            double ratio = Math.sin(angularRadius) / Math.cos(Math.toRadians(farthestLatitude));
            longitudeDelta = Math.toDegrees(Math.asin(Math.min(1.0, ratio)));
        }

        double minLongitude = longitude - longitudeDelta;
        double maxLongitude = longitude + longitudeDelta;
        if (longitudeDelta >= 180.0) {
            return List.of(polygon(-180.0, minLatitude, 180.0, maxLatitude));
        }
        if (minLongitude < -180.0) {
            return List.of(
                    polygon(-180.0, minLatitude, maxLongitude, maxLatitude),
                    polygon(minLongitude + 360.0, minLatitude, 180.0, maxLatitude)
            );
        }
        if (maxLongitude > 180.0) {
            return List.of(
                    polygon(minLongitude, minLatitude, 180.0, maxLatitude),
                    polygon(-180.0, minLatitude, maxLongitude - 360.0, maxLatitude)
            );
        }
        return List.of(polygon(minLongitude, minLatitude, maxLongitude, maxLatitude));
    }

    private static String polygon(double minLongitude, double minLatitude, double maxLongitude, double maxLatitude) {
        return "POLYGON((" + point(minLongitude, minLatitude) + "," + point(maxLongitude, minLatitude) + ","
                + point(maxLongitude, maxLatitude) + "," + point(minLongitude, maxLatitude) + ","
                + point(minLongitude, minLatitude) + "))";
    }

    private static String point(double longitude, double latitude) {
        return String.format(java.util.Locale.ROOT, "%.8f %.8f", longitude, latitude);
    }
}
