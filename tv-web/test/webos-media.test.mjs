import test from 'node:test';
import assert from 'node:assert/strict';
import {WebosMedia} from '../src/webos-media.js';
function player(){
 const listeners={},feed=[],events=[];let options;
 const video={style:{removeProperty(){}},buffered:{length:0},currentTime:0,addEventListener:(name,fn)=>listeners[name]=fn,play:async()=>{}};
 const media=new WebosMedia({video,scope:{addEventListener(){}},onEvent:event=>events.push(event),createMuxer:config=>{options=config;return {feed:packet=>feed.push(packet),destroy(){}};}});
 media.start(1);return {media,feed,events,listeners,ready:()=>options.onReady(),captureReady:()=>options.onReady};
}
test('the SPS/PPS packet waits for MediaSource before JMuxer receives it',()=>{
 const {media,feed,events,listeners,ready}=player(),first=new Uint8Array([0,0,0,1,0x67]);
 media.pushVideo(first,0,30);media.pushVideo(new Uint8Array([0,0,0,1,0x65]),1/30,30);
 assert.equal(feed.length,0);assert.equal(events.length,0);ready();
 assert.equal(feed.length,2);assert.equal(feed[0].video,first);assert.equal(events.length,0);
 listeners.playing();assert.equal(events[0].type,'video-start');media.close();
});
test('a MediaSource that never opens cannot accumulate unlimited video',()=>{
 const {media,events}=player();for(let i=0;i<121;i++)media.pushVideo(new Uint8Array([0,0,1,0x65]),i/30,30);
 assert.equal(media.run,0);assert.equal(media.videoQueue.length,0);assert.equal(events[0].type,'error');
});
test('a late sourceopen from a stopped player cannot feed the next session',()=>{
 const {media,feed,ready,captureReady}=player(),frame=new Uint8Array([0,0,1,0x67]);
 media.pushVideo(frame,0,30);const previousReady=captureReady();
 media.start(2);media.pushVideo(frame,0,30);previousReady();assert.equal(feed.length,0);
 ready();assert.equal(feed.length,1);media.close();
});

function audioOutput(media){
 const scheduled=[];
 media.audio={state:'running',currentTime:0,destination:{},
  createBuffer:(_channels,count,rate)=>({duration:count/rate,getChannelData:()=>new Float32Array(count)}),
  createBufferSource:()=>({connect(){},disconnect(){},stop(){},start(when){scheduled.push({when,duration:this.buffer.duration});}})};
 return scheduled;
}
test('audio remains contiguous when video currentTime and packet delivery advance in bursts',()=>{
 const {media,events}=player(),scheduled=audioOutput(media),count=480,period=count/44100;
 media.videoFirstPts=0;media.videoPlaying=true;
 for(let i=0;i<100;i++){
  media.audio.currentTime=Math.floor(i/4)*4*period;
  media.video.currentTime=Math.floor(i/3)*3*period;
  media.pushAudio(new Uint8Array(count*4),i*period);
 }
 assert.equal(scheduled.length,100);assert.equal(events.some(e=>e.type==='error'),false);
 for(let i=1;i<scheduled.length;i++)assert.ok(Math.abs(scheduled[i].when-scheduled[i-1].when-scheduled[i-1].duration)<1e-8,'PCM buffers must touch without gaps or overlaps');
 media.close();
});
test('an audio underrun or a sender clock jump recovers with headroom instead of stopping the receiver',()=>{
 const {media,events}=player(),scheduled=audioOutput(media),pcm=new Uint8Array(480*4);
 media.pushAudio(pcm,0);media.audio.currentTime=1;media.pushAudio(pcm,480/44100);
 assert.ok(scheduled[1]?.when>=1.1,'a late packet must re-prime the jitter buffer');
 media.pushAudio(pcm,20);
 assert.equal(media.run,1);assert.equal(events.some(e=>e.type==='error'),false);media.close();
});
test('sparse screen updates preserve the sender timeline and a backed-up video player catches up',()=>{
 const {media,feed,events,ready,listeners}=player(),frame=new Uint8Array([0,0,1,0x65]);
 media.pushVideo(frame,0,30);ready();listeners.playing();
 media.pushVideo(frame,1.5,30);media.pushVideo(frame,6.5,30);
 assert.equal(media.run,1);assert.ok(Math.abs(feed.reduce((sum,p)=>sum+p.duration,0)-6533)<2);
 media.video.buffered={length:1,start:()=>0,end:()=>8};media.video.currentTime=0;
 media.pushVideo(frame,6.6,30);
 assert.ok(media.video.currentTime>7);assert.equal(events.some(e=>e.type==='error'),false);media.close();
});
