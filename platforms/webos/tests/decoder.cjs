'use strict';
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=path.resolve(__dirname,'..');
(async()=>{
 const packets=[];
 const core=await require(path.join(root,'build/decoder-test.js'))({onAirPlayMedia:packet=>packets.push(packet)});
 try{
  for(const [name,codec,spf] of [['aac-lc',4,1024],['alac',2,4096]]){
   core.ccall('test_reset',null,[],[]);packets.length=0;
   const bytes=fs.readFileSync(path.join(__dirname,'fixtures',name+'.packets'));let offset=0,index=0;
   while(offset<bytes.length){const size=bytes.readUInt32LE(offset);offset+=4;const p=core._malloc(size);core.HEAPU8.set(bytes.subarray(offset,offset+size),p);
    assert.equal(core.ccall('test_audio','number',['number','number','number','number','number'],[p,size,codec,spf,index++*spf/44100]),1,name+' packet decoded');core._free(p);offset+=size;}
   assert.ok(packets.length>0,name+' produced PCM');let peak=0,samples=0;
   for(const packet of packets){assert.equal(packet.kind,'audio');const view=new DataView(packet.data.buffer,packet.data.byteOffset,packet.data.byteLength);for(let i=0;i<view.byteLength;i+=2)peak=Math.max(peak,Math.abs(view.getInt16(i,true)));samples+=view.byteLength/4;}
   assert.ok(peak>1000&&peak<32000,name+' decoded a non-silent, unclipped tone');assert.ok(samples>=spf,name+' sample count');
   console.log(name+': decoded '+samples+' stereo PCM samples, peak '+peak);
  }
 }finally{core.PThread?.terminateAllThreads();}
})().catch(error=>{console.error(error);process.exitCode=1;});
