#!/usr/bin/env python3
"""A bounded hardware decoder test, independent of installing the widget."""
from pathlib import Path
import argparse,json,time,urllib.request,xml.etree.ElementTree as ET

P=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--tv',required=True);parser.add_argument('--host',required=True);parser.add_argument('--seconds',type=int,default=8)
args=parser.parse_args();token=(P/'.cache/runtime/widget-token').read_text().strip()
url=f'http://{args.host}:4188/media/{token}/hardware-test/sample.mp4'
service='urn:schemas-upnp-org:service:AVTransport:1'
def call(name,values):
 envelope=ET.Element('s:Envelope',{'xmlns:s':'http://schemas.xmlsoap.org/soap/envelope/','s:encodingStyle':'http://schemas.xmlsoap.org/soap/encoding/'})
 body=ET.SubElement(envelope,'s:Body');action=ET.SubElement(body,'u:'+name,{'xmlns:u':service})
 for key,value in values.items():ET.SubElement(action,key).text=str(value)
 request=urllib.request.Request('http://'+args.tv+':7676/smp_22_',data=ET.tostring(envelope),headers={'Content-Type':'text/xml; charset="utf-8"','SOAPAction':'"'+service+'#'+name+'"'})
 response=ET.fromstring(urllib.request.urlopen(request,timeout=4).read());return {e.tag.split('}')[-1]:e.text for e in list(response)[0][0]}
before=call('GetTransportInfo',{'InstanceID':0})
if before.get('CurrentTransportState')!='NO_MEDIA_PRESENT':raise SystemExit('TV already has a media session; leaving it untouched.')
metadata=ET.Element('DIDL-Lite',{'xmlns':'urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/','xmlns:dc':'http://purl.org/dc/elements/1.1/','xmlns:upnp':'urn:schemas-upnp-org:metadata-1-0/upnp/'})
item=ET.SubElement(metadata,'item',{'id':'airplay-legacy-test','parentID':'0','restricted':'1'});ET.SubElement(item,'dc:title').text='AirPlay TV Legacy playback test';ET.SubElement(item,'upnp:class').text='object.item.videoItem'
ET.SubElement(item,'res',{'protocolInfo':'http-get:*:video/mp4:*'}).text=url
result={'startedAt':time.time(),'format':'H.264/AAC 720p MP4','positions':[],'widgetInstalled':False}
try:
 call('SetAVTransportURI',{'InstanceID':0,'CurrentURI':url,'CurrentURIMetaData':ET.tostring(metadata,encoding='unicode')})
 call('Play',{'InstanceID':0,'Speed':1})
 for _ in range(min(args.seconds,15)):
  time.sleep(1);state=call('GetTransportInfo',{'InstanceID':0});position=call('GetPositionInfo',{'InstanceID':0})
  entry={'state':state.get('CurrentTransportState'),'time':position.get('RelTime'),'duration':position.get('TrackDuration')};result['positions'].append(entry);print(entry,flush=True)
except Exception as error:result['error']=str(error);print(type(error).__name__,str(error),flush=True)
finally:
 try:call('Stop',{'InstanceID':0})
 except Exception as error:result['stopError']=str(error)
 (P/'.cache/tv-playback-test.json').write_text(json.dumps(result,indent=2)+'\n')
print('Transport position is decoder evidence; visible image and audible sound still require confirmation on the TV.',flush=True)
