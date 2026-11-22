import {build} from 'esbuild';
import {execFileSync} from 'node:child_process';
import {mkdir, copyFile, cp, access} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

export const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export async function buildWeb() {
  execFileSync('python3', [path.join(webRoot, 'scripts/export-assets.py')], {stdio: 'inherit'});
  await build({entryPoints: [path.join(webRoot, 'src/app.js')], bundle: true, target: ['chrome69'], format: 'iife', outfile: path.join(webRoot, 'dist/app.js'), sourcemap: true, minify: false, legalComments: 'eof'});
  await copyFile(path.join(webRoot, 'index.html'), path.join(webRoot, 'dist/index.html'));
  await copyFile(path.join(webRoot, 'src/style.css'), path.join(webRoot, 'dist/style.css'));
  const native = path.resolve(webRoot, '../platforms/tizen/build/core');
  try { await access(path.join(native, 'airplay-core.wasm')); await mkdir(path.join(webRoot, 'dist/core'), {recursive: true}); await cp(native, path.join(webRoot, 'dist/core'), {recursive: true}); }
  catch { console.info('Native receiver not built yet; browser UI preview is available. Packaging requires the core.'); }
}
if (process.argv[1] === fileURLToPath(import.meta.url)) await buildWeb();
