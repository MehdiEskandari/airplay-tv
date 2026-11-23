import test from 'node:test';
import assert from 'node:assert/strict';
import {ReceiverEngine} from '../src/engine.js';
import {Preferences} from '../src/preferences.js';
import {nextInDirection} from '../src/remote.js';

const storage=()=>{const map=new Map();return {getItem:key=>map.get(key)||null,setItem:(key,value)=>map.set(key,value)};};
const tick=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function receiver(overrides={}) {
  const calls=[];let notify;
  const module={ccall:(...args)=>{calls.push(args);return 0;}};
  const engine=new ReceiverEngine({available:true,loadCore:async fn=>{notify=fn;return module;},getIp:()=> '192.168.1.20',settings:new Preferences(storage()),...overrides});
  return {engine,calls,module,notify:event=>notify(event)};
}

test('fresh installation uses Persian/Teli dark and persists a local random identity',()=>{
  const disk=storage(),first=new Preferences(disk),again=new Preferences(disk);
  assert.equal(first.get('language'),'fa');assert.equal(first.get('languageSelected'),false);
  assert.equal(first.get('appearance'),'teli_dark');assert.equal(first.get('deviceId'),again.get('deviceId'));
  assert.equal(parseInt(first.get('deviceId').slice(0,2),16)&3,2);
  first.set('language','mul');first.set('appearance','teli_light');
  assert.equal(new Preferences(disk).get('language'),'mul');assert.equal(new Preferences(disk).get('appearance'),'teli_light');
});
test('invalid names, malformed settings and forged trust entries are rejected',()=>{
  const disk=storage();disk.setItem('airplay-tv-v1','{"language":"xx","maxFps":1000,"trusted":[{"id":"spoof","key":"no-proof"}]}');
  const prefs=new Preferences(disk);assert.equal(prefs.get('language'),'fa');assert.equal(prefs.get('maxFps'),30);assert.deepEqual(prefs.get('trusted'),[]);
  for(const name of ['', 'a\nname','ف'.repeat(32)])assert.throws(()=>prefs.set('serverName',name));
  const key='A'.repeat(43)+'=';prefs.set('trusted',[{key,id:'phone',name:'My phone'}]);
  assert.equal(new Preferences(disk).get('trusted')[0].key,key);
  const broken=new Preferences({getItem(){throw new Error('blocked');},setItem(){throw new Error('blocked');}});
  assert.equal(broken.get('language'),'fa');assert.equal(broken.persistent,false);
});
test('browser preview never claims that the receiver is listening',async()=>{
  const {engine,calls}=receiver({available:false});await engine.start();assert.equal(engine.status,'unsupported');assert.equal(calls.length,0);
});
test('a queued native start is not readiness; the matching run must confirm actual sockets',async()=>{
  const r=receiver();await r.engine.start();assert.equal(r.engine.status,'starting');
  const run=r.calls[0][3].at(-1);r.notify({type:'ready',runId:run-1});assert.equal(r.engine.status,'starting');
  r.notify({type:'ready',runId:run});assert.equal(r.engine.status,'listening');
  r.engine.stop();assert.equal(r.calls.at(-1)[0],'airplay_stop');
});
test('stop during module loading prevents the delayed module from starting or publishing readiness',async()=>{
  let resolve,callback;const promise=new Promise(r=>resolve=r);const r=receiver({loadCore:fn=>{callback=fn;return promise;}});
  const starting=r.engine.start();r.engine.stop();resolve(r.module);await starting;callback({type:'ready'});
  assert.equal(r.engine.status,'stopped');assert.equal(r.calls.length,0);
});
test('startup deadline covers module loading, fails closed and ignores late completion',async()=>{
  let resolve;const r=receiver({deadlineMs:5,loadCore:()=>new Promise(r=>resolve=r)});
  const starting=r.engine.start();await tick(20);assert.equal(r.engine.status,'error');resolve(r.module);await starting;assert.equal(r.calls.length,0);
});
test('network failure prevents native start; pipeline failure stops active sockets',async()=>{
  const missing=receiver({getIp:()=> '0.0.0.0'});await missing.engine.start();assert.equal(missing.engine.status,'error');assert.equal(missing.calls.length,0);
  const live=receiver();await live.engine.start();live.notify({type:'ready'});live.notify({type:'error',message:'decoder failed'});
  assert.equal(live.engine.status,'error');assert.equal(live.calls.at(-1)[0],'airplay_stop');
});
test('physical remote directions work with both RTL and LTR row order',()=>{
  const left={x:0,y:0,width:80,height:40},middle={x:100,y:0,width:80,height:40},right={x:200,y:0,width:80,height:40},lower={x:100,y:80,width:80,height:40};
  for(const order of [[left,right,lower],[right,left,lower]]){
    assert.equal(nextInDirection(middle,order,'left'),left);assert.equal(nextInDirection(middle,order,'right'),right);assert.equal(nextInDirection(middle,order,'down'),lower);
  }
  assert.equal(nextInDirection(left,[middle,right],'left'),null);
});
test('stop while an asynchronous network lookup runs prevents a delayed native load',async()=>{
  let resolve,loaded=false;const lookup=new Promise(r=>resolve=r);
  const r=receiver({getIp:()=>lookup,loadCore:async()=>{loaded=true;return {};}});
  const starting=r.engine.start();r.engine.stop();resolve('192.168.1.20');await starting;
  assert.equal(loaded,false);assert.equal(r.engine.status,'stopped');
});
