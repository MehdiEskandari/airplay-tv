'use strict';
const fs=require('fs'),path=require('path'),os=require('os'),http=require('http'),crypto=require('crypto');
const {Sockets}=require('./sockets.cjs');
const {APP}=require('./identity.cjs');
function address(){
  for(const entries of Object.values(os.networkInterfaces()))for(const entry of entries||[])
    if((entry.family==='IPv4'||entry.family===4)&&!entry.internal&&!entry.address.startsWith('169.254.'))return entry.address;
  throw new Error('networkUnavailable');
}
function validate(values){
  const [name,ip,id,trusted,approve,w,h,fps,run]=values;
  if(typeof name!=='string'||!name.trim()||Buffer.byteLength(name)>63||/[\x00-\x1f]/.test(name))throw new Error('Invalid receiver name');
  if(!require('net').isIPv4(ip)||ip==='0.0.0.0'||!/^[a-f0-9]{12}$/i.test(id))throw new Error('Invalid receiver identity or address');
  const keys=JSON.parse(trusted);
  if(!Array.isArray(keys)||keys.length>128||keys.some(key=>!key||!/^[A-Za-z0-9+/]{43}=$/.test(key.key)))throw new Error('Invalid trusted identities');
  if(![0,1].includes(approve)||!((w===1280&&h===720)||(w===1920&&h===1080))||![30,60].includes(fps)||!Number.isSafeInteger(run)||run<1)throw new Error('Invalid receiver configuration');
}
class CoreController {
  constructor({root=__dirname,stateDir,listenPort=7002,WebSocketServer,onEvent=()=>{}}){
    if(!Number.isInteger(listenPort)||listenPort<0||listenPort>65535)throw new Error('Invalid listener port');this.listenPort=listenPort;
    this.root=root;this.stateDir=stateDir||path.join(process.platform==='darwin'?path.join(os.homedir(),'Library/Caches/AirPlayTV-webOS'):'/media/internal',APP);this.WebSocketServer=WebSocketServer;this.onEvent=onEvent;
    this.token=crypto.randomBytes(24).toString('hex');this.clients=new Set();this.module=null;this.pending=null;this.run=0;this.epoch=0;this.stopTimer=null;
  }
  async open(){
    if(this.server)return this.info();
    fs.mkdirSync(this.stateDir,{recursive:true,mode:0o700});
    this.server=http.createServer((req,res)=>{res.writeHead(404);res.end();});
    this.ws=new this.WebSocketServer({server:this.server,maxPayload:1024,verifyClient:({req})=>req.url==='/media/'+this.token});
    this.ws.on('connection',socket=>{
      if(this.clients.size){socket.close(1008);return;}this.clients.add(socket);
      socket.on('error',()=>{});socket.on('close',()=>{this.clients.delete(socket);if(!this.clients.size&&this.run){this.emit({type:'error',message:'Media connection closed',runId:this.run});this.stop();}});
    });
    await new Promise((resolve,reject)=>{this.server.once('error',reject);this.server.listen(0,'127.0.0.1',resolve);});
    return this.info();
  }
  info(){return {ip:address(),mediaUrl:this.server?'ws://127.0.0.1:'+this.server.address().port+'/media/'+this.token:null,coreBuilt:fs.existsSync(path.join(this.root,'core/airplay-core.wasm')),nodeVersion:process.versions.node};}
  emit(event){if(event.runId!==undefined&&event.runId!==this.run)return;this.onEvent(event);}
  async load(){
    if(this.module)return this.module;if(this.pending)return this.pending;
    this.pending=(async()=>{
      const factory=require(path.join(this.root,'core/airplay-core.js'));
      const sockets=new Sockets();this.sockets=sockets;let core;
      core=await factory({
        mainScriptUrlOrBlob:path.join(this.root,'core/airplay-core.js'),
        wasmBinary:fs.readFileSync(path.join(this.root,'core/airplay-core.wasm')),
        locateFile:name=>path.join(this.root,'core',name),airplaySockets:sockets,airplayPort:this.listenPort,
        preRun:[function(module){module.FS.mkdir('/persist');module.FS.mount(module.NODEFS,{root:this.stateDir},'/persist');}.bind(this)],
        onAirPlayEvent:event=>this.emit(event),onAirPlayMedia:packet=>this.media(packet),
        print:message=>this.emit({type:'log',level:'info',message:String(message).slice(0,500)}),
        printErr:message=>this.emit({type:'log',level:'error',message:String(message).slice(0,500)}),
        onAbort:()=>this.emit({type:'error',message:'Receiver core aborted',runId:this.run})
      });
      sockets.heap=()=>core.HEAPU8;this.module=core;return core;
    })().finally(()=>{this.pending=null;});return this.pending;
  }
  async start(values){
    validate(values);if(!this.clients.size)throw new Error('Media player is not connected');
    clearTimeout(this.stopTimer);const epoch=++this.epoch,core=await this.load();
    if(epoch!==this.epoch||!this.clients.size){this.stop();throw new Error('Receiver start cancelled');}this.run=values[8];
    return core.ccall('airplay_start','number',['string','string','string','string','number','number','number','number','number'],values);
  }
  approve(id,decision){if(!Number.isInteger(id)||id<1||![0,1,2].includes(decision))throw new Error('Invalid approval');if(this.module)this.module.ccall('airplay_approve',null,['number','number'],[id,decision]);}
  playback(values){if(!Array.isArray(values)||values.length!==4||values.some(v=>typeof v!=='number'||!Number.isFinite(v)))throw new Error('Invalid playback report');if(this.module)this.module.ccall('airplay_playback',null,['number','number','number','number'],values);}
  media(packet){
    if(packet.runId!==this.run||!this.clients.size||!Number.isFinite(packet.pts))return;
    const payload=Buffer.from(packet.data);if(payload.length>8*1024*1024)return;
    const header=Buffer.alloc(32);header[0]=packet.kind==='video'?1:2;header.writeUInt32LE(packet.runId,4);header.writeDoubleLE(packet.pts,8);header.writeUInt32LE(packet.width,16);header.writeUInt32LE(packet.height,20);header.writeUInt32LE(packet.fps,24);
    const frame=Buffer.concat([header,payload]);
    for(const client of this.clients){if(client.bufferedAmount>8*1024*1024){client.close(1009);continue;}if(client.readyState===1)client.send(frame,{binary:true});}
  }
  stop(){
    this.epoch++;this.run=0;if(this.module){try{this.module.ccall('airplay_stop',null,[],[]);}catch{}}
    clearTimeout(this.stopTimer);this.stopTimer=setTimeout(()=>{
      if(this.run)return;if(this.sockets)this.sockets.destroy();
      if(this.module&&this.module.PThread)this.module.PThread.terminateAllThreads();this.module=null;this.sockets=null;
    },1000);
  }
  async close(){this.stop();for(const client of this.clients)client.close();this.clients.clear();if(this.ws)this.ws.close();if(this.server)await new Promise(resolve=>this.server.close(resolve));this.server=null;}
}
module.exports={CoreController,validate,address,APP};
