#!/usr/bin/env python3
"""Local fault provider. No real E-Gen credentials or outbound requests."""
import argparse,json,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from urllib.parse import urlparse,parse_qs
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--port',type=int,default=18090)
parser.add_argument('--scenario',required=True,help='JSON: pages, optional faults keyed by request number')
args=parser.parse_args()
scenario=json.loads(Path(args.scenario).read_text())
class Handler(BaseHTTPRequestHandler):
    count=0
    def log_message(self,*args): pass # never log credential-bearing URLs
    def do_GET(self):
        Handler.count+=1
        fault=scenario.get('faults',{}).get(str(Handler.count),{})
        page=parse_qs(urlparse(self.path).query).get('pageNo',['1'])[0]
        print(json.dumps({'request':Handler.count,'page':page,'status':fault.get('status',200)}),flush=True)
        time.sleep(fault.get('delay_seconds',0))
        payload=fault.get('body',scenario.get('pages',{}).get(page,{}))
        data=json.dumps(payload).encode()
        self.send_response(fault.get('status',200));self.send_header('Content-Type','application/json')
        self.end_headers()
        try:self.wfile.write(data)
        except (BrokenPipeError,ConnectionResetError):pass
ThreadingHTTPServer(('0.0.0.0',args.port),Handler).serve_forever()
