// Build an explicitly synthetic, SDK-only test of the production media player.
// It does not exercise AirPlay pairing, sockets or the service audio decoder.
import {build,webRoot} from '../../../tv-web/scripts/build.mjs';
import {mkdir,copyFile,writeFile,access} from 'node:fs/promises';
import path from 'node:path';
const platform=path.resolve(webRoot,'../platforms/webos'),out=path.join(platform,'build/media-test');
const fixtures=path.resolve(process.argv[2]||path.join(platform,'.cache/fixtures'));
for(const name of ['pattern.h264','tone.wav'])await access(path.join(fixtures,name));
await mkdir(out,{recursive:true});
for(const name of ['pattern.h264','tone.wav'])await copyFile(path.join(fixtures,name),path.join(out,name));
await copyFile(path.join(platform,'icon.png'),path.join(out,'icon.png'));
await writeFile(path.join(out,'appinfo.json'),JSON.stringify({id:'com.mehdiskan.airplaytv.mediatest',version:'0.2.0',type:'web',main:'index.html',title:'Synthetic media test',icon:'icon.png',resolution:'1920x1080',disableBackHistoryAPI:true}));
await writeFile(path.join(out,'index.html'),'<!doctype html><html><head><meta charset="utf-8"><title>Synthetic media test</title><style>body{margin:0;background:#101820;color:white;font:28px sans-serif}video{width:100vw;height:100vh;object-fit:contain}aside{position:fixed;bottom:0;left:0;background:#101820ed;padding:20px;z-index:10;max-width:95vw}button{font:inherit;padding:12px;background:#30a9fb;border:0;border-radius:12px}pre{white-space:pre-wrap}</style></head><body><video id="test-video" playsinline></video><aside><b>SYNTHETIC MEDIA TEST — no AirPlay transport</b><p><button id="play">Play H264 + PCM fixture</button></p><pre id="result">Ready</pre></aside><script src="app.js"></script></body></html>');
const entry=path.join(out,'entry.mjs');
await writeFile(entry,`import {WebosMedia} from ${JSON.stringify(path.join(webRoot,'src/webos-media.js'))};
const video=document.getElementById('test-video'),result=document.getElementById('result');
const media=new WebosMedia({video,onEvent:event=>{if(event.type==='error')result.textContent=event.message;}});
window.mediaSelftest=media;
document.getElementById('play').onclick=async()=>{
 media.start(1);const raw=new Uint8Array(await (await fetch('pattern.h264')).arrayBuffer()),wav=await (await fetch('tone.wav')).arrayBuffer();
 const offsets=[];for(let i=0;i<raw.length-5;i++){let prefix=0;if(raw[i]===0&&raw[i+1]===0){if(raw[i+2]===1)prefix=3;else if(raw[i+2]===0&&raw[i+3]===1)prefix=4;}if(prefix&&(raw[i+prefix]&31)===9){offsets.push(i);i+=prefix;}}
 const frames=offsets.map((o,i)=>raw.subarray(o,offsets[i+1]||raw.length)),view=new DataView(wav),text=new TextDecoder();let offset=12,size=0;
 while(offset<wav.byteLength-8){size=view.getUint32(offset+4,true);if(text.decode(new Uint8Array(wav,offset,4))==='data'){offset+=8;break;}offset+=8+size+(size&1);}
 const pcm=new Uint8Array(wav,offset,size),start=performance.now();let v=0,a=0;
 clearInterval(window.fixtureTimer);window.fixtureTimer=setInterval(()=>{
  const elapsed=(performance.now()-start)/1000;
  while(v<frames.length&&v/30<=elapsed)media.pushVideo(frames[v],v++/30,30);
  while(a<pcm.length&&a/4/44100<=elapsed){const end=Math.min(pcm.length,a+352*4);media.pushAudio(pcm.subarray(a,end),a/4/44100);a=end;}
  result.textContent=JSON.stringify(media.diagnostics(),null,2);
  if(v===frames.length&&a===pcm.length){clearInterval(window.fixtureTimer);setTimeout(()=>{const d=media.diagnostics();result.textContent=(d.decodedFrames>180&&d.audioPeak>0?'PASS':'FAIL')+' — synthetic player test\\n'+JSON.stringify(d,null,2);},1000);}
 },5);
};
window.addEventListener('pagehide',()=>media.close());`);
await build({entryPoints:[entry],bundle:true,format:'iife',target:'chrome79',alias:{stream:path.join(webRoot,'src/browser-stream.js')},outfile:path.join(out,'app.js')});
console.log('Open in the official Simulator: '+out);
