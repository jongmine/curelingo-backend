#!/usr/bin/env python3
"""Requires dedicated curelingo-original-verification MySQL container; uses dummy provider key."""
import json, os, shutil, subprocess, sys, tempfile, time
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1]).resolve()
OUT.mkdir(parents=True, exist_ok=False)
JAR = OUT / "verified-app.jar"
shutil.copy2(ROOT/"build/libs/curelingo-0.0.1-SNAPSHOT.jar", JAR)
def sql(query):
    p = subprocess.run(['docker','exec','-i','-e','MYSQL_PWD=local-verification',
        'curelingo-original-verification','mysql','-uroot','-N','curelingo'],
        input=query,text=True,capture_output=True,check=True)
    return p.stdout.strip()
sql((ROOT/'src/main/resources/db/mysql-public-data-schema.sql').read_text())
sql("INSERT IGNORE INTO hospital(hpid,duty_name,imported_at) VALUES('TEST1','Fixture',NOW()),('TEST2','Fixture2',NOW())")
def page(items, total=None):
    return {'response': {'header': {'resultCode':'00'}, 'body': {
        'totalCount': len(items) if total is None else total, 'items': {'item':items}}}}
def row(value, stamp='20261004180000', hpid='TEST1'):
    return {'hpid':hpid,'hvec':value,'hvidate':stamp}
cases = [
 ('normal', {'pages': {'1':page([row(7)])}}, True),
 ('retry-503', {'pages': {'1':page([row(8)])},'faults': {'1':{'status':503}}}, True),
 ('duplicate', {'pages': {'1':page([row(9),row(10)])}}, False),
 ('incomplete', {'pages': {'1':page([row(9)],2)}}, False),
 ('old-timestamp', {'pages': {'1':page([row(9,'20261004170000')])}}, False),
 ('unknown-hpid-rollback', {'pages': {'1':page([row(9),row(10,hpid='UNKNOWN')])}}, False),
 ('api-22', {'pages': {'1':{'response':{'header':{'resultCode':'22'}}}}}, False),
 ('empty', {'pages': {'1':page([])}}, False),
 ('two-pages', {'pages': {'1':page([row(11)],2), '2':page([row(12,hpid='TEST2')],2)}}, True),
 ('page-two-error', {'pages': {'1':page([row(13)],2), '2':{'response':{'header':{'resultCode':'22'}}}}}, False),
 ('timeout', {'pages': {'1':page([row(13)])},'faults': {
     str(i):{'delay_seconds':1} for i in range(1,4)}}, False),
 ('api-23-retry', {'pages': {'1':page([row(13)])}, 'faults': {'1':{'body':{'response':{'header':{'resultCode':'23'}}}}}}, True),
 ('missing-hpid', {'pages': {'1':page([{'hvec':3}])}}, False),
 ('invalid-count', {'pages': {'1':page([row('invalid')])}}, False),
 ('invalid-time', {'pages': {'1':page([row(13,'20260230180000')])}}, False),
 ('changed-total', {'pages': {'1':page([row(13)],2), '2':page([row(14,hpid='TEST2')],3)}}, False),
 ('quota-exhausted', {'pages': {'1':page([row(13)])}}, False),
]
results=[]
for name, scenario, success in cases:
    before=sql('SELECT hpid, hvec, source_updated_at, fetched_at FROM hospital_bed_status ORDER BY hpid')
    budget_before=int(sql('SELECT COALESCE(SUM(requests),0) FROM egen_daily_budget'))
    with tempfile.TemporaryDirectory() as tmp:
        path=Path(tmp)/'scenario.json';path.write_text(json.dumps(scenario))
        provider=subprocess.Popen([sys.executable,str(ROOT/'scripts/verification/bed-provider-mock.py'),
            '--port','18090','--scenario',str(path)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        try:
            time.sleep(.2)
            env=os.environ.copy()
            env.update(SPRING_PROFILES_ACTIVE='bed-sync', SPRING_MAIN_WEB_APPLICATION_TYPE='none', EGEN_API_KEY='fixture-only', CURELINGO_PUBLIC_DATA_SYNC_BASE_URL='http://127.0.0.1:18090',
                CURELINGO_PUBLIC_DATA_SYNC_REQUEST_DELAY='0ms',SPRING_DATASOURCE_URL='jdbc:mysql://localhost:13307/curelingo?serverTimezone=UTC',
                SPRING_DATASOURCE_USERNAME='root',SPRING_DATASOURCE_PASSWORD='local-verification')
            env.update(CURELINGO_PUBLIC_DATA_SYNC_READ_TIMEOUT='200ms', CURELINGO_BED_REFRESH_ENABLED='false', GEMINI_API_URL='http://127.0.0.1:18090', GEMINI_API_KEY='fixture-only', GOOGLE_TRANSLATION_API_KEY='fixture-only')
            if name in ('two-pages', 'page-two-error', 'changed-total'): env['CURELINGO_PUBLIC_DATA_SYNC_PAGE_SIZE']='1'
            if name=='quota-exhausted': env['CURELINGO_PUBLIC_DATA_SYNC_DAILY_REQUEST_LIMIT']=str(budget_before)
            run=subprocess.run(['java','-jar',str(JAR)],
                env=env,text=True,capture_output=True,timeout=90)
            (OUT/(name+'.log')).write_text(run.stdout+run.stderr)
        finally:
            provider.terminate();provider.wait(timeout=5)
            provider_log=provider.stdout.read()
            (OUT/(name+'-provider.jsonl')).write_text(provider_log)
    after=sql('SELECT hpid, hvec, source_updated_at, fetched_at FROM hospital_bed_status ORDER BY hpid')
    if success: assert sql("SELECT source_updated_at FROM hospital_bed_status WHERE hpid='TEST1'").startswith('2026-10-04 18:00:00'), (name,'source local time shifted')
    state=sql('SELECT status FROM sync_run ORDER BY id DESC LIMIT 1')
    calls=int(sql('SELECT COALESCE(SUM(requests),0) FROM egen_daily_budget'))-budget_before
    assert run.returncode in (0, 1), (name, 'abnormal process termination', run.returncode)
    assert (run.returncode==0)==success, (name,run.returncode)
    assert state==('SUCCEEDED' if success else 'FAILED'), (name,state)
    if not success: assert before==after, (name,'current rows changed')
    expected_calls=0 if name=='quota-exhausted' else 3 if name=='timeout' else 2 if name in ('retry-503','two-pages','page-two-error','changed-total','api-23-retry') else 1
    assert len(provider_log.strip().splitlines()) == expected_calls if provider_log.strip() else expected_calls == 0
    assert calls==expected_calls, (name,calls)
    results.append(dict(scenario=name,passed=True,status=state,attempts=calls,
        unchanged_on_failure=before==after if not success else None))
    print(json.dumps(results[-1]),flush=True)
(OUT/'results.json').write_text(json.dumps(results,indent=2))
