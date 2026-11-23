#!/usr/bin/env python3
"""Build portable UxPlay/FFmpeg WASM for a webOS Node JavaScript service."""
import argparse,concurrent.futures,hashlib,json,os,shutil,subprocess,tarfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3];PLATFORM=ROOT/'platforms/webos'
parser=argparse.ArgumentParser()
parser.add_argument('--sdk',type=Path,default=PLATFORM/'.cache/emsdk')
parser.add_argument('--source-root',type=Path,default=ROOT,help='Checkout containing initialized protocol/FFmpeg/plist submodules')
parser.add_argument('--jobs',type=int,default=4)
parser.add_argument('--decoder-test',action='store_true',help='Also build the real audio decoder integration test')
args=parser.parse_args();sdk=args.sdk.resolve();em=sdk/'upstream/emscripten';cpp=args.source_root.resolve()/'app/src/main/cpp'
if not (em/'emcc').exists():parser.error('Install and activate upstream Emscripten 3.1.45 first')
cache=PLATFORM/'.cache';cache.mkdir(exist_ok=True);env=dict(os.environ,EM_CONFIG=str(sdk/'.emscripten'),EM_CACHE=str(cache/'emscripten'))
env.update(CC=str(em/'emcc'),CXX=str(em/'em++'),AR=str(em/'emar'),RANLIB=str(em/'emranlib'))
def run(cmd,cwd=ROOT,**kw):subprocess.run([str(x) for x in cmd],cwd=cwd,env=env,check=True,**kw)
version=subprocess.check_output([str(em/'emcc'),'--version'],env=env,text=True)
if '3.1.45' not in version:parser.error('This build is pinned to upstream Emscripten 3.1.45')
openssl=cache/'openssl-3.4.4'
if not openssl.exists():
    archive=next((args.source_root/'app/.cxx').glob('**/openssl-3.4.4.tar.gz'),cache/'openssl-3.4.4.tar.gz')
    if not archive.exists():run(['curl','--fail','--location','--silent','--show-error','https://www.openssl.org/source/openssl-3.4.4.tar.gz','--output',archive])
    if hashlib.sha256(archive.read_bytes()).hexdigest()!='7bdf55ac20f2779e99e5eca306f824fad2b37dee5a06cc35ed5a8b85a6060010':raise RuntimeError('OpenSSL source hash mismatch')
    with tarfile.open(archive) as tar:tar.extractall(cache,filter='data')
if not (openssl/'libcrypto.a').exists():
    run(['perl','Configure','linux-generic32','no-asm','no-shared','no-dso','no-tests','no-async','no-engine','no-apps','no-docs','no-legacy','no-module','no-sock','-O2','-pthread'],openssl)
    run(['make',f'-j{args.jobs}','build_generated','libcrypto.a'],openssl)
ff=cache/'ffmpeg-build';ff.mkdir(exist_ok=True)
if not (ff/'libavcodec/libavcodec.a').exists():
    run([cpp/'third_party/ffmpeg/configure','--target-os=none','--arch=wasm32','--cpu=generic','--enable-cross-compile','--cc='+str(em/'emcc'),'--cxx='+str(em/'em++'),'--ar='+str(em/'emar'),'--ranlib='+str(em/'emranlib'),'--nm='+str(sdk/'upstream/bin/llvm-nm'),'--disable-asm','--disable-x86asm','--disable-all','--disable-debug','--disable-autodetect','--disable-network','--disable-pthreads','--disable-doc','--disable-programs','--enable-avcodec','--enable-avutil','--enable-decoder=alac,aac','--enable-static','--disable-shared','--extra-cflags=-O2 -pthread','--extra-ldflags=-pthread'],ff)
    run(['make',f'-j{args.jobs}'],ff)
uxrepo=cpp/'third_party/UxPlay';revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=uxrepo,text=True).strip()
patches=sorted((ROOT/'app/src/main/cpp/patches/UxPlay').glob('*.patch'))+sorted((PLATFORM/'native').glob('*.patch'))
stamp=hashlib.sha256((revision+''.join(p.read_text() for p in patches)).encode()).hexdigest()[:16]
ux=cache/('UxPlay-'+stamp)
if not ux.exists():
    ux.mkdir();archive=cache/'uxplay.tar'
    with archive.open('wb') as f:run(['git','archive','HEAD'],uxrepo,stdout=f)
    with tarfile.open(archive) as tar:tar.extractall(ux,filter='data')
    run(['git','init','--quiet'],ux)
    for patch in patches:run(['git','apply','--unidiff-zero',patch],ux)
