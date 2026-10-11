
                SELECT h.hpid, h.duty_name, h.duty_address,
                       COALESCE(NULLIF(h.emergency_telephone,''),h.telephone) AS telephone,
                       ST_Latitude(l.location) AS latitude, ST_Longitude(l.location) AS longitude,
                       ST_Distance_Sphere(l.location, ST_GeomFromText(
                           CONCAT('POINT(', 126.978,' ',37.5665,')'),4326,'axis-order=long-lat')) AS distance_meters,
                       b.available_general_beds, b.source_updated_at, b.fetched_at
                  FROM hospital_location l JOIN hospital h ON h.hpid=l.hpid
                  LEFT JOIN hospital_bed_status b ON b.hpid=h.hpid
                 WHERE h.active=TRUE AND h.emergency_room_open='1' AND (MBRContains(ST_GeomFromText('POLYGON((126.86439747 37.47656223,127.09160253 37.47656223,127.09160253 37.65643777,126.86439747 37.65643777,126.86439747 37.47656223))',4326,'axis-order=long-lat'),l.location))
                HAVING distance_meters <= 10000
                 ORDER BY distance_meters,h.hpid LIMIT 100
                ;
