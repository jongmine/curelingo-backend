package com.curelingo.curelingo.publicdata.mysql.db;

import com.curelingo.curelingo.clinic.domain.Clinic;

public record NearbyClinicResult(Clinic clinic, double distanceKm, String nameEn, String addressEn) {
}