lib=ux/'lib';plist=cpp/'third_party/libplist'
sources=[lib/(name+'.c') for name in ['airplay_video','byteutils','compat','crypto','fairplay_playfair','http_request','http_response','httpd','logger','mirror_buffer','netutils','pairing','raop','raop_buffer','raop_ntp','raop_rtp','raop_rtp_mirror','srp','utils']]
sources += [lib/'llhttp'/name for name in ['api.c','http.c','llhttp.c']]
sources += [lib/'playfair'/name for name in ['hand_garble.c','modified_md5.c','omg_hax.c','playfair.c','sap_hash.c']]
sources += [plist/'src'/name for name in ['base64.c','bplist.c','bytearray.c','hashtable.c','jplist.c','jsmn.c','oplist.c','out-default.c','out-limd.c','out-plutil.c','plist.c','ptrarray.c','time64.c','xplist.c']]
sources += [plist/'libcnary'/name for name in ['node.c','node_list.c']]
sources += [cpp/'android_dnssd_shim.c']+sorted((PLATFORM/'native').glob('*.cpp'))+[PLATFORM/'native/sockets.c']
includes=[lib,lib/'llhttp',lib/'playfair',cpp,plist/'include',plist/'src',plist/'libcnary/include',openssl/'include',ff,cpp/'third_party/ffmpeg']
flags=['-O2','-pthread','-D_GNU_SOURCE','-DHAVE_STRNDUP','-DPACKAGE_VERSION="2.6.0"','-DPLIST_210','-DTARGET_RT_LITTLE_ENDIAN=1','-include',str(PLATFORM/'native/socket-mapping.h')]+['-I'+str(p) for p in includes]
objects=PLATFORM/'build/objects';objects.mkdir(parents=True,exist_ok=True)
def compile_one(src):
    obj=objects/(hashlib.sha256(str(src).encode()).hexdigest()[:16]+'.o')
    run([em/('em++' if src.suffix=='.cpp' else 'emcc'),*flags,'-std=c++17' if src.suffix=='.cpp' else '-std=gnu11','-c',src,'-o',obj]);return obj
with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:compiled=list(pool.map(compile_one,sources))
out=PLATFORM/'build/core';out.mkdir(parents=True,exist_ok=True)
run([em/'em++',*compiled,ff/'libavcodec/libavcodec.a',ff/'libavutil/libavutil.a',openssl/'libcrypto.a','-O2','-pthread','--js-library',PLATFORM/'native/socket-library.js','-s','PROXY_TO_PTHREAD=1','-s','PTHREAD_POOL_SIZE=16','-s','INITIAL_MEMORY=134217728','-s','ALLOW_MEMORY_GROWTH=1','-s','MAXIMUM_MEMORY=268435456','-s','MODULARIZE=1','-s','EXPORT_NAME=AirPlayCore','-s','ENVIRONMENT=node,worker','-s','MIN_NODE_VERSION=120000','-s','EXIT_RUNTIME=0','-s','FORCE_FILESYSTEM=1','-s','EXPORTED_FUNCTIONS=["_main","_airplay_start","_airplay_stop","_airplay_approve","_airplay_playback","_malloc","_free"]','-s','EXPORTED_RUNTIME_METHODS=["ccall","FS","NODEFS","PThread"]','-lnodefs.js','-o',out/'airplay-core.js'])
licenses=out/'licenses';licenses.mkdir(exist_ok=True)
for name,source in [('UxPlay.txt',ux/'LICENSE'),('FFmpeg.txt',cpp/'third_party/ffmpeg/COPYING.LGPLv2.1'),('libplist.txt',plist/'COPYING.LESSER'),('OpenSSL.txt',openssl/'LICENSE.txt'),('Emscripten.txt',em/'LICENSE')]:shutil.copyfile(source,licenses/name)
(out/'build-info.json').write_text(json.dumps({'emscripten':'3.1.45','uxplay':revision,'patches':stamp,'ffmpeg':subprocess.check_output(['git','rev-parse','HEAD'],cwd=cpp/'third_party/ffmpeg',text=True).strip(),'openssl':'3.4.4','target':'webOS Node service; physical verification pending'},indent=2)+'\n')
print('Built portable webOS service core')
if args.decoder_test:
    run([em/'em++',PLATFORM/'tests/player.cpp',PLATFORM/'native/player.cpp','-std=c++17','-O2','-pthread','-I'+str(ff),'-I'+str(cpp/'third_party/ffmpeg'),ff/'libavcodec/libavcodec.a',ff/'libavutil/libavutil.a','--no-entry','-s','MODULARIZE=1','-s','EXPORT_NAME=DecoderTest','-s','ENVIRONMENT=node,worker','-s','MIN_NODE_VERSION=120000','-s','PTHREAD_POOL_SIZE=1','-s','INITIAL_MEMORY=67108864','-s','EXPORTED_FUNCTIONS=["_test_reset","_test_audio","_malloc","_free"]','-s','EXPORTED_RUNTIME_METHODS=["ccall","PThread"]','-o',PLATFORM/'build/decoder-test.js'])
