import test from 'node:test';
import assert from 'node:assert/strict';
import {WebosMedia} from '../src/webos-media.js';
function player(){
 const listeners={},feed=[],events=[];let options;
 const video={style:{removeProperty(){}},buffered:{length:0},currentTime:0,addEventListener:(name,fn)=>listeners[name]=fn,play:async()=>{}};
 const media=new WebosMedia({video,scope:{addEventListener(){}},onEvent:event=>events.push(event),createMuxer:config=>{options=config;return {feed:packet=>feed.push(packet),destroy(){}};}});
 media.start(1);return {media,feed,events,listeners,ready:()=>options.onReady()};
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
