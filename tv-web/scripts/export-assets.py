#!/usr/bin/env python3
"""Reuse the Android product's strings, fonts, icon and creator portrait."""
import json
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / 'tv-web'
RES = ROOT / 'app/src/main/res'
generated = WEB / 'generated'
assets = WEB / 'dist/assets'
generated.mkdir(parents=True, exist_ok=True)
assets.mkdir(parents=True, exist_ok=True)
strings = {}
for language, folder in [('fa', 'values'), ('en', 'values-en'), ('mul', 'values-b+mul')]:
    strings[language] = {e.attrib['name']: re.sub(r'[\u064b-\u065f\u0670\u06d6-\u06ed]', '', ''.join(e.itertext())).replace('تأیید','تایید') for e in ET.parse(RES / folder / 'strings.xml').getroot() if e.tag == 'string'}
    strings[language] = {key:value.replace("\\'", "'").replace('\\"','"').replace('\\n','\n') for key,value in strings[language].items()}
(generated / 'strings.json').write_text(json.dumps(strings, ensure_ascii=False), encoding='utf-8')
for name in ['vazirmatn_regular.ttf', 'vazirmatn_medium.ttf', 'vazirmatn_semibold.ttf', 'vazirmatn_bold.ttf', 'inter_variable.ttf']:
    shutil.copy2(RES / 'font' / name, assets / name)
shutil.copy2(RES / 'drawable-nodpi/creator_mehdi.jpg', assets / 'creator.jpg')
shutil.copy2(ROOT / 'docs/art/icon.svg', assets / 'icon.svg')
original_icon = ET.parse(ROOT / 'docs/art/icon.svg')
(generated / 'airplay-path.json').write_text(json.dumps(original_icon.find('.//{http://www.w3.org/2000/svg}path').attrib['d']))
shutil.copy2(ROOT / 'docs/art/icon.png', assets / 'icon.png')
for name in ['Vazirmatn-OFL.txt', 'Inter-OFL.txt']:
    shutil.copy2(ROOT / 'fonts-licenses' / name, assets / name)
shutil.copy2(ROOT / 'LICENSE', assets / 'LICENSE.txt')
print('Synced three languages, original fonts, AirPlay icon and creator portrait')
