#!/usr/bin/env python3
"""Local Mac receiver, HLS relay and diagnostics for a Samsung Legacy widget."""
from pathlib import Path
import argparse,atexit,hmac,http.server,ipaddress,json,mimetypes,os,re,secrets,signal,socket,subprocess,threading,time,urllib.parse,uuid

ROOT=Path(__file__).resolve().parents[3];PLATFORM=ROOT/'platforms/samsung-legacy'
def lan_ip(tv):
 for interface in ['en1','en0','en2']:
  result=subprocess.run(['ifconfig',interface],capture_output=True,text=True)
  match=re.search(r'\binet (\d+\.\d+\.\d+\.\d+) ',result.stdout)
  if match and ipaddress.ip_address(match[1]) in ipaddress.ip_network(tv+'/24',strict=False):return match[1],interface
 raise RuntimeError('No local LAN interface found on the TV subnet; pass --host and --interface.')

class Companion:
 def __init__(self,host,tv,port,interface,ffmpeg):
  self.host,self.tv,self.port,self.interface,self.ffmpeg=host,tv,port,interface,str(ffmpeg)
  self.cache=PLATFORM/'.cache/runtime';self.cache.mkdir(parents=True,exist_ok=True);self.cache.chmod(0o700)
  token_file=self.cache/'widget-token'
  if not token_file.exists():token_file.write_text(secrets.token_hex(24));token_file.chmod(0o600)
  self.token=token_file.read_text().strip();self.lock=threading.RLock();self.control=threading.RLock()
  self.events=[];self.sequence=0;self.native=None;self.encoder=None;self.remote=None;self.sample=None;self.state='stopped';self.closed=False;self.generation=0
  self.telemetry=[];self.current_stream=None;self.stream_allowed=False;self.name='AirPlay TV Legacy';self.widget_poll=0
  self.logfile=self.cache/'events.ndjson';self.last_widget=0
  identity=self.cache/'receiver-id'
  if not identity.exists():raw=bytearray(secrets.token_bytes(6));raw[0]=(raw[0]|2)&254;identity.write_text(raw.hex())
  self.identity=identity.read_text().strip()
  subprocess.run(['python3',str(PLATFORM/'scripts/build.py'),'--host',host,'--port',str(port),'--token',self.token],check=True,stdout=subprocess.DEVNULL)
  def watchdog():
   while not self.closed:
    time.sleep(5)
    if self.widget_poll and time.monotonic()-self.widget_poll>15 and self.native:
     self.stop();self.emit({'type':'log','message':'Stopped receiving because the TV widget disconnected.'})
  threading.Thread(target=watchdog,daemon=True).start()
 def emit(self,event):
  with self.lock:
   self.sequence+=1;entry={'id':self.sequence,'event':event};self.events.append(entry);self.events=self.events[-200:]
   with self.logfile.open('a') as out:out.write(json.dumps({'time':time.time(),**entry},ensure_ascii=False)+'\n')
 def encoder_args(self,folder,inputs,finite=False):
  return [self.ffmpeg,'-hide_banner','-loglevel','warning','-y',*inputs,
          '-c:v','libx264','-preset','ultrafast','-tune','zerolatency','-pix_fmt','yuv420p','-profile:v','main','-level','3.1',
          '-r','30','-g','30','-keyint_min','30','-sc_threshold','0','-b:v','2000k','-maxrate','2500k','-bufsize','5000k',
          '-c:a','aac','-b:a','128k','-ar','44100','-ac','2','-f','hls','-hls_time','1','-hls_list_size','6',
          '-hls_flags','delete_segments+independent_segments','-hls_segment_filename',str(folder/'seg%06d.ts'),str(folder/'index.m3u8')]
 def folder(self,kind):
  folder=self.cache/'media'/(kind+'-'+uuid.uuid4().hex[:12]);folder.mkdir(parents=True);return folder
 def publish_when_ready(self,folder,generation,kind,process):
  def monitor():
   deadline=time.monotonic()+25
   while not self.closed and generation==self.generation and time.monotonic()<deadline:
    playlist=folder/'index.m3u8'
    if playlist.exists() and len(list(folder.glob('seg*.ts')))>=2:
     url=f'http://{self.host}:{self.port}/media/{self.token}/{folder.name}/index.m3u8'
     self.current_stream=url
     if kind!='mirror' or self.stream_allowed:self.emit({'type':'stream','url':url,'kind':kind})
     return
    if process.poll() is not None:break
    time.sleep(.1)
   if generation==self.generation:self.emit({'type':'error','message':'HLS output did not become ready: '+kind})
  threading.Thread(target=monitor,daemon=True).start()
 def native_events(self,process,folder,generation):
  for line in process.stdout:
   if generation!=self.generation:break
   try:event=json.loads(line)
   except (ValueError,TypeError):continue
   if not isinstance(event,dict) or 'type' not in event:continue
   kind=event['type']
   if kind=='ready':self.state='listening'
   if kind=='connected':
    self.state='connected';self.stream_allowed=True
    if self.current_stream:self.emit({'type':'stream','url':self.current_stream,'kind':'mirror'})
   if kind=='disconnected':self.state='listening';self.stream_allowed=False;self.emit({'type':'stream-stop'})
   if kind=='error':self.state='error'
   if kind=='remote-url':self.play_remote(event.get('url',''));continue
   if kind=='remote-stop':self.stop_process(self.remote);self.remote=None;self.emit({'type':'stream-stop'});continue
   self.emit(event)
  if generation==self.generation and self.state not in ['stopped','error']:
   self.state='error';self.emit({'type':'error','message':'The Mac receiver exited.'})
 @staticmethod
 def stop_process(process):
  if not process:return
  if process.poll() is None:
   process.terminate()
   try:process.wait(timeout=3)
   except subprocess.TimeoutExpired:process.kill();process.wait(timeout=3)
 def start(self,payload):
  with self.control:
   if self.native and self.native.poll() is None:
    if self.state in ['listening','connected']:self.emit({'type':'ready','ip':self.host})
    return
   self.stop();self.generation+=1;generation=self.generation
   name=payload.get('name','AirPlay TV Legacy')
   if not isinstance(name,str) or not name.strip() or len(name.encode())>50 or re.search(r'[\x00-\x1f]',name):raise ValueError('Invalid receiver name')
   self.name=name;self.state='starting';self.stream_allowed=False;self.current_stream=None
   folder=self.folder('mirror');video_read,video_write=os.pipe();audio_read,audio_write=os.pipe()
   inputs=['-thread_queue_size','64','-f','rawvideo','-pixel_format','yuv420p','-video_size','1280x720','-framerate','30','-i','pipe:'+str(video_read),
           '-thread_queue_size','512','-f','s16le','-ar','44100','-ac','2','-i','pipe:'+str(audio_read)]
   try:
    with (folder/'encoder.log').open('w') as log:
     self.encoder=subprocess.Popen(self.encoder_args(folder,inputs),pass_fds=(video_read,audio_read),stdout=subprocess.DEVNULL,stderr=log)
    binary=PLATFORM/'build/airplay-legacy-host'
    with (self.cache/'native.log').open('a') as log:
     self.native=subprocess.Popen([str(binary),name,self.host,self.identity,str(self.cache/'pairing.key'),str(video_write),str(audio_write),'1' if payload.get('approve',True) else '0',self.interface],pass_fds=(video_write,audio_write),stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=log,text=True,bufsize=1)
   except Exception:
    self.stop();raise
   finally:
    for fd in [video_read,video_write,audio_read,audio_write]:os.close(fd)
   threading.Thread(target=self.native_events,args=(self.native,folder,generation),daemon=True).start()
   self.publish_when_ready(folder,generation,'mirror',self.encoder)
 def stop(self):
  with self.control:
   self.generation+=1;self.state='stopped';self.current_stream=None;self.stream_allowed=False
   for process in [self.encoder,self.remote,self.sample]:self.stop_process(process)
   self.encoder=self.remote=self.sample=None
   if self.native:
    try:self.native.stdin.write('stop\n');self.native.stdin.flush();self.native.stdin.close()
    except (OSError,ValueError):pass
    try:self.native.wait(timeout=4)
    except subprocess.TimeoutExpired:self.stop_process(self.native)
    self.native=None
   self.emit({'type':'stream-stop'})
 def approve(self,payload):
  request=int(payload.get('id',0));decision=1 if payload.get('decision')==1 else 0
  with self.control:
   if self.native and self.native.poll() is None:self.native.stdin.write(json.dumps({'id':request,'decision':decision})+'\n');self.native.stdin.flush()
 def test(self):
  with self.control:
   self.stop_process(self.sample);folder=self.folder('sample');generation=self.generation
   inputs=['-re','-f','lavfi','-i','testsrc2=size=1280x720:rate=30','-f','lavfi','-i','sine=frequency=440:sample_rate=44100','-t','15','-af','volume=0.08']
   with (folder/'encoder.log').open('w') as log:self.sample=subprocess.Popen(self.encoder_args(folder,inputs,True),stdout=subprocess.DEVNULL,stderr=log)
   self.publish_when_ready(folder,generation,'sample',self.sample)
 def play_remote(self,url):
  parsed=urllib.parse.urlsplit(url)
  if parsed.scheme not in ['http','https'] or not parsed.hostname:self.emit({'type':'error','message':'Unsupported AirPlay video URL'});return
  with self.control:
   self.stop_process(self.remote);folder=self.folder('remote');generation=self.generation
   inputs=['-i',url,'-vf','scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2']
   with (folder/'encoder.log').open('w') as log:self.remote=subprocess.Popen(self.encoder_args(folder,inputs),stdout=subprocess.DEVNULL,stderr=log)
   self.publish_when_ready(folder,generation,'remote',self.remote)
 def record_telemetry(self,payload,source):
  if len(json.dumps(payload))>4096:raise ValueError('Telemetry too large')
  now=time.time()
  if source==self.tv:self.last_widget=now
  self.telemetry.append({'time':now,'source':source,**payload});self.telemetry=self.telemetry[-100:]
  self.emit({'type':'log','message':'TV '+str(payload.get('type','event'))+' '+json.dumps(payload.get('data',{}),ensure_ascii=False)})
  if source==self.tv and payload.get('type')=='widget-closed':self.stop()
 def status(self):
  return {'state':self.state,'receiverRunning':bool(self.native and self.native.poll() is None),'lastWidgetSeen':self.last_widget,'widgetEvents':self.telemetry[-20:],
          'encoderRunning':bool(self.encoder and self.encoder.poll() is None),'streamReady':bool(self.current_stream),'name':self.name}

