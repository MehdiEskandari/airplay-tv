// Build an explicitly synthetic, SDK-only test of the production media player.
// It does not exercise AirPlay pairing, sockets or the service audio decoder.
import {build,webRoot} from '../../../tv-web/scripts/build.mjs';
import {mkdir,copyFile,writeFile,readFile,access} from 'node:fs/promises';
import path from 'node:path';
const platform=path.resolve(webRoot,'../platforms/webos'),out=path.join(platform,'build/media-test');
const fixtures=path.resolve(process.argv[2]||path.join(platform,'.cache/fixtures'));
for(const name of ['pattern.h264','tone.wav'])await access(path.join(fixtures,name));
await mkdir(out,{recursive:true});
for(const name of ['pattern.h264','tone.wav'])await copyFile(path.join(fixtures,name),path.join(out,name));
for(const name of ['icon.png','largeIcon.png'])await copyFile(path.join(platform,name),path.join(out,name));
const info=JSON.parse(await readFile(path.join(platform,'appinfo.json'),'utf8'));
await writeFile(path.join(out,'appinfo.json'),JSON.stringify({...info,id:info.id+'.mediatest',title:'Synthetic media test'}));
await writeFile(path.join(out,'index.html'),'<!doctype html><html><head><meta charset="utf-8"><title>Synthetic media test</title><style>body{margin:0;background:#101820;color:white;font:24px sans-serif}video{width:100vw;height:100vh;object-fit:contain}aside{position:fixed;bottom:0;left:0;background:#101820ed;padding:16px;z-index:10;max-width:95vw}button{font:inherit;padding:12px;background:#30a9fb;border:0;border-radius:12px}pre{white-space:pre-wrap}</style></head><body><video id="test-video" playsinline></video><aside><b>SYNTHETIC MEDIA TEST — no AirPlay transport</b><p><button id="play">Play jittered H264 + PCM for 32 seconds</button></p><pre id="result">Ready</pre></aside><script src="app.js"></script></body></html>');
const entry=path.join(out,'entry.mjs');
await writeFile(entry,`import {WebosMedia} from ${JSON.stringify(path.join(webRoot,'src/webos-media.js'))};
const video=document.getElementById('test-video'),result=document.getElementById('result');
const media=new WebosMedia({video,onEvent:event=>{if(event.type==='error')result.textContent=event.message;}});
window.mediaSelftest=media;
let timing;media.activate();
const createSource=media.audio.createBufferSource.bind(media.audio);
media.audio.createBufferSource=()=>{const source=createSource(),start=source.start;source.start=function(when){
 if(timing){const gap=when-timing.end;if(timing.count&&gap>.000001)timing.gaps++;if(timing.count&&gap<-.000001)timing.overlaps++;timing.count++;timing.end=when+source.buffer.duration;}
 return start.apply(source,arguments);
};return source;};
document.getElementById('play').onclick=async()=>{
 timing={count:0,gaps:0,overlaps:0,end:0};await media.audio.resume();
 media.start(1);const raw=new Uint8Array(await (await fetch('pattern.h264')).arrayBuffer()),wav=await (await fetch('tone.wav')).arrayBuffer();
 const offsets=[];for(let i=0;i<raw.length-5;i++){let prefix=0;if(raw[i]===0&&raw[i+1]===0){if(raw[i+2]===1)prefix=3;else if(raw[i+2]===0&&raw[i+3]===1)prefix=4;}if(prefix&&(raw[i+prefix]&31)===9){offsets.push(i);i+=prefix;}}
 const frames=offsets.map((o,i)=>raw.subarray(o,offsets[i+1]||raw.length)),view=new DataView(wav),text=new TextDecoder();let offset=12,size=0;
 while(offset<wav.byteLength-8){size=view.getUint32(offset+4,true);if(text.decode(new Uint8Array(wav,offset,4))==='data'){offset+=8;break;}offset+=8+size+(size&1);}
 const pcm=new Uint8Array(wav,offset,size),start=performance.now(),totalFrames=frames.length*4,totalBytes=pcm.length*4,delays=[10,70,20,90,40,60,15];let v=0,a=0,nextDelivery=0,delivery=0;
 clearInterval(window.fixtureTimer);window.fixtureTimer=setInterval(()=>{
  const elapsed=(performance.now()-start)/1000;
  if(elapsed<nextDelivery)return;nextDelivery=elapsed+delays[delivery++%delays.length]/1000;
  while(v<totalFrames&&v/30<=elapsed){media.pushVideo(frames[v%frames.length],v/30,30);v++;}
  while(a<totalBytes&&a/4/44100<=elapsed){const sourceOffset=a%pcm.length,n=Math.min(pcm.length-sourceOffset,352*4,totalBytes-a);media.pushAudio(pcm.subarray(sourceOffset,sourceOffset+n),a/4/44100);a+=n;}
  result.textContent=JSON.stringify({...media.diagnostics(),scheduleGaps:timing.gaps,scheduleOverlaps:timing.overlaps},null,2);
  if(v===totalFrames&&a===totalBytes){clearInterval(window.fixtureTimer);setTimeout(()=>{const d={...media.diagnostics(),scheduleGaps:timing.gaps,scheduleOverlaps:timing.overlaps};result.textContent=(d.decodedFrames>900&&d.audioPeak>0&&!d.audioUnderruns&&!timing.gaps&&!timing.overlaps?'PASS':'FAIL')+' — synthetic jittered player test\\n'+JSON.stringify(d,null,2);},1000);}
 },5);
};
window.addEventListener('pagehide',()=>media.close());`);
await build({entryPoints:[entry],bundle:true,format:'iife',target:'chrome79',alias:{stream:path.join(webRoot,'src/browser-stream.js')},outfile:path.join(out,'app.js')});
console.log('Open in the official Simulator: '+out);
