package com.curelingo.curelingo.publicdata.mysql.db;

import com.curelingo.curelingo.clinic.domain.Clinic;
import com.curelingo.curelingo.publicdata.mysql.api.LocationBounds;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class HospitalSearchRepository {
    private static final String QUERY_PREFIX = """
            WITH department_candidates AS (
                SELECT l.hpid,
                       l.location,
                       ST_Distance_Sphere(l.location,
                           ST_GeomFromText(CONCAT('POINT(', :longitude, ' ', :latitude, ')'),
                                           4326, 'axis-order=long-lat')) AS distance_meters
                  FROM hospital_location l FORCE INDEX (idx_hospital_location)
                  STRAIGHT_JOIN hospital_department hd
                    ON hd.hpid = l.hpid AND hd.department_code = :departmentCode
                 WHERE (
            """;

    private static final String QUERY_SUFFIX = """
                       )
            )
            SELECT /*+ NO_MERGE(department_candidates)
                       NO_DERIVED_CONDITION_PUSHDOWN(department_candidates) */
                   h.hpid,
                   h.duty_name,
                   h.duty_address,
                   h.duty_name_en,
                   h.duty_address_en,
                   h.telephone,
                   h.hospital_type,
                   h.duty_time_1s, h.duty_time_1c,
                   h.duty_time_2s, h.duty_time_2c,
                   h.duty_time_3s, h.duty_time_3c,
                   h.duty_time_4s, h.duty_time_4c,
                   h.duty_time_5s, h.duty_time_5c,
                   h.duty_time_6s, h.duty_time_6c,
                   h.duty_time_7s, h.duty_time_7c,
                   h.duty_time_8s, h.duty_time_8c,
                   ST_Latitude(c.location) AS latitude,
                   ST_Longitude(c.location) AS longitude,
                   c.distance_meters
              FROM department_candidates c
              STRAIGHT_JOIN hospital h ON h.hpid = c.hpid
             WHERE h.active = TRUE
               AND c.distance_meters <= :radiusMeters
             ORDER BY c.distance_meters, h.hpid
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public HospitalSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<NearbyClinicResult> findNearby(double latitude, double longitude, int radiusMeters,
                                                String departmentCode) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("radiusMeters", radiusMeters)
                .addValue("departmentCode", departmentCode);
        StringBuilder sql = new StringBuilder(QUERY_PREFIX);
        List<String> polygons = LocationBounds.polygons(latitude, longitude, radiusMeters);
        for (int i = 0; i < polygons.size(); i++) {
            if (i > 0) sql.append(" OR ");
            String parameterName = "bounds" + i;
            sql.append("MBRContains(ST_GeomFromText(:")
                    .append(parameterName)
                    .append(", 4326, 'axis-order=long-lat'), l.location)");
            parameters.addValue(parameterName, polygons.get(i));
        }
        sql.append(QUERY_SUFFIX);

        return jdbc.query(sql.toString(), parameters, (rs, rowNum) -> {
            Clinic clinic = Clinic.builder()
                    .hpid(rs.getString("hpid"))
                    .name(rs.getString("duty_name"))
                    .addr(rs.getString("duty_address"))
                    .nameEn(rs.getString("duty_name_en"))
                    .addrEn(rs.getString("duty_address_en"))
                    .tel(rs.getString("telephone"))
                    .type(rs.getString("hospital_type"))
                    .lat(rs.getDouble("latitude"))
                    .lng(rs.getDouble("longitude"))
                    .dutyTime1s(rs.getString("duty_time_1s"))
                    .dutyTime1c(rs.getString("duty_time_1c"))
                    .dutyTime2s(rs.getString("duty_time_2s"))
                    .dutyTime2c(rs.getString("duty_time_2c"))
                    .dutyTime3s(rs.getString("duty_time_3s"))
                    .dutyTime3c(rs.getString("duty_time_3c"))
                    .dutyTime4s(rs.getString("duty_time_4s"))
                    .dutyTime4c(rs.getString("duty_time_4c"))
                    .dutyTime5s(rs.getString("duty_time_5s"))
                    .dutyTime5c(rs.getString("duty_time_5c"))
                    .dutyTime6s(rs.getString("duty_time_6s"))
                    .dutyTime6c(rs.getString("duty_time_6c"))
                    .dutyTime7s(rs.getString("duty_time_7s"))
                    .dutyTime7c(rs.getString("duty_time_7c"))
                    .dutyTime8s(rs.getString("duty_time_8s"))
                    .dutyTime8c(rs.getString("duty_time_8c"))
                    .build();
            return new NearbyClinicResult(clinic, rs.getDouble("distance_meters") / 1000.0,
                    clinic.getNameEn(), clinic.getAddrEn());
        });
    }
}
