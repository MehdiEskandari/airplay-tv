#!/usr/bin/env node
'use strict';
// Development only. Runs the packaged service on the host, with a tiny Luna
// transport replacement in a separate staging app. Never included in the IPK.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
const {createRequire}=require('module');
const platform=path.resolve(__dirname,'..');
const {WebSocketServer}=require(path.resolve(platform,'../../tv-web/node_modules/ws'));
const APP='io.github.mehdieskandari.airplaytv.webos',SERVICE=APP+'.receiver';
const token=crypto.randomBytes(24).toString('hex'),methods=new Map(),connections=new Set();
class SimulatorService {
  constructor(id){if(id!==SERVICE)throw new Error('Unexpected service ID');}
  register(name,callback,cancel){methods.set(name,{callback,cancel});}
}
const entry=path.join(platform,'build/service/service.js'),localRequire=createRequire(entry);
const moduleInstance={exports:{}};
vm.runInThisContext(require('module').wrap(fs.readFileSync(entry,'utf8')),{filename:entry})(moduleInstance.exports,id=>id==='webos-service'?SimulatorService:localRequire(id),moduleInstance,entry,path.dirname(entry));
const server=new WebSocketServer({host:'127.0.0.1',port:0,maxPayload:65536,verifyClient:({req})=>req.url==='/rpc/'+token});
const controller=moduleInstance.exports.controller;
let fixtureTimer;
function fixture(){
  const dir=process.argv[2];if(!dir||!controller.run)throw new Error('Pass a fixture directory and start the receiver first');
  const raw=fs.readFileSync(path.join(dir,'pattern.h264')),wav=fs.readFileSync(path.join(dir,'tone.wav'));
  const starts=[];for(let i=0;i<raw.length-5;i++){let prefix=0;if(raw[i]===0&&raw[i+1]===0){if(raw[i+2]===1)prefix=3;else if(raw[i+2]===0&&raw[i+3]===1)prefix=4;}if(prefix&&(raw[i+prefix]&31)===9){starts.push(i);i+=prefix;}}
  const frames=starts.map((start,i)=>raw.subarray(start,starts[i+1]||raw.length));
  let dataStart=12,dataSize=0;while(dataStart<wav.length-8){const size=wav.readUInt32LE(dataStart+4);if(wav.toString('ascii',dataStart,dataStart+4)==='data'){dataSize=size;dataStart+=8;break;}dataStart+=8+size+(size&1);}
  if(!frames.length||!dataSize)throw new Error('Invalid synthetic fixtures');
  const pcm=wav.subarray(dataStart,dataStart+dataSize),run=controller.run,start=performance.now();let videoIndex=0,audioIndex=0;
  clearInterval(fixtureTimer);console.log('SYNTHETIC MEDIA FIXTURE: this bypasses AirPlay transport and pairing');
  fixtureTimer=setInterval(()=>{
    if(controller.run!==run){clearInterval(fixtureTimer);return;}
    const seconds=(performance.now()-start)/1000;
    while(videoIndex<frames.length&&videoIndex/30<=seconds){controller.media({kind:'video',runId:run,pts:videoIndex/30,width:1280,height:720,fps:30,data:frames[videoIndex++]});}
    while(audioIndex<pcm.length&&audioIndex/4/44100<=seconds){const end=Math.min(pcm.length,audioIndex+352*4);controller.media({kind:'audio',runId:run,pts:audioIndex/4/44100,width:0,height:0,fps:0,data:pcm.subarray(audioIndex,end)});audioIndex=end;}
    if(videoIndex===frames.length&&audioIndex===pcm.length){clearInterval(fixtureTimer);console.log('Fixture delivered: '+videoIndex+' H264 access units and '+audioIndex/4+' stereo PCM samples');}
  },5);
}
server.on('connection',socket=>{
  connections.add(socket);const subscriptions=new Map();
  socket.on('message',bytes=>{
    let request;try{request=JSON.parse(bytes);}catch{return;}
    const {id,method,payload,cancel}=request;
    if(method==='fixture'){try{fixture();}catch(error){console.error(error.message);}return;}
    if(method==='diagnostics'){console.log('SIMULATOR MEDIA: '+JSON.stringify(payload));return;}
    const handler=methods.get(method);
    if(!Number.isSafeInteger(id)||!handler)return;
    const message={sender:APP,applicationID:APP,payload:payload||{},uniqueToken:id,respond:result=>{if(socket.readyState===1)socket.send(JSON.stringify({id,result}));}};
    if(cancel){handler.cancel?.(subscriptions.get(id)||message);subscriptions.delete(id);return;}
    if(payload?.subscribe)subscriptions.set(id,message);
    handler.callback(message);
  });
  socket.on('close',()=>{connections.delete(socket);for(const message of subscriptions.values())methods.get('events')?.cancel?.(message);});
});
server.on('listening',()=>{
  const staging=path.join(platform,'build/simulator');
  fs.rmSync(staging,{recursive:true,force:true});fs.cpSync(path.join(platform,'build/app'),staging,{recursive:true});
  const bundle=path.join(staging,'app.js'),appCode=fs.readFileSync(bundle,'utf8');
  if(!appCode.includes('this.video = video2;'))throw new Error('Update the test instrumentation for the current bundle');
  fs.writeFileSync(bundle,appCode.replace('this.video = video2;','this.video = video2; window.__hostMedia=this;'));
  const metadataPath=path.join(staging,'appinfo.json'),metadata=JSON.parse(fs.readFileSync(metadataPath,'utf8'));metadata.id+='.simulator';metadata.title='AirPlay TV Host Harness';fs.writeFileSync(metadataPath,JSON.stringify(metadata,null,2));
  const url='ws://127.0.0.1:'+server.address().port+'/rpc/'+token;
  fs.writeFileSync(path.join(staging,'simulator-bridge.js'),`/* HOST HARNESS: not the TV Luna runtime. */
(()=>{const Native=window.PalmServiceBridge,url=${JSON.stringify(url)};let next=0;
window.PalmServiceBridge=class {
 call(uri,payload){if(!uri.startsWith('luna://${SERVICE}/')){this.native=new Native();this.native.onservicecallback=r=>this.onservicecallback?.(r);this.native.call(uri,payload);return;}
 this.id=++next;this.method=uri.split('/').pop();this.socket=new WebSocket(url);
 this.socket.onmessage=e=>{const r=JSON.parse(e.data);if(r.id===this.id)this.onservicecallback?.(JSON.stringify(r.result));};
 this.socket.onopen=()=>this.socket.send(JSON.stringify({id:this.id,method:this.method,payload:JSON.parse(payload)}));
 this.socket.onerror=()=>this.onservicecallback?.(JSON.stringify({returnValue:false,errorText:'Host simulator harness unavailable'}));
 }
 cancel(){this.native?.cancel();if(this.socket?.readyState===1)this.socket.send(JSON.stringify({id:this.id,method:this.method,cancel:true}));this.socket?.close();}
};document.documentElement.dataset.hostHarness='true';
const Audio=window.AudioContext;let audio,pcmPeak=0;
window.AudioContext=class extends Audio{constructor(options){super(options);audio=this;}
 createBufferSource(){const source=super.createBufferSource(),start=source.start.bind(source);source.start=(...args)=>{if(source.buffer)for(let c=0;c<source.buffer.numberOfChannels;c++)for(const value of source.buffer.getChannelData(c))pcmPeak=Math.max(pcmPeak,Math.abs(value));return start(...args);};return source;}};
window.addEventListener('DOMContentLoaded',()=>{
 const panel=document.createElement('div');panel.style='position:fixed;z-index:9999;bottom:8px;left:8px;background:#111e;color:#fff;padding:8px;font:28px monospace;direction:ltr;max-width:95vw';
 panel.innerHTML='<div>HOST HARNESS • not an LG TV service</div><button id="fixture-start">Play synthetic A/V test</button><span id="fixture-stats"></span>';document.body.appendChild(panel);
 const send=(method,payload)=>{const socket=new WebSocket(url);socket.onopen=()=>{socket.send(JSON.stringify({method,payload}));setTimeout(()=>socket.close(),100);};};
 document.getElementById('fixture-start').onclick=()=>send('fixture',{});
 let reported=0;setInterval(()=>{const video=document.getElementById('receiver-video'),media=window.__hostMedia,stats={...media?.diagnostics(),readyState:video.readyState,buffered:video.buffered.length?video.buffered.end(0):0,mseReady:media?.muxer?.mseReady,videoTime:Math.round(video.currentTime*100)/100,audioState:audio?.state||'locked',scheduledPcmPeak:pcmPeak};document.getElementById('fixture-stats').textContent=JSON.stringify(stats);if(stats.videoPackets>reported+30){reported=stats.videoPackets;send('diagnostics',stats);}},500);
});})();`);
  const html=path.join(staging,'index.html');fs.writeFileSync(html,fs.readFileSync(html,'utf8').replace('<script src="app.js">','<script src="simulator-bridge.js"></script><script src="app.js">'));
  console.log('HOST HARNESS READY. This is not an LG device service.');
  console.log('Launch in the official simulator: '+staging);
});
for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>{for(const socket of connections)socket.close();server.close();setTimeout(()=>process.exit(0),1500);});
