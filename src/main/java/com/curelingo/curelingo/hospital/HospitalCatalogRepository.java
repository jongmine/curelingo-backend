package com.curelingo.curelingo.hospital;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class HospitalCatalogRepository {
    private static final String SAVE_HOSPITAL_SQL = """
            INSERT INTO hospital
                (hpid, duty_name, duty_address, duty_name_en, duty_address_en, duty_etc, rnum,
                 hospital_type, emergency_room_open, telephone, emergency_telephone,
                 duty_time_1s, duty_time_1c, duty_time_2s, duty_time_2c,
                 duty_time_3s, duty_time_3c, duty_time_4s, duty_time_4c,
                 duty_time_5s, duty_time_5c, duty_time_6s, duty_time_6c,
                 duty_time_7s, duty_time_7c, duty_time_8s, duty_time_8c, active, imported_at)
            VALUES
                (:hpid, :name, :address, :nameEn, :addressEn, :etc, :rnum,
                 :type, :emergencyOpen, :telephone, :emergencyTelephone,
                 :time1s, :time1c, :time2s, :time2c, :time3s, :time3c, :time4s, :time4c,
                 :time5s, :time5c, :time6s, :time6c, :time7s, :time7c, :time8s, :time8c,
                 TRUE, UTC_TIMESTAMP(6))
            AS incoming
            ON DUPLICATE KEY UPDATE duty_name = incoming.duty_name,
                duty_address = incoming.duty_address, duty_name_en = incoming.duty_name_en,
                duty_address_en = incoming.duty_address_en, duty_etc = incoming.duty_etc,
                rnum = incoming.rnum, hospital_type = incoming.hospital_type,
                emergency_room_open = incoming.emergency_room_open, telephone = incoming.telephone,
                emergency_telephone = incoming.emergency_telephone,
                duty_time_1s = incoming.duty_time_1s, duty_time_1c = incoming.duty_time_1c,
                duty_time_2s = incoming.duty_time_2s, duty_time_2c = incoming.duty_time_2c,
                duty_time_3s = incoming.duty_time_3s, duty_time_3c = incoming.duty_time_3c,
                duty_time_4s = incoming.duty_time_4s, duty_time_4c = incoming.duty_time_4c,
                duty_time_5s = incoming.duty_time_5s, duty_time_5c = incoming.duty_time_5c,
                duty_time_6s = incoming.duty_time_6s, duty_time_6c = incoming.duty_time_6c,
                duty_time_7s = incoming.duty_time_7s, duty_time_7c = incoming.duty_time_7c,
                duty_time_8s = incoming.duty_time_8s, duty_time_8c = incoming.duty_time_8c,
                active = TRUE, imported_at = UTC_TIMESTAMP(6)
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public HospitalCatalogRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    public void save(HospitalDto dto) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("hpid", dto.getHpid())
                .addValue("name", dto.getDutyName())
                .addValue("address", dto.getDutyAddr())
                .addValue("nameEn", dto.getDutyNameEn())
                .addValue("addressEn", dto.getDutyAddrEn())
                .addValue("etc", dto.getDutyEtc())
                .addValue("rnum", dto.getRnum())
                .addValue("type", dto.getDutyDivNam())
                .addValue("emergencyOpen", dto.getDutyEryn())
                .addValue("telephone", dto.getDutyTel1())
                .addValue("emergencyTelephone", dto.getDutyTel3());
        for (int day = 1; day <= 8; day++) {
            parameters.addValue("time" + day + "s", time(dto, day, true));
            parameters.addValue("time" + day + "c", time(dto, day, false));
        }
        namedJdbc.update(SAVE_HOSPITAL_SQL, parameters);

        if (dto.getWgs84Lat() != null && dto.getWgs84Lon() != null) {
            jdbc.update("""
                    INSERT INTO hospital_location (hpid, location)
                    VALUES (?, ST_GeomFromText(CONCAT('POINT(', ?, ' ', ?, ')'), 4326, 'axis-order=long-lat'))
                    AS incoming
                    ON DUPLICATE KEY UPDATE location = incoming.location
                    """, dto.getHpid(), dto.getWgs84Lon(), dto.getWgs84Lat());
        } else {
            jdbc.update("DELETE FROM hospital_location WHERE hpid = ?", dto.getHpid());
        }

        if (dto.getDepartments() != null && !dto.getDepartments().isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO hospital_department (hpid, department_code)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE department_code = VALUES(department_code)
                    """, dto.getDepartments(), dto.getDepartments().size(),
                    (statement, department) -> {
                        statement.setString(1, dto.getHpid());
                        statement.setString(2, department);
                    });
        }
    }

    public HospitalDto findByHpid(String hpid) {
        List<HospitalDto> rows = jdbc.query("""
                SELECT h.hpid, h.duty_name, h.duty_address, h.hospital_type, h.emergency_room_open,
                       h.telephone, h.emergency_telephone, h.duty_etc, h.duty_name_en, h.duty_address_en,
                       h.rnum, h.duty_time_1s, h.duty_time_1c, h.duty_time_2s, h.duty_time_2c,
                       h.duty_time_3s, h.duty_time_3c, h.duty_time_4s, h.duty_time_4c,
                       h.duty_time_5s, h.duty_time_5c, h.duty_time_6s, h.duty_time_6c,
                       h.duty_time_7s, h.duty_time_7c, h.duty_time_8s, h.duty_time_8c,
                       ST_Latitude(l.location) AS latitude, ST_Longitude(l.location) AS longitude
                  FROM hospital h LEFT JOIN hospital_location l ON l.hpid = h.hpid
                 WHERE h.hpid = ? AND h.active = TRUE
                """, (rs, rowNum) -> HospitalDto.builder()
                .hpid(rs.getString("hpid"))
                .dutyName(rs.getString("duty_name"))
                .dutyAddr(rs.getString("duty_address"))
                .dutyDivNam(rs.getString("hospital_type"))
                .dutyEryn(rs.getString("emergency_room_open"))
                .dutyTel1(rs.getString("telephone"))
                .dutyTel3(rs.getString("emergency_telephone"))
                .dutyEtc(rs.getString("duty_etc"))
                .dutyNameEn(rs.getString("duty_name_en"))
                .dutyAddrEn(rs.getString("duty_address_en"))
                .rnum(rs.getString("rnum"))
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
                .wgs84Lat(rs.getObject("latitude", Double.class))
                .wgs84Lon(rs.getObject("longitude", Double.class))
                .build(), hpid);
        if (rows.isEmpty()) return null;
        HospitalDto result = rows.get(0);
        result.setDepartments(namedJdbc.query("SELECT department_code FROM hospital_department WHERE hpid = :hpid ORDER BY department_code",
                new MapSqlParameterSource("hpid", hpid), (rs, rowNum) -> rs.getString(1)));
        return result;
    }

    private static String time(HospitalDto dto, int day, boolean start) {
        return switch (day) {
            case 1 -> start ? dto.getDutyTime1s() : dto.getDutyTime1c();
            case 2 -> start ? dto.getDutyTime2s() : dto.getDutyTime2c();
            case 3 -> start ? dto.getDutyTime3s() : dto.getDutyTime3c();
            case 4 -> start ? dto.getDutyTime4s() : dto.getDutyTime4c();
            case 5 -> start ? dto.getDutyTime5s() : dto.getDutyTime5c();
            case 6 -> start ? dto.getDutyTime6s() : dto.getDutyTime6c();
            case 7 -> start ? dto.getDutyTime7s() : dto.getDutyTime7c();
            case 8 -> start ? dto.getDutyTime8s() : dto.getDutyTime8c();
            default -> throw new IllegalArgumentException("Unsupported day: " + day);
        };
    }
}
