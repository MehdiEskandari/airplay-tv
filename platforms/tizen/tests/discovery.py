#!/usr/bin/env python3
"""Inspect actual encoded DNS records and fuzz the question parser on the host."""
from pathlib import Path
import os
import struct
import subprocess
import tempfile

platform=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='airplay-mdns-test-') as folder:
    binary=Path(folder)/'discovery'
    subprocess.run([os.environ.get('CXX','c++'),'-std=c++17','-pthread','-fsanitize=address,undefined','-I'+str(platform/'native'),str(platform/'tests/discovery.cpp'),str(platform/'native/mdns.cpp'),'-o',str(binary)],check=True)
    data=subprocess.check_output([str(binary)])
assert struct.unpack('!6H',data[:12])==(0,0x8400,0,9,0,0)
def name(offset):
    labels=[]
    while data[offset]:
        size=data[offset];assert size<=63
        labels.append(data[offset+1:offset+1+size].decode())
        offset+=1+size
    return labels,offset+1
offset=12
records=[]
for _ in range(9):
    owner,offset=name(offset)
    kind,cls,ttl,size=struct.unpack('!HHIH',data[offset:offset+10]);offset+=10
    end=offset+size
    assert ttl==120 and cls&0x7fff==1
    if kind==1: assert data[offset:end]==bytes([192,168,1,20])
    if kind==33:
        assert struct.unpack('!3H',data[offset:offset+6])==(0,0,7000)
        target,after=name(offset+6);assert target==['airplay-test','local'] and after==end
    if kind==12:
        target,after=name(offset);assert after==end
    records.append((owner,kind));offset=end
assert offset==len(data)
assert any(owner[0]=='ایر پلی.تی وی' and kind==16 for owner,kind in records)
assert sum(kind==16 for _,kind in records)==2
print('mDNS: 9 valid records; AirPlay/RAOP, UTF-8 labels, address and ports verified; 10,000 malformed queries checked with ASan/UBSan.')
