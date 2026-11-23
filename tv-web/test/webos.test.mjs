import test from 'node:test';
import assert from 'node:assert/strict';
import {webosPlatform} from '../src/platforms/webos.js';
import {ReceiverEngine} from '../src/engine.js';
import {remoteKey} from '../src/remote.js';

test('a browser or an LG device without the service bridge cannot receive AirPlay',async()=>{
  for(const scope of [{},{PalmSystem:{}}]){
    const platform=webosPlatform(scope);
    assert.equal(platform.available,false);
    assert.equal(platform.isDevice,'PalmSystem' in scope);
    let loaded=false;
    const engine=new ReceiverEngine({...platform,loadCore:async()=>{loaded=true;},settings:{}});
    await engine.start();
    assert.equal(engine.status,'unsupported');assert.equal(loaded,false);
    await assert.rejects(platform.loadCore(),/webosServiceRequired/);
  }
});
test('LG receiver state follows service events and never treats a queued start as readiness',async()=>{
  const bridges=[],calls=[];
  class Bridge{
    constructor(){bridges.push(this);}
    call(uri,payload){const data=JSON.parse(payload);calls.push({uri,data});queueMicrotask(()=>this.onservicecallback(JSON.stringify({returnValue:true,...(uri.endsWith('/info')?{ip:'192.168.1.2',coreBuilt:true,mediaUrl:'ws://127.0.0.1:1234/media/'+'a'.repeat(48)}:{}),...(data.subscribe?{subscribed:true}:{})})));}
    cancel(){}
  }
  const media={connect:async()=>{},start(){},close(){}};
  const scope={PalmSystem:{},PalmServiceBridge:Bridge,document:{getElementById:()=>({})}};
  const platform=webosPlatform(scope,{createMedia:()=>media});assert.equal(platform.available,true);
  const engine=new ReceiverEngine({...platform,settings:{values:{serverName:'LG',deviceId:'021122334455',trusted:[],approveDevices:true,resolution:'1280x720',maxFps:30}}});
  await engine.start();assert.equal(engine.status,'starting');
  const run=calls.find(x=>x.uri.endsWith('/start')).data.values[8];
  const eventsBridge=bridges[calls.findIndex(x=>x.uri.endsWith('/events'))];
  eventsBridge.onservicecallback(JSON.stringify({returnValue:true,event:{type:'ready',runId:run}}));assert.equal(engine.status,'listening');engine.stop();
});
test('webOS Back works even when the remote reports an Unidentified key',()=>{
  for(const key of ['', 'Unidentified', 'BrowserBack'])assert.equal(remoteKey({key,keyCode:461}),'Escape');
  assert.equal(remoteKey({key:'Unidentified',keyCode:38}),'ArrowUp');
  assert.equal(remoteKey({key:'Escape',keyCode:27}),'Escape');
});
test('stopping during the media connection cancels a late Luna subscription',async()=>{
  let finishConnect,connectStarted=false,closed=0,subscribed=0;
  class Bridge{
    call(uri){if(uri.endsWith('/events'))subscribed++;queueMicrotask(()=>this.onservicecallback(JSON.stringify({returnValue:true,ip:'192.168.1.2',coreBuilt:true,mediaUrl:'ws://127.0.0.1:1234/media/'+'a'.repeat(48)})));}
    cancel(){}
  }
  const platform=webosPlatform({PalmSystem:{},PalmServiceBridge:Bridge,document:{getElementById:()=>({})}},{createMedia:()=>({connect:()=>{connectStarted=true;return new Promise(resolve=>finishConnect=resolve);},close(){closed++;}})});
  const engine=new ReceiverEngine({...platform,settings:{}}),start=engine.start();
  while(!connectStarted)await new Promise(resolve=>setImmediate(resolve));
  engine.stop();finishConnect();await start;
  assert.equal(engine.status,'stopped');assert.equal(subscribed,0);assert.ok(closed>0);
});
test('the custom exit confirmation closes an LG app only on a detected device',()=>{
  let closed=0;
  webosPlatform({PalmSystem:{},close:()=>closed++}).exit();
  webosPlatform({close:()=>closed++}).exit();
  assert.equal(closed,1);
});
