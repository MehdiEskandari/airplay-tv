import {access,copyFile,cp,mkdir,readFile,rm} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {buildWeb,webRoot} from '../../../tv-web/scripts/build.mjs';

const platform=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const argv=process.argv.slice(2),unsigned=argv.includes('--unsigned'),profileIndex=argv.indexOf('--profile');
const profile=profileIndex<0?null:argv[profileIndex+1];
if(!unsigned&&!profile)throw new Error('Choose --unsigned for a development archive, or --profile <Samsung certificate profile> for signing.');
await access(path.join(platform,'build/core/airplay-core.js'));
const wasm=await readFile(path.join(platform,'build/core/airplay-core.wasm'));
if(wasm.length<8||wasm.readUInt32LE(0)!==0x6d736100)throw new Error('A compiled Samsung AirPlay core is required. UI-only packages are refused.');
await access(path.join(platform,'build/core/airplay-core.worker.js'));
await buildWeb();
const staging=path.join(platform,'build/widget');
await rm(staging,{recursive:true,force:true});await mkdir(staging,{recursive:true});
await cp(path.join(webRoot,'dist'),staging,{recursive:true,filter:src=>!src.endsWith('.map')});
await copyFile(path.join(platform,'config.xml'),path.join(staging,'config.xml'));
const output=path.join(platform,'output');await mkdir(output,{recursive:true});
if(unsigned){
  const artifact=path.join(output,'AirPlay-TV-Tizen-1.5.0-unsigned.wgt');await rm(artifact,{force:true});
  execFileSync('zip',['-qr',artifact,'.'],{cwd:staging,stdio:'inherit'});
  console.info(artifact+'\nUNSIGNED development archive. Not installable or store-ready until signed with a Samsung TV certificate.');
}else{
  execFileSync(process.env.TIZEN_CLI||'tizen',['package','-t','wgt','-s',profile,'-o',output,'--',staging],{stdio:'inherit'});
}
