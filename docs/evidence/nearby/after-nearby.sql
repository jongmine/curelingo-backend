SET SESSION max_execution_time = 15000;
EXPLAIN ANALYZE
WITH department_candidates AS (
    SELECT l.hpid,
           l.location,
           ST_Distance_Sphere(l.location, ST_GeomFromText('POINT(126.9780 37.5665)', 4326, 'axis-order=long-lat')) AS distance_meters
      FROM hospital_location l FORCE INDEX (idx_hospital_location)
      STRAIGHT_JOIN hospital_department hd
        ON hd.hpid = l.hpid
       AND hd.department_code = 'D001'
     WHERE MBRContains(ST_GeomFromText('POLYGON((126.94394304 37.53951466, 127.01205696 37.53951466, 127.01205696 37.59348534, 126.94394304 37.59348534, 126.94394304 37.53951466))', 4326, 'axis-order=long-lat'), l.location)
)
SELECT /*+ NO_MERGE(department_candidates)
           NO_DERIVED_CONDITION_PUSHDOWN(department_candidates) */
       h.hpid,
       h.duty_name,
       h.duty_address,
       h.hospital_type,
       ST_Latitude(department_candidates.location) AS latitude,
       ST_Longitude(department_candidates.location) AS longitude,
       department_candidates.distance_meters,
       b.available_general_beds,
       b.total_general_beds,
       b.available_pediatric_beds,
       b.source_updated_at AS bed_source_updated_at,
       b.fetched_at AS bed_fetched_at
  FROM department_candidates
  STRAIGHT_JOIN hospital h ON h.hpid = department_candidates.hpid
  LEFT JOIN hospital_bed_status b ON b.hpid = h.hpid
 WHERE h.active = TRUE
   AND department_candidates.distance_meters <= 3000
 ORDER BY department_candidates.distance_meters, h.hpid
 LIMIT 50;
