package com.curelingo.curelingo.publicdata.mysql.db;

public record NearbyEmergencyHospital(
        String hpid,
        String name,
        String nameEn,
        String address,
        String addressEn,
        String telephone,
        double latitude,
        double longitude,
        double distanceKm
) {
}
