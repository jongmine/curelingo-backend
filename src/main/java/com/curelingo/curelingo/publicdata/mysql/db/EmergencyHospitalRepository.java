package com.curelingo.curelingo.publicdata.mysql.db;

import com.curelingo.curelingo.emergencyhospital.dto.EmergencyBedStatus;
import com.curelingo.curelingo.publicdata.mysql.api.LocationBounds;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EmergencyHospitalRepository {
    private final java.time.Duration staleAfter;

    private final NamedParameterJdbcTemplate jdbc;

    public EmergencyHospitalRepository(NamedParameterJdbcTemplate jdbc,
            @org.springframework.beans.factory.annotation.Value("${curelingo.bed-refresh.stale-after:6m}") java.time.Duration staleAfter) {
        this.jdbc = jdbc;
        this.staleAfter = staleAfter;
    }

    public List<NearbyEmergencyHospital> findNearbyEmergencyHospitals(double latitude, double longitude,
                                                                       int radiusMeters) {
        MapSqlParameterSource parameters = parameters(latitude, longitude, radiusMeters);
        String bounds = boundsPredicate(parameters, latitude, longitude, radiusMeters);
        String sql = """
                SELECT h.hpid, h.duty_name, h.duty_name_en, h.duty_address, h.duty_address_en,
                       COALESCE(NULLIF(h.emergency_telephone, ''), h.telephone) AS telephone,
                       ST_Latitude(l.location) AS latitude, ST_Longitude(l.location) AS longitude,
                       ST_Distance_Sphere(l.location, ST_GeomFromText(
                           CONCAT('POINT(', :longitude, ' ', :latitude, ')'), 4326,
                           'axis-order=long-lat')) AS distance_meters,
                       b.source_updated_at, b.fetched_at, b.hvec, b.hvs01, b.hv28, b.hvs02, b.hvoc, b.hvs22, b.hvgc, b.hvs38, b.hv9, b.hvs14, b.hv38, b.hvs21, b.hv60, b.hvs60
                  FROM hospital h FORCE INDEX (idx_hospital_emergency_active)
                  STRAIGHT_JOIN hospital_location l ON l.hpid = h.hpid
                  LEFT JOIN hospital_bed_status b ON b.hpid = h.hpid
                 WHERE h.active = TRUE AND h.emergency_room_open = '1' AND %s
                HAVING distance_meters <= :radiusMeters
                 ORDER BY distance_meters, h.hpid LIMIT 100
                """.formatted(bounds);
        java.time.Instant now = java.time.Instant.now();
        return jdbc.query(sql, parameters, (rs, rowNum) -> new NearbyEmergencyHospital(
                rs.getString("hpid"), rs.getString("duty_name"), rs.getString("duty_name_en"),
                rs.getString("duty_address"), rs.getString("duty_address_en"), rs.getString("telephone"),
                rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getDouble("distance_meters") / 1000.0,
                bedStatus(rs, now)));
    }

    public List<EmergencyBedStatus> findNearbyEmergencyBeds(double latitude, double longitude, int radiusMeters) {
        return findNearbyEmergencyHospitals(latitude, longitude, radiusMeters).stream()
                .map(NearbyEmergencyHospital::bedStatus).toList();
    }

    private EmergencyBedStatus bedStatus(java.sql.ResultSet rs, java.time.Instant now) throws java.sql.SQLException {
        EmergencyBedStatus status = new EmergencyBedStatus();
        status.setHpid(rs.getString("hpid"));
        status.setDutyName(rs.getString("duty_name"));
        status.setDutyTel3(rs.getString("telephone"));
        status.setDistanceKm(rs.getDouble("distance_meters") / 1000.0);
        java.time.LocalDateTime source = rs.getObject("source_updated_at", java.time.LocalDateTime.class);
        status.setUpdatedAt(source); // Legacy field keeps the provider's Korean local time.
        java.time.Instant sourceInstant = source == null ? null : source.atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant();
        java.sql.Timestamp fetched = rs.getTimestamp("fetched_at");
        status.setSourceUpdatedAt(sourceInstant);
        status.setFetchedAt(fetched == null ? null : fetched.toInstant());
        status.setSourceAgeSeconds(sourceInstant == null ? null : java.time.Duration.between(sourceInstant, now).getSeconds());
        status.setObservationStatus(fetched == null || rs.getObject("hvec") == null ? "NOT_AVAILABLE"
                : fetched.toInstant().isBefore(now.minus(staleAfter)) ? "STALE" : "RECENTLY_FETCHED");
        if (fetched != null) {
            EmergencyBedStatus.Beds beds = new EmergencyBedStatus.Beds();
            beds.setHvec(integer(rs, "hvec"));
            beds.setHvs01(integer(rs, "hvs01"));
            beds.setHv28(integer(rs, "hv28"));
            beds.setHvs02(integer(rs, "hvs02"));
            beds.setHvoc(integer(rs, "hvoc"));
            beds.setHvs22(integer(rs, "hvs22"));
            beds.setHvgc(integer(rs, "hvgc"));
            beds.setHvs38(integer(rs, "hvs38"));
            beds.setHv9(integer(rs, "hv9"));
            beds.setHvs14(integer(rs, "hvs14"));
            beds.setHv38(integer(rs, "hv38"));
            beds.setHvs21(integer(rs, "hvs21"));
            beds.setHv60(integer(rs, "hv60"));
            beds.setHvs60(integer(rs, "hvs60"));
            status.setBeds(beds);
        }
        return status;
    }

    private static Integer integer(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static MapSqlParameterSource parameters(double latitude, double longitude, int radiusMeters) {
        return new MapSqlParameterSource()
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("radiusMeters", radiusMeters);
    }

    private static String boundsPredicate(MapSqlParameterSource parameters, double latitude, double longitude,
                                          int radiusMeters) {
        List<String> polygons = LocationBounds.polygons(latitude, longitude, radiusMeters);
        StringBuilder predicate = new StringBuilder("(");
        for (int i = 0; i < polygons.size(); i++) {
            if (i > 0) predicate.append(" OR ");
            String name = "bounds" + i;
            predicate.append("MBRContains(ST_GeomFromText(:").append(name)
                    .append(", 4326, 'axis-order=long-lat'), l.location)");
            parameters.addValue(name, polygons.get(i));
        }
        return predicate.append(')').toString();
    }
}
