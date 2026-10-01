package com.curelingo.curelingo.publicdata.mysql.db;

import com.curelingo.curelingo.emergencyhospital.dto.EmergencyBedStatus;
import com.curelingo.curelingo.publicdata.mysql.api.LocationBounds;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EmergencyHospitalRepository {
    private static final String EMERGENCY_FILTER = "h.emergency_room_open = '1'";

    private final NamedParameterJdbcTemplate jdbc;

    public EmergencyHospitalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<NearbyEmergencyHospital> findNearbyEmergencyHospitals(double latitude, double longitude,
                                                                       int radiusMeters) {
        MapSqlParameterSource parameters = parameters(latitude, longitude, radiusMeters);
        String bounds = boundsPredicate(parameters, latitude, longitude, radiusMeters);
        String sql = """
                WITH nearby AS (
                    SELECT l.hpid, l.location,
                           ST_Distance_Sphere(l.location,
                               ST_GeomFromText(CONCAT('POINT(', :longitude, ' ', :latitude, ')'),
                                               4326, 'axis-order=long-lat')) AS distance_meters
                      FROM hospital_location l FORCE INDEX (idx_hospital_location)
                      STRAIGHT_JOIN hospital h ON h.hpid = l.hpid
                     WHERE %s AND %s
                )
                SELECT h.hpid, h.duty_name, h.duty_name_en, h.duty_address, h.duty_address_en,
                       h.telephone, ST_Latitude(n.location) AS latitude,
                       ST_Longitude(n.location) AS longitude, n.distance_meters
                  FROM nearby n STRAIGHT_JOIN hospital h ON h.hpid = n.hpid
                 WHERE n.distance_meters <= :radiusMeters
                 ORDER BY n.distance_meters, h.hpid
                """.formatted(EMERGENCY_FILTER, bounds);
        return jdbc.query(sql, parameters, (rs, rowNum) -> new NearbyEmergencyHospital(
                rs.getString("hpid"), rs.getString("duty_name"), rs.getString("duty_name_en"),
                rs.getString("duty_address"), rs.getString("duty_address_en"), rs.getString("telephone"),
                rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getDouble("distance_meters") / 1000.0));
    }

    public List<EmergencyBedStatus> findNearbyEmergencyBeds(double latitude, double longitude,
                                                            int radiusMeters) {
        MapSqlParameterSource parameters = parameters(latitude, longitude, radiusMeters);
        String bounds = boundsPredicate(parameters, latitude, longitude, radiusMeters);
        String sql = """
                WITH nearby AS (
                    SELECT l.hpid, l.location,
                           ST_Distance_Sphere(l.location,
                               ST_GeomFromText(CONCAT('POINT(', :longitude, ' ', :latitude, ')'),
                                               4326, 'axis-order=long-lat')) AS distance_meters
                      FROM hospital_location l FORCE INDEX (idx_hospital_location)
                      STRAIGHT_JOIN hospital h ON h.hpid = l.hpid
                     WHERE %s AND %s
                )
                SELECT b.hpid, COALESCE(b.duty_name, h.duty_name) AS duty_name,
                       COALESCE(b.duty_tel3, h.emergency_telephone) AS duty_tel3,
                       b.source_updated_at, b.hvec, b.hvs01, b.hv28, b.hvs02, b.hvoc, b.hvs22,
                       b.hvgc, b.hvs38, b.hv9, b.hvs14, b.hv38, b.hvs21, b.hv60, b.hvs60,
                       n.distance_meters
                  FROM nearby n
                  JOIN hospital h ON h.hpid = n.hpid
                  JOIN hospital_bed_status b ON b.hpid = n.hpid
                 WHERE n.distance_meters <= :radiusMeters
                 ORDER BY n.distance_meters, b.hpid
                """.formatted(EMERGENCY_FILTER, bounds);
        return jdbc.query(sql, parameters, (rs, rowNum) -> {
            EmergencyBedStatus status = new EmergencyBedStatus();
            status.setHpid(rs.getString("hpid"));
            status.setDutyName(rs.getString("duty_name"));
            status.setDutyTel3(rs.getString("duty_tel3"));
            status.setDistanceKm(rs.getDouble("distance_meters") / 1000.0);
            status.setUpdatedAt(rs.getObject("source_updated_at", java.time.LocalDateTime.class));

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
            return status;
        });
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
