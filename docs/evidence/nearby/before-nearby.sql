SET SESSION max_execution_time = 15000;
EXPLAIN ANALYZE
SELECT h.hpid,
       h.duty_name,
       h.duty_address,
       h.hospital_type,
       ST_Latitude(l.location) AS latitude,
       ST_Longitude(l.location) AS longitude,
       ST_Distance_Sphere(l.location, ST_GeomFromText('POINT(126.9780 37.5665)', 4326, 'axis-order=long-lat')) AS distance_meters,
       b.available_general_beds,
       b.total_general_beds,
       b.available_pediatric_beds,
       b.source_updated_at AS bed_source_updated_at,
       b.fetched_at AS bed_fetched_at
  FROM hospital_location l FORCE INDEX (idx_hospital_location)
  STRAIGHT_JOIN hospital h ON h.hpid = l.hpid
  LEFT JOIN hospital_bed_status b ON b.hpid = h.hpid
 WHERE h.active = TRUE
   AND EXISTS (
       SELECT 1
         FROM hospital_department hd
        WHERE hd.hpid = h.hpid
          AND hd.department_code = 'D001'
   )
   AND MBRContains(ST_GeomFromText('POLYGON((126.94394304 37.53951466, 127.01205696 37.53951466, 127.01205696 37.59348534, 126.94394304 37.59348534, 126.94394304 37.53951466))', 4326, 'axis-order=long-lat'), l.location)
   AND ST_Distance_Sphere(l.location, ST_GeomFromText('POINT(126.9780 37.5665)', 4326, 'axis-order=long-lat')) <= 3000
 ORDER BY distance_meters, h.hpid
 LIMIT 50;
