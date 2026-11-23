const path=require('path'),fs=require('fs'),net=require('net'),os=require('os');
const repository=path.resolve(__dirname,'../../..');
const {WebSocketServer,WebSocket}=require(path.join(repository,'tv-web/node_modules/ws'));
const {CoreController,address}=require(path.join(repository,'platforms/webos/service/controller.cjs'));
const root=path.join(repository,'platforms/webos/build'),state=fs.mkdtempSync(path.join(os.tmpdir(),'airplay-webos-smoke-'));
let readyResolve,readyReject;const ready=new Promise((resolve,reject)=>{readyResolve=resolve;readyReject=reject;});
const controller=new CoreController({root,stateDir:state,listenPort:0,WebSocketServer,onEvent:event=>{
 if(event.type==='ready'){console.log('Native WASM sockets and discovery are ready');readyResolve(event);}
 if(event.type==='error'){console.log('Core error:',event.message);readyReject(new Error(event.message));}
 if(event.type==='log'&&event.level==='error')console.log('Native:',event.message.slice(0,300));
}});
const deadline=setTimeout(()=>{console.error('Core smoke timed out');process.exit(1);},20000);
(async()=>{let ws;try{
 const info=await controller.open();ws=new WebSocket(info.mediaUrl);await new Promise((resolve,reject)=>{ws.on('open',resolve);ws.on('error',reject);});
 await controller.start(['AirPlay TV LG Test',address(),'021122334455','[]',1,1280,720,30,1]);const listening=await ready;
 if(!listening.port)throw new Error('Missing real bound listener port');
 const result=await new Promise((resolve,reject)=>{const client=net.connect(listening.port,'127.0.0.1');let bytes=Buffer.alloc(0);client.on('error',reject);client.on('connect',()=>client.write('GET /info RTSP/1.0\r\nCSeq: 1\r\n\r\n'));client.on('data',chunk=>{bytes=Buffer.concat([bytes,chunk]);const split=bytes.indexOf('\r\n\r\n');if(split>=0){const header=bytes.subarray(0,split).toString();const size=Number((/Content-Length: (\d+)/i.exec(header)||[])[1]);if(bytes.length>=split+4+size){client.destroy();resolve({header,body:bytes.subarray(split+4)});}}});});
 if(!result.header.startsWith('RTSP/1.0 200')||result.body.subarray(0,8).toString()!=='bplist00')throw new Error('Invalid native RTSP /info response');
 console.log('Actual native RTSP /info returned a binary plist:',result.body.length,'bytes');

 }finally{if(ws)ws.terminate();await controller.close();clearTimeout(deadline);fs.rmSync(state,{recursive:true,force:true});}})().catch(error=>{console.error(error);process.exitCode=1;});
