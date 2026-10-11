#!/usr/bin/env python3
"""Dedicated fixture MySQL only; never contacts the public provider or Gemini."""
import json, os, subprocess, sys, time, urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(sys.argv[1]).resolve(); OUT.mkdir(parents=True,exist_ok=False)
sql="""
INSERT IGNORE INTO hospital(hpid,duty_name,telephone,emergency_room_open,active,imported_at)
VALUES('TEST3','Missing beds','02-fixture','1',TRUE,NOW());
UPDATE hospital SET emergency_room_open='1', telephone='02-fixture', active=TRUE WHERE hpid IN('TEST1','TEST2','TEST3');
INSERT INTO hospital_location(hpid,location)
VALUES('TEST1',ST_GeomFromText('POINT(126.978 37.5665)',4326,'axis-order=long-lat')),
('TEST2',ST_GeomFromText('POINT(126.979 37.5665)',4326,'axis-order=long-lat')),
('TEST3',ST_GeomFromText('POINT(126.980 37.5665)',4326,'axis-order=long-lat'))
ON DUPLICATE KEY UPDATE location=VALUES(location);
INSERT IGNORE INTO hospital_department(hpid,department_code) VALUES('TEST1','D001'),('TEST2','D001'),('TEST3','D001');
UPDATE hospital_bed_status SET hvec=0, fetched_at=NOW() WHERE hpid='TEST1';
UPDATE hospital_bed_status SET fetched_at=NOW()-INTERVAL 7 MINUTE WHERE hpid='TEST2';
"""
subprocess.run(['docker','exec','-i','-e','MYSQL_PWD=local-verification','curelingo-original-verification','mysql','-uroot','curelingo'],input=sql,text=True,check=True)
env=os.environ.copy();env.update(SPRING_PROFILES_ACTIVE='contract-verification',SERVER_PORT='18080', MANAGEMENT_SERVER_PORT='19464',SPRING_DATASOURCE_URL='jdbc:mysql://localhost:13307/curelingo?serverTimezone=UTC',SPRING_DATASOURCE_USERNAME='root',SPRING_DATASOURCE_PASSWORD='local-verification',CURELINGO_BED_REFRESH_ENABLED='false',EGEN_API_KEY='fixture-only',GEMINI_API_KEY='fixture-only',GEMINI_API_URL='http://127.0.0.1:18090',GOOGLE_TRANSLATION_API_KEY='fixture-only')
with (OUT/'app.log').open('w') as log:
 app=subprocess.Popen(['java','-jar',str(ROOT/'build/libs/curelingo-0.0.1-SNAPSHOT.jar')],env=env,stdout=log,stderr=log)
 try:
  for _ in range(60):
   try:
    data=json.load(urllib.request.urlopen('http://127.0.0.1:18080/api/emergency/nearby?lat=37.5665&lon=126.978',timeout=2));break
   except Exception:
    if app.poll() is not None: raise RuntimeError('Fixture app exited; see app.log')
    time.sleep(1)
  else: raise RuntimeError('Fixture API did not become ready')
  rows={x['hpid']:x for x in data}
  assert rows['TEST1']['beds']['hvec']==0 and rows['TEST1']['observationStatus']=='RECENTLY_FETCHED'
  assert rows['TEST2']['observationStatus']=='STALE'
  assert rows['TEST3']['beds'] is None and rows['TEST3']['observationStatus']=='NOT_AVAILABLE'
  assert rows['TEST3']['telephone']=='02-fixture'
  assert rows['TEST1']['fetchedAt'] and rows['TEST1']['sourceUpdatedAt']=='2026-10-04T09:00:00Z'
  assert all('lat' in x and 'lng' in x for x in data)
  clinics=json.load(urllib.request.urlopen('http://127.0.0.1:18080/api/clinic/department?lat=37.5665&lng=126.978&department=D001',timeout=5))
  assert len(clinics)==3 and len(clinics)<=50
  assert [x['hpid'] for x in clinics]==['TEST1','TEST2','TEST3']
  assert clinics[0]['availableGeneralBeds']==0
  (OUT/'clinic-response.json').write_text(json.dumps(clinics,ensure_ascii=False,indent=2))
  (OUT/'response.json').write_text(json.dumps(data,ensure_ascii=False,indent=2))
  (OUT/'result.json').write_text(json.dumps({'passed':True,'returnedHospitalCount':len(data),'publicApiCalls':0,'clinicSearchPassed':True,'states':['RECENTLY_FETCHED','STALE','NOT_AVAILABLE']}))
  print((OUT/'result.json').read_text())
 finally:
  app.terminate();app.wait(timeout=15)
