#!/usr/bin/env python3
"""Package a local ES5/Orsay widget with the same product assets and Teli colors."""
from pathlib import Path
import argparse,json,re,shutil,subprocess,zipfile,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[3]
PLATFORM=ROOT/'platforms/samsung-legacy'
parser=argparse.ArgumentParser()
parser.add_argument('--host',default='127.0.0.1')
parser.add_argument('--port',type=int,default=4188)
parser.add_argument('--token')
args=parser.parse_args()
if not args.token:
 token_file=PLATFORM/'.cache/runtime/widget-token'
 if token_file.exists():args.token=token_file.read_text().strip()
 elif args.host=='127.0.0.1':args.token='preview-only'
 else:parser.error('Start the companion first, or supply its token; a disconnected widget is not packaged for the TV.')
subprocess.run(['python3',str(ROOT/'tv-web/scripts/export-assets.py')],check=True)
build=PLATFORM/'build/widget';build.mkdir(parents=True,exist_ok=True)
assets=build/'assets';shutil.copytree(ROOT/'tv-web/dist/assets',assets,dirs_exist_ok=True)
for name in ['index.html','app.js']:shutil.copyfile(PLATFORM/'web'/name,build/name)
shutil.copyfile(PLATFORM/'config.xml',build/'config.xml')
(build/'widget.info').write_text('Application Position = 0,0\nApplication Size = 960x540\nScreen Number = 1\nScreen Resolution = 960x540\nUse Alpha Blending? = Yes\n')
strings=json.loads((ROOT/'tv-web/generated/strings.json').read_text())
(build/'strings.js').write_text('window.AirPlayStrings='+json.dumps(strings,ensure_ascii=False).replace('\u2028','\\u2028').replace('\u2029','\\u2029')+';\n')
(build/'bridge-config.js').write_text('window.AirPlayBridgeConfig='+json.dumps({'base':f'http://{args.host}:{args.port}','token':args.token,'name':'AirPlay TV Legacy'})+';\n')
css=(ROOT/'tv-web/src/style.css').read_text()
roots=re.findall(r':root(?:\[[^]]+\])?\{([^}]+)\}',css)
def variables(body):return dict(re.findall(r'--([a-z-]+):([^;}]+)',body))
base=variables(roots[0]);base['scale']='1'
fontfaces='\n'.join(re.findall(r'@font-face\{[^}]+\}',css)).replace('font-weight:100 900','font-weight:400')
css=re.sub(r'@font-face\{[^}]+\}|:root(?:\[[^]]+\])?\{[^}]+\}|@media[^{}]*\{.*?\}\}', '',css)
blocks=re.findall(r'([^{}]+)\{([^{}]*)\}',css)
result=[fontfaces]
for theme,override in [('teli_dark',{}),('teli_light',variables(roots[1])),('solid_dark',variables(roots[2]))]:
 values=dict(base,**override);prefix='html[data-theme='+theme+']'
 for selectors,body in blocks:
  if '--' in body and not 'var(' in body:continue
  body=re.sub(r'var\(--([a-z-]+)\)',lambda m:values[m[1]],body)
  selectors=selectors.strip()
  if selectors.startswith('/*'):selectors=selectors[selectors.rfind('*/')+2:].strip()
  if not selectors:continue
  targets=[]
  for selector in selectors.split(','):
   selector=selector.strip();targets.append(prefix+selector[4:] if selector=='html' or selector.startswith('html[') else prefix+' '+selector)
  result.append(','.join(targets)+'{'+body+'}')
result.append((PLATFORM/'web/legacy.css').read_text())
result.append('html[lang=en] body,html[lang=en] button{font-family:Arial,Vazirmatn,sans-serif}')
(build/'style.css').write_text('\n'.join(result))
icon_source=(ROOT/'tv-web/src/icons.js').read_text()
for name in ['mirror','wifi','airplay']:
 if name=='airplay':path=ET.parse(ROOT/'docs/art/icon.svg').find('.//{http://www.w3.org/2000/svg}path').attrib['d']
 else:path=re.search(r"  "+name+r":'([^']+)'",icon_source)[1]
 (assets/(name+'.svg')).write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="#8196a9" d="'+path+'"/></svg>')
output=PLATFORM/'output';output.mkdir(exist_ok=True)
archive=output/'AirPlayTVLegacy_0.1.0.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
 for file in sorted(build.rglob('*')):
  if file.is_file():z.write(file,file.relative_to(build))
manifest=ET.Element('rsp',{'stat':'ok'});listing=ET.SubElement(manifest,'list');widget=ET.SubElement(listing,'widget',{'id':'AirPlayTVLegacy'})
ET.SubElement(widget,'title').text='AirPlay TV Legacy'
ET.SubElement(widget,'compression',{'size':str(archive.stat().st_size),'type':'zip'})
ET.SubElement(widget,'description').text='AirPlay TV Legacy - Teli dark and light'
ET.SubElement(widget,'download').text=f'http://{args.host}/widget/{archive.name}'
ET.ElementTree(manifest).write(output/'widgetlist.xml',encoding='UTF-8',xml_declaration=True)
print('Built',archive,'('+str(archive.stat().st_size)+' bytes). Companion receiver required; hardware installation pending.')
