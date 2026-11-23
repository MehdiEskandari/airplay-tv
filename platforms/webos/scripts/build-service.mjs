import {build} from '../../../tv-web/scripts/build.mjs';
import {mkdir,copyFile,cp,rm,access} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const platform=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const webRoot=path.resolve(platform,'../../tv-web');
export const servicePath=path.join(platform,'build/service');
export async function buildService(){
  await access(path.join(platform,'build/core/airplay-core.wasm'));
  await rm(servicePath,{recursive:true,force:true});await mkdir(servicePath,{recursive:true});
  await build({entryPoints:[path.join(platform,'service/index.cjs')],bundle:true,platform:'node',format:'cjs',target:'node12',external:['webos-service'],nodePaths:[path.join(webRoot,'node_modules')],outfile:path.join(servicePath,'service.js')});
  for(const file of ['package.json','services.json'])await copyFile(path.join(platform,'service',file),path.join(servicePath,file));
  await cp(path.join(platform,'build/core'),path.join(servicePath,'core'),{recursive:true});
  await mkdir(path.join(servicePath,'licenses'),{recursive:true});
  await copyFile(path.join(webRoot,'node_modules/ws/LICENSE'),path.join(servicePath,'licenses/ws-MIT.txt'));
  console.info('Built webOS JavaScript service with portable AirPlay core');
}
if(process.argv[1]===fileURLToPath(import.meta.url))await buildService();
