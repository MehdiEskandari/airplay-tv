#!/usr/bin/env python3
"""Check the actual LG IPK, including platform isolation and icon dimensions."""
from pathlib import Path
import io,json,struct,tarfile

root=Path(__file__).resolve().parents[1]
metadata=json.loads((root/'appinfo.json').read_text())
artifact=root/'output'/f"{metadata['id']}_{metadata['version']}_all.ipk"
data=artifact.read_bytes()
assert data[:8]==b'!<arch>\n', 'IPK must be an ar archive'
offset=8;payload=None
while offset<len(data):
    header=data[offset:offset+60];size=int(header[48:58]);name=header[:16].decode().strip().rstrip('/')
    body=data[offset+60:offset+60+size]
    if name.startswith('data.tar'):payload=body
    offset+=60+size+(size%2)
assert payload is not None, 'Missing IPK payload'
with tarfile.open(fileobj=io.BytesIO(payload),mode='r:*') as archive:
    files={member.name:archive.extractfile(member).read() for member in archive.getmembers() if member.isfile()}
prefix='usr/palm/applications/io.github.mehdieskandari.airplaytv.webos/'
def packaged(name):
    matches=[data for path,data in files.items() if path.lstrip('./')==prefix+name]
    assert len(matches)==1, name
    return matches[0]
info=json.loads(packaged('appinfo.json'))
assert info['type']=='web' and info['disableBackHistoryAPI'] is True and info['requiredACG']==[]
assert info['id']==metadata['id'] and info['version']==metadata['version']
for name,size in [('icon.png',80),('largeIcon.png',130)]:
    png=packaged(name);assert png[:8]==b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II',png[16:24])==(size,size), name
html=packaged(info['main']).decode();js=packaged('app.js').decode()
assert 'application/avplayer' not in html and '$WEBAPIS' not in html
assert 'lg-webos' in js and 'webosServiceRequired' in js and 'samsung-tizen' not in js
assert not any(path.endswith(('.map','.node')) or 'simulator-bridge' in path or '/simulator/' in path for path in files)
assert 'HOST HARNESS' not in js
service_prefix='usr/palm/services/'+info['id']+'.receiver/'
def service_file(name):
    matches=[data for path,data in files.items() if path.lstrip('./')==service_prefix+name]
    assert len(matches)==1, name
    return matches[0]
package=json.loads(service_file('package.json'));services=json.loads(service_file('services.json'))
assert package['name']==services['id']==services['services'][0]['name']==info['id']+'.receiver'
assert package['version']==info['version']
assert service_file(package['main'])
assert service_file('core/airplay-core.wasm')[:8]==b'\0asm\x01\0\0\0'
assert service_file('core/airplay-core.worker.js') and service_file('core/airplay-core.js')
assert json.loads(service_file('core/build-info.json'))['emscripten']=='3.1.45'
for license in ['UxPlay','FFmpeg','libplist','OpenSSL','Emscripten']:assert service_file('core/licenses/'+license+'.txt')
assert service_file('licenses/ws-MIT.txt') and packaged('licenses/JMuxer-MIT.txt')
assert packaged('assets/vazirmatn_regular.ttf')
assert packaged('assets/LICENSE.txt') and packaged('assets/Vazirmatn-OFL.txt')
print('Verified LG IPK: app, real WASM service/worker, licenses, icons, fonts and exclusion of host harness/native add-ons')
