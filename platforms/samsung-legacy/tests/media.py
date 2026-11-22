#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,tempfile
import imageio_ffmpeg

p=Path(__file__).resolve().parents[1];root=p.parents[1];ff=p/'.cache/ffmpeg-host';source=root/'app/src/main/cpp/third_party/ffmpeg'
with tempfile.TemporaryDirectory(prefix='airplay-legacy-media-') as temp:
 temp=Path(temp);encoder=imageio_ffmpeg.get_ffmpeg_exe()
 subprocess.run([encoder,'-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=red:s=240x320:r=30','-t','0.5','-c:v','libx264','-preset','ultrafast','-g','1','-f','h264',str(temp/'portrait.h264')],check=True)
 subprocess.run([encoder,'-hide_banner','-loglevel','error','-f','lavfi','-i','sine=frequency=440:sample_rate=44100','-t','0.5','-ac','2','-c:a','aac','-f','adts',str(temp/'audio.aac')],check=True)
 command=['clang++','-std=c++17','-fsanitize=address,undefined','-g','-pthread','-I'+str(p/'bridge'),'-I'+str(root/'app/src/main/cpp/third_party/UxPlay/lib'),'-I'+str(source),'-I'+str(ff),str(p/'tests/media.cpp'),str(p/'bridge/media.cpp'),str(ff/'libswscale/libswscale.a'),str(ff/'libavcodec/libavcodec.a'),str(ff/'libavutil/libavutil.a'),'-o',str(temp/'media-test')]
 subprocess.run(command,check=True);subprocess.run([str(temp/'media-test'),str(temp/'portrait.h264'),str(temp/'audio.aac')],check=True)
