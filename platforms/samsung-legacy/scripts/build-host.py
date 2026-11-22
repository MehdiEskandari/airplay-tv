#!/usr/bin/env python3
"""Build the Mac companion from the existing patched AirPlay protocol core."""
from pathlib import Path
import concurrent.futures,hashlib,json,os,platform,shutil,subprocess,tarfile

ROOT=Path(__file__).resolve().parents[3];P=ROOT/'platforms/samsung-legacy';CPP=ROOT/'app/src/main/cpp'
cache=P/'.cache';cache.mkdir(exist_ok=True)
def run(argv,cwd=ROOT,**kwargs):subprocess.run([str(x) for x in argv],cwd=cwd,check=True,**kwargs)
if platform.system()!='Darwin':raise SystemExit('The initial companion uses macOS Bonjour. Build on the Mac connected to the TV.')
ff=cache/'ffmpeg-host';ff.mkdir(exist_ok=True)
if not (ff/'libswscale/libswscale.a').exists():
 run([CPP/'third_party/ffmpeg/configure','--target-os=darwin','--arch='+('aarch64' if platform.machine()=='arm64' else 'x86_64'),
      '--cc=clang','--cxx=clang++','--disable-asm','--disable-x86asm','--disable-all','--disable-debug',
      '--disable-network','--disable-autodetect','--disable-programs','--disable-doc','--enable-pic','--enable-static','--disable-shared',
      '--enable-avcodec','--enable-avutil','--enable-swscale','--enable-decoder=h264,alac,aac','--enable-parser=h264'],ff)
 run(['make','-j6'],ff)
uxRepo=CPP/'third_party/UxPlay'
revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=uxRepo,text=True).strip()
patches=sorted((CPP/'patches/UxPlay').glob('*.patch'))+[ROOT/'platforms/tizen/native/verified-sender.patch']
stamp=hashlib.sha256((revision+''.join(x.read_text() for x in patches)).encode()).hexdigest()[:16]
ux=cache/('UxPlay-'+stamp)
if ux.exists() and not (ux/'.patched').exists():shutil.rmtree(ux)
if not ux.exists():
 ux.mkdir();archive=cache/'uxplay.tar'
 with archive.open('wb') as out:run(['git','archive','HEAD'],uxRepo,stdout=out)
 with tarfile.open(archive) as t:t.extractall(ux,filter='data')
 run(['git','init','--quiet'],ux)
 for patch in patches:run(['git','apply','--unidiff-zero',patch],ux)
 (ux/'.patched').write_text(stamp)
lib=ux/'lib';plist=CPP/'third_party/libplist';openssl=Path('/opt/homebrew/opt/openssl@3')
if not openssl.exists():openssl=Path('/usr/local/opt/openssl@3')
if not (openssl/'include/openssl/evp.h').exists():raise SystemExit('Homebrew OpenSSL 3 headers are required.')
sources=[lib/(x+'.c') for x in ['airplay_video','byteutils','compat','crypto','fairplay_playfair','http_request','http_response','httpd','logger','mirror_buffer','netutils','pairing','raop','raop_buffer','raop_ntp','raop_rtp','raop_rtp_mirror','srp','utils']]
sources += [lib/'llhttp'/x for x in ['api.c','http.c','llhttp.c']]
sources += [lib/'playfair'/x for x in ['hand_garble.c','modified_md5.c','omg_hax.c','playfair.c','sap_hash.c']]
sources += [plist/'src'/x for x in ['base64.c','bplist.c','bytearray.c','hashtable.c','jplist.c','jsmn.c','oplist.c','out-default.c','out-limd.c','out-plutil.c','plist.c','ptrarray.c','time64.c','xplist.c']]
sources += [plist/'libcnary/node.c',plist/'libcnary/node_list.c',CPP/'android_dnssd_shim.c']+sorted((P/'bridge').glob('*.cpp'))
includes=[lib,lib/'llhttp',lib/'playfair',CPP,plist/'include',plist/'src',plist/'libcnary/include',openssl/'include',ff,CPP/'third_party/ffmpeg']
flags=['-O2','-pthread','-DHAVE_STRNDUP','-DPACKAGE_VERSION="2.6.0"','-DPLIST_210','-DTARGET_RT_LITTLE_ENDIAN=1','-Wno-deprecated-declarations']+['-I'+str(x) for x in includes]
objects=P/'build/objects';objects.mkdir(parents=True,exist_ok=True)
def compile_one(src):
 obj=objects/(hashlib.sha256(str(src).encode()).hexdigest()[:16]+'.o')
 run(['clang++' if src.suffix=='.cpp' else 'clang',*flags,'-std=c++17' if src.suffix=='.cpp' else '-std=gnu11','-c',src,'-o',obj]);return obj
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:compiled=list(pool.map(compile_one,sources))
binary=P/'build/airplay-legacy-host'
run(['clang++',*compiled,ff/'libswscale/libswscale.a',ff/'libavcodec/libavcodec.a',ff/'libavutil/libavutil.a',
     '-L'+str(openssl/'lib'),'-lcrypto','-pthread','-o',binary])
(P/'build/host-build.json').write_text(json.dumps({'uxplay':revision,'patches':stamp,'architecture':platform.machine()},indent=2)+'\n')
print('Built Mac companion:',binary)
