import test from 'node:test';
import assert from 'node:assert/strict';
import {HlsPlayer} from '../src/hls.js';

function player(){
  const events=[];let prepared;
  const api={open:url=>events.push(['open',url]),setListener:listener=>api.listener=listener,setDisplayRect(){},setDisplayMethod(){},prepareAsync:fn=>prepared=fn,play:()=>events.push(['play']),pause:()=>events.push(['pause']),stop:()=>events.push(['stop']),close:()=>events.push(['close']),getDuration:()=>120000,getCurrentTime:()=>5000,seekTo:(n,done)=>{events.push(['seek',n]);done?.();}};
  const hls=new HlsPlayer({api,onStart:()=>events.push(['started']),onStop:()=>events.push(['stopped']),onError:error=>events.push(['error',error]),onProgress:info=>events.push(['progress',info])});
  return {api,hls,events,prepared:()=>prepared()};
}
test('AVPlay seeks before starting and reports a seconds-based timeline to AirPlay',()=>{
  const p=player();p.hls.play('https://example.com/movie.m3u8',5);assert.equal(p.events.some(e=>e[0]==='started'),false);
  p.prepared();assert.deepEqual(p.events.slice(1,3),[['seek',5000],['play']]);
  p.api.listener.oncurrentplaytime(10000);assert.deepEqual(p.events.at(-1),['progress',{position:10,duration:120,rate:1,ready:true}]);
  p.hls.rate(0);assert.ok(p.events.some(e=>e[0]==='pause'));assert.equal(p.events.at(-1)[1].rate,0);
});
test('stopped streams cannot start from a delayed prepare callback',()=>{
  const p=player();p.hls.play('http://192.168.1.20/movie');const obsolete=p.prepared;
  p.hls.stop();obsolete();assert.equal(p.events.some(e=>e[0]==='started'),false);
  assert.equal(p.hls.active,false);assert.ok(p.events.some(e=>e[0]==='close'));
});
test('unsupported URL schemes never show a playing state',()=>{
  const p=player();p.hls.play('file:///etc/passwd');assert.equal(p.events.some(e=>e[0]==='open'),false);assert.equal(p.hls.active,false);assert.equal(p.events.at(-1)[0],'error');
});
