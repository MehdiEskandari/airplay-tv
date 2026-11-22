#!/usr/bin/env python3
"""Build the existing AirPlay protocol core with Samsung's socket-enabled SDK.

Stock Emscripten is deliberately rejected: it cannot listen for native AirPlay
TCP/UDP connections on a TV. All products/caches remain inside this platform.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[3]
PLATFORM = ROOT / 'platforms/tizen'
CPP = ROOT / 'app/src/main/cpp'
parser = argparse.ArgumentParser()
parser.add_argument('--sdk', required=True, type=Path, help='Samsung emsdk directory containing fastcomp/')
parser.add_argument('--python', default=Path(os.sys.executable), type=Path, help='Python with setuptools (distutils compatibility)')
parser.add_argument('--jobs', default=4, type=int)
args = parser.parse_args()
sdk = args.sdk.resolve() / 'fastcomp/emscripten'
if not (sdk / 'system/include/samsung/wasm/elementary_media_stream_source.h').exists():
    parser.error('Samsung Emscripten 1.39.4.7 is required, not upstream Emscripten')
cache = PLATFORM / '.cache'
cache.mkdir(parents=True, exist_ok=True)
tools = cache / 'sdk-tools'
tools.mkdir(exist_ok=True)

def run(argv, cwd=ROOT, **kw):
    subprocess.run([str(x) for x in argv], cwd=cwd, env=env, check=True, **kw)

# The official macOS bundle builds C++17 constexpr lambdas as C++14. Correct
# only the Samsung libraries' flags, leaving the application toolchain intact.
system_libs = sdk / 'tools/system_libs.py'
source = system_libs.read_text()
begin = source.index('class lib_tizen_emss(')
end = source.index('# If main()', begin)
patched = source[:begin] + source[begin:end].replace('-std=c++14', '-std=c++17').replace('(CXXLibrary):','(CXXLibrary, MTLibrary):') + source[end:]
if patched != source:
    system_libs.write_text(patched)
config = tools / 'emscripten.config'
node_candidates = sorted((args.sdk.resolve() / 'node').glob('*/bin/node'))
if not node_candidates:
    parser.error('Bundled Samsung Node runtime is missing')
config_text='\n'.join(f'{k} = {str(v)!r}' for k,v in {
    'LLVM_ROOT': sdk.parent/'bin', 'BINARYEN_ROOT': sdk.parent,
    'EMSCRIPTEN_ROOT': sdk, 'NODE_JS': node_candidates[0],
    'TEMP_DIR': cache/'tmp'}.items()) + '\nJS_ENGINES=[NODE_JS]\nCOMPILER_ENGINE=NODE_JS\n'
if not config.exists() or config.read_text()!=config_text:
    config.write_text(config_text)
env = dict(os.environ, EM_CONFIG=str(config), EM_CACHE=str(cache/'emscripten'),
           EMCC_CORES=str(args.jobs), PATH=str(tools)+os.pathsep+os.environ['PATH'])
for name, entry in [('emcc','emcc'),('em++','em++.py'),('emar','emar.py'),('emranlib','emranlib.py')]:
    wrapper = tools/name
    wrapper.write_text('#!/bin/sh\nexec '+shlex.quote(str(args.python.absolute()))+' '+shlex.quote(str(sdk/entry))+' "$@"\n')
    wrapper.chmod(0o755)

openssl = cache/'openssl-3.4.4'
if not openssl.exists():
    archive = next((ROOT/'app/.cxx').glob('**/openssl-3.4.4.tar.gz'), None)
    if archive is None:
        archive=cache/'openssl-3.4.4.tar.gz'
        urllib.request.urlretrieve('https://www.openssl.org/source/openssl-3.4.4.tar.gz',archive)
    expected='7bdf55ac20f2779e99e5eca306f824fad2b37dee5a06cc35ed5a8b85a6060010'
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected:
        raise RuntimeError('OpenSSL archive does not match the official 3.4.4 SHA256')
    with tarfile.open(archive) as tar: tar.extractall(cache,filter='data')
if not (openssl/'libcrypto.a').exists():
    env.update(CC=str(tools/'emcc'),AR=str(tools/'emar'),RANLIB=str(tools/'emranlib'))
    run(['perl','Configure','linux-generic32','no-asm','no-shared','no-dso','no-tests','no-async','no-engine','no-apps','no-docs','no-legacy','no-module','no-sock','-O2','-pthread'],openssl)
    run(['make',f'-j{args.jobs}','build_generated','libcrypto.a'],openssl)

ff = cache/'ffmpeg-build'
ff.mkdir(exist_ok=True)
# FFmpeg names configure-test executables without an extension. This SDK
# interprets those as relocatable objects, falsely reporting every libc symbol
# as available. Give actual link probes a .js suffix and preserve their result.
ffcc=tools/'ffcc'
ffcc.write_text('#!'+str(Path(os.sys.executable).absolute())+'\n'+
    'import os,sys,subprocess\n'+
    'args=sys.argv[1:]; original=None; output=None\n'+
    'if "-o" in args and not any(x in args for x in ["-c","-E","-S","-r"]):\n'+
    ' i=args.index("-o")+1\n'+
    ' if not os.path.splitext(args[i])[1]:\n'+
    '  original=args[i]; output=original+".js"; args[i]=output\n'+
    'result=subprocess.run(['+repr(str(tools/'emcc'))+']+args).returncode\n'+
    'if result==0 and original: os.replace(output,original)\n'+
    'sys.exit(result)\n')
ffcc.chmod(0o755)
if not (ff/'libavcodec/libavcodec.a').exists():
    run([CPP/'third_party/ffmpeg/configure','--target-os=none','--arch=wasm32','--cpu=generic','--enable-cross-compile',
         '--cc='+str(ffcc),'--cxx='+str(tools/'em++'),'--ar='+str(tools/'emar'),'--ranlib='+str(tools/'emranlib'),
         '--nm='+str(sdk.parent/'bin/llvm-nm'),'--disable-asm','--disable-x86asm','--disable-all','--disable-debug',
         '--disable-autodetect','--disable-network','--disable-pthreads','--disable-doc','--disable-programs',
         '--enable-avcodec','--enable-avutil','--enable-decoder=alac,aac','--enable-static','--disable-shared','--extra-cflags=-O2 -pthread',
         '--extra-ldflags=-pthread -s ERROR_ON_UNDEFINED_SYMBOLS=1'],ff)
    run(['make',f'-j{args.jobs}'],ff)

# Export a clean UxPlay revision and apply the same fixes as Android. Never
# reset or build by mutating the shared submodule's dirty working tree.
uxRepo=CPP/'third_party/UxPlay'
revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=uxRepo,text=True).strip()
patches=sorted((CPP/'patches/UxPlay').glob('*.patch'))
patches+=sorted((PLATFORM/'native').glob('*.patch'))
stamp=hashlib.sha256(('isolated-git-v2'+revision+''.join(p.read_text() for p in patches)).encode()).hexdigest()[:16]
ux=cache/('UxPlay-'+stamp)
if ux.exists() and not (ux/'.tizen-patches-applied').exists():
    shutil.rmtree(ux) # Only our generated archive copy, never the source submodule.
if not ux.exists():
    ux.mkdir()
    archive=cache/'uxplay.tar'
    with archive.open('wb') as out: run(['git','archive','HEAD'],uxRepo,stdout=out)
    with tarfile.open(archive) as tar: tar.extractall(ux,filter='data')
    run(['git','init','--quiet'],ux)
    for patch in patches: run(['git','apply','--unidiff-zero',patch],ux)
    (ux/'.tizen-patches-applied').write_text(stamp)
if 'const char *verified_key' not in (ux/'lib/raop.h').read_text():
    raise RuntimeError('The Tizen sender-identity patch was not applied')
lib=ux/'lib'
plist=CPP/'third_party/libplist'
sources=[lib/(name+'.c') for name in ['airplay_video','byteutils','compat','crypto','fairplay_playfair','http_request','http_response','httpd','logger','mirror_buffer','netutils','pairing','raop','raop_buffer','raop_ntp','raop_rtp','raop_rtp_mirror','srp','utils']]
sources += [lib/'llhttp'/name for name in ['api.c','http.c','llhttp.c']]
sources += [lib/'playfair'/name for name in ['hand_garble.c','modified_md5.c','omg_hax.c','playfair.c','sap_hash.c']]
sources += [plist/'src'/name for name in ['base64.c','bplist.c','bytearray.c','hashtable.c','jplist.c','jsmn.c','oplist.c','out-default.c','out-limd.c','out-plutil.c','plist.c','ptrarray.c','time64.c','xplist.c']]
sources += [plist/'libcnary'/name for name in ['node.c','node_list.c']]
sources += [CPP/'android_dnssd_shim.c'] + sorted((PLATFORM/'native').glob('*.cpp'))
includes=[lib,lib/'llhttp',lib/'playfair',CPP,plist/'include',plist/'src',plist/'libcnary/include',openssl/'include',ff,CPP/'third_party/ffmpeg']
flags=['-O2','-pthread','-D_GNU_SOURCE','-DHAVE_STRNDUP','-DPACKAGE_VERSION="2.6.0"','-DPLIST_210','-DTARGET_RT_LITTLE_ENDIAN=1']
flags += ['-I'+str(p) for p in includes]
objects=PLATFORM/'build/objects';objects.mkdir(parents=True,exist_ok=True)
def compile_one(src):
    obj=objects/(hashlib.sha256(str(src).encode()).hexdigest()[:16]+'.o')
    compiler=tools/('em++' if src.suffix=='.cpp' else 'emcc')
    run([compiler,*flags,'-std=c++17' if src.suffix=='.cpp' else '-std=gnu11','-c',src,'-o',obj])
    return obj
with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
    compiled=list(pool.map(compile_one,sources))
out=PLATFORM/'build/core';out.mkdir(parents=True,exist_ok=True)
run([tools/'em++',*compiled,ff/'libavcodec/libavcodec.a',ff/'libavutil/libavutil.a',openssl/'libcrypto.a',
     '-O2','-pthread','-s','USE_PTHREADS=1','-s','PROXY_TO_PTHREAD=1','-s','PTHREAD_POOL_SIZE=16',
     '-s','TOTAL_MEMORY=134217728','-s','ALLOW_MEMORY_GROWTH=1','-s','WASM_MEM_MAX=536870912',
     '-s','MODULARIZE=1','-s','EXPORT_NAME=AirPlayCore','-s','ENVIRONMENT=web,worker','-s','NO_EXIT_RUNTIME=1',
     '-s','EXPORTED_FUNCTIONS=["_main","_airplay_start","_airplay_stop","_airplay_approve","_airplay_playback","_malloc","_free"]',
     '-s','EXTRA_EXPORTED_RUNTIME_METHODS=["ccall"]','-lidbfs.js','--pre-js',PLATFORM/'native/persist.js',
     '-o',out/'airplay-core.js'])
licenses=out/'licenses';licenses.mkdir(exist_ok=True)
for name,file in [('UxPlay.txt',ux/'LICENSE'),('FFmpeg.txt',CPP/'third_party/ffmpeg/COPYING.LGPLv2.1'),
                  ('libplist.txt',plist/'COPYING.LESSER'),('OpenSSL.txt',openssl/'LICENSE.txt'),('Samsung-Emscripten.txt',sdk/'LICENSE')]:
    shutil.copyfile(file,licenses/name)
(out/'build-info.json').write_text(json.dumps({'sdk':'Samsung 1.39.4.7','uxplay':revision,'patches':stamp,'openssl':'3.4.4','runtimeVerified':False},indent=2)+'\n')
print('Built Samsung AirPlay core. Hardware playback still requires verification on a compatible TV.')
