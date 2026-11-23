import {build} from 'esbuild';
import {execFileSync} from 'node:child_process';
import {mkdir, copyFile, cp, access, rm} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

export const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export {build};
export async function buildWeb({platform='tizen'}={}) {
  if(!['tizen','webos'].includes(platform))throw new Error('Unknown TV platform: '+platform);
  // Rebuilding for LG must not retain a previously copied Samsung core.
  await rm(path.join(webRoot,'dist'),{recursive:true,force:true});
  execFileSync('python3', [path.join(webRoot, 'scripts/export-assets.py')], {stdio: 'inherit'});
  await build({entryPoints: [path.join(webRoot, 'src/app.js')], alias:{'#airplay-platform':path.join(webRoot,'src/platforms',platform+'.js'),...(platform==='webos'?{stream:path.join(webRoot,'src/browser-stream.js')}:{})},bundle: true, target: [platform==='webos'?'chrome79':'chrome69'], format: 'iife', outfile: path.join(webRoot, 'dist/app.js'), sourcemap: true, minify: false, legalComments: 'eof'});
  await copyFile(platform==='webos'?path.resolve(webRoot,'../platforms/webos/index.html'):path.join(webRoot, 'index.html'), path.join(webRoot, 'dist/index.html'));
  await copyFile(path.join(webRoot, 'src/style.css'), path.join(webRoot, 'dist/style.css'));
  if(platform==='webos'){console.info('Built webOS interface and media player; packaging also requires the service core.');return;}
  const native = path.resolve(webRoot, '../platforms/tizen/build/core');
  try { await access(path.join(native, 'airplay-core.wasm')); await mkdir(path.join(webRoot, 'dist/core'), {recursive: true}); await cp(native, path.join(webRoot, 'dist/core'), {recursive: true}); }
  catch { console.info('Native receiver not built yet; browser UI preview is available. Packaging requires the core.'); }
}
if (process.argv[1] === fileURLToPath(import.meta.url)) await buildWeb({platform:process.argv.includes('--webos')?'webos':'tizen'});
