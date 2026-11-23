import test from 'node:test';
import assert from 'node:assert/strict';
import {webosPlatform} from '../src/platforms/webos.js';
import {ReceiverEngine} from '../src/engine.js';
import {remoteKey} from '../src/remote.js';

test('LG device detection never enables an unavailable AirPlay core',async()=>{
  for(const scope of [{},{PalmSystem:{}}]){
    const platform=webosPlatform(scope);
    assert.equal(platform.available,false);
    assert.equal(platform.isDevice,'PalmSystem' in scope);
    let loaded=false;
    const engine=new ReceiverEngine({...platform,loadCore:async()=>{loaded=true;},settings:{}});
    await engine.start();
    assert.equal(engine.status,'unsupported');assert.equal(loaded,false);
    await assert.rejects(platform.loadCore(),/webosCorePending/);
  }
});
test('webOS Back works even when the remote reports an Unidentified key',()=>{
  for(const key of ['', 'Unidentified', 'BrowserBack'])assert.equal(remoteKey({key,keyCode:461}),'Escape');
  assert.equal(remoteKey({key:'Unidentified',keyCode:38}),'ArrowUp');
  assert.equal(remoteKey({key:'Escape',keyCode:27}),'Escape');
});
test('the custom exit confirmation closes an LG app only on a detected device',()=>{
  let closed=0;
  webosPlatform({PalmSystem:{},close:()=>closed++}).exit();
  webosPlatform({close:()=>closed++}).exit();
  assert.equal(closed,1);
});