def handler_for(companion):
 class Handler(http.server.BaseHTTPRequestHandler):
  def log_message(self,format,*args):pass # Tokens must not appear in access logs.
  def reply(self,status,data,kind='application/json'):
   body=json.dumps(data,ensure_ascii=False).encode() if not isinstance(data,bytes) else data
   self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(body)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff')
   if self.path.startswith('/api/') and status==200:self.send_header('Access-Control-Allow-Origin','*')
   self.end_headers()
   try:self.wfile.write(body)
   except (BrokenPipeError,ConnectionResetError):pass
  def source_ok(self):return self.client_address[0] in [companion.tv,companion.host,'127.0.0.1']
  def serve_file(self,file,mime):
   size=file.stat().st_size;start,end,status=0,size-1,200
   header=self.headers.get('Range')
   if header:
    match=re.fullmatch(r'bytes=(\d+)-(\d*)',header.strip())
    if not match:return self.reply(416,{'error':'unsupported range'})
    start=int(match[1]);end=min(size-1,int(match[2])) if match[2] else size-1
    if start> end or start>=size:return self.reply(416,{'error':'range outside file'})
    status=206
   self.send_response(status);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(end-start+1));self.send_header('Accept-Ranges','bytes');self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff')
   if status==206:self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
   if file.suffix in ['.mp4','.ts']:
    self.send_header('transferMode.dlna.org','Streaming');self.send_header('contentFeatures.dlna.org','DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000')
   self.end_headers()
   if self.command!='HEAD':
    try:
     with file.open('rb') as source:
      source.seek(start);remaining=end-start+1
      while remaining:
       chunk=source.read(min(65536,remaining))
       if not chunk:break
       self.wfile.write(chunk);remaining-=len(chunk)
    except (BrokenPipeError,ConnectionResetError):pass
   if self.client_address[0]==companion.tv:
    with companion.lock:
     with companion.logfile.open('a') as log:log.write(json.dumps({'time':time.time(),'delivery':file.name,'method':self.command,'status':status,'range':header})+'\n')
  def auth(self):
   token=urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query).get('token',[''])[0]
   return self.source_ok() and hmac.compare_digest(token,companion.token)
  def do_OPTIONS(self):
   if not self.auth():return self.reply(403,{'error':'unauthorized'})
   self.send_response(200);self.send_header('Access-Control-Allow-Origin','*');self.send_header('Access-Control-Allow-Headers','Content-Type');self.send_header('Access-Control-Allow-Methods','GET,POST,OPTIONS');self.end_headers()
  def do_GET(self):
   if not self.source_ok():return self.reply(403,{'error':'source denied'})
   path=urllib.parse.unquote(urllib.parse.urlsplit(self.path).path)
   if path.startswith('/api/'):
    if not self.auth():return self.reply(403,{'error':'unauthorized'})
    if path=='/api/status':return self.reply(200,companion.status())
    if path=='/api/events':
     if self.client_address[0]==companion.tv:companion.widget_poll=time.monotonic()
     try:after=int(urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query).get('after',['0'])[0])
     except ValueError:return self.reply(400,{'error':'invalid cursor'})
     with companion.lock:return self.reply(200,{'events':[e for e in companion.events if e['id']>after],'name':companion.name})
    return self.reply(404,{'error':'not found'})
   root=None;relative=''
   if path.startswith('/preview/'):
    relative=path[len('/preview/'):] or 'index.html';root=PLATFORM/'build/widget'
    if relative=='bridge-config.js':return self.reply(200,b'window.AirPlayBridgeConfig={};','application/javascript')
   elif path.startswith('/media/'+companion.token+'/'):
    root=companion.cache/'media';relative=path[len('/media/'+companion.token+'/'):]
   else:return self.reply(404,{'error':'not found'})
   file=(root/relative).resolve()
   if root.resolve() not in file.parents or not file.is_file():return self.reply(404,{'error':'not found'})
   mime={'.m3u8':'application/vnd.apple.mpegurl','.ts':'video/mp2t','.js':'application/javascript'}.get(file.suffix,mimetypes.guess_type(file)[0] or 'application/octet-stream')
   return self.serve_file(file,mime)
  def do_HEAD(self):return self.do_GET()
  def do_POST(self):
   if not self.auth():return self.reply(403,{'error':'unauthorized'})
   try:
    size=int(self.headers.get('Content-Length','0'))
    if size<0 or size>8192:raise ValueError('Invalid body size')
    data=json.loads(self.rfile.read(size));path=urllib.parse.urlsplit(self.path).path
    if not isinstance(data,dict):raise ValueError('Object expected')
    if path=='/api/start':companion.start(data)
    elif path=='/api/stop':companion.stop()
    elif path=='/api/approve':companion.approve(data)
    elif path=='/api/test':companion.test()
    elif path=='/api/telemetry':companion.record_telemetry(data,self.client_address[0])
    else:return self.reply(404,{'error':'not found'})
    return self.reply(200,{'ok':True})
   except Exception as error:return self.reply(400,{'error':str(error)})
 return Handler

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--tv',required=True);parser.add_argument('--host');parser.add_argument('--interface',default='en1');parser.add_argument('--port',type=int,default=4188);parser.add_argument('--ffmpeg')
 args=parser.parse_args();ipaddress.ip_address(args.tv)
 if args.host:host,interface=args.host,args.interface
 else:host,interface=lan_ip(args.tv)
 if args.ffmpeg:ffmpeg=args.ffmpeg
 else:
  import imageio_ffmpeg
  ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
 companion=Companion(host,args.tv,args.port,interface,ffmpeg);atexit.register(companion.stop)
 server=http.server.ThreadingHTTPServer(('0.0.0.0',args.port),handler_for(companion))
 print(f'Legacy companion ready at {host}:{args.port}; allowed TV: {args.tv}. Preview: http://127.0.0.1:{args.port}/preview/',flush=True)
 try:server.serve_forever()
 except KeyboardInterrupt:pass
 finally:companion.closed=True;server.server_close();companion.stop()
