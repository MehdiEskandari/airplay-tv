#!/usr/bin/env python3
"""Serve only the widget catalog/package to one TV; drop root after binding 80."""
from pathlib import Path
import argparse,http.server,ipaddress,json,os,threading,time,urllib.parse

PLATFORM=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--host',required=True);parser.add_argument('--tv',required=True);parser.add_argument('--port',type=int,default=80);parser.add_argument('--duration',type=int,default=1800)
args=parser.parse_args();ipaddress.ip_address(args.host);ipaddress.ip_address(args.tv)
output=PLATFORM/'output';catalog=output/'widgetlist.xml';archive=output/'AirPlayTVLegacy_0.1.0.zip'
if not catalog.is_file() or not archive.is_file():raise SystemExit('Build the widget and start the companion first.')
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*unused):pass
 def do_GET(self):
  path=urllib.parse.urlsplit(self.path).path
  if self.client_address[0] not in [args.tv,args.host,'127.0.0.1']:self.send_error(403);return
  if path in ['/widgetlist.xml','/widgetlist.xml/']:file=catalog;mime='text/xml; charset=utf-8'
  elif path=='/widget/'+archive.name:file=archive;mime='application/zip'
  else:self.send_error(404);return
  content=file.read_bytes();self.send_response(200);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(content)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(content)
  with (PLATFORM/'.cache/installation.ndjson').open('a') as log:log.write(json.dumps({'time':time.time(),'path':path,'bytes':len(content),'source':self.client_address[0]})+'\n')
try:server=http.server.ThreadingHTTPServer((args.host,args.port),Handler)
except PermissionError:raise SystemExit('macOS requires an administrator to bind port 80. Run this same command with sudo; it drops root immediately after binding.')
if os.getuid()==0 and 'SUDO_UID' in os.environ:
 os.setgroups([]);os.setgid(int(os.environ['SUDO_GID']));os.setuid(int(os.environ['SUDO_UID']))
timer=threading.Timer(args.duration,server.shutdown);timer.daemon=True;timer.start()
print(f'Legacy installer listening at {args.host}:{args.port}; TV {args.tv}; uid {os.getuid()}; stops after {args.duration} seconds.',flush=True)
try:server.serve_forever()
except KeyboardInterrupt:pass
finally:timer.cancel();server.server_close()
