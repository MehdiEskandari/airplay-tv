import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import dgram from 'node:dgram';
import {once} from 'node:events';
import {Sockets} from '../../platforms/webos/service/sockets.cjs';
import {CoreController} from '../../platforms/webos/service/controller.cjs';
const tick=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(test){for(let i=0;i<100;i++){if(test())return;await tick(5);}throw new Error('Socket did not become ready');}
function sockets(){const bridge=new Sockets(),heap=new Uint8Array(1024*1024);bridge.heap=()=>heap;return {bridge,heap};}
test('webOS WASM transport uses real TCP and preserves partial reads',async t=>{
  const {bridge,heap}=sockets();t.after(()=>bridge.destroy());
  const server=bridge.create(2,1);assert.equal(bridge.bind(server,'127.0.0.1',0),0);await until(()=>bridge.state(server)===1);
  const client=net.connect(bridge.items.get(server).port,'127.0.0.1');t.after(()=>client.destroy());await once(client,'connect');
  await until(()=>bridge.ready(server,0));const fd=bridge.accept(server,0,0);assert.ok(fd>=64);
  client.write('hello');await until(()=>bridge.ready(fd,0));
  assert.equal(bridge.receive(fd,128,2,0,0,0),2);assert.equal(Buffer.from(heap.slice(128,130)).toString(),'he');
  assert.equal(bridge.receive(fd,128,10,0,0,0),3);assert.equal(Buffer.from(heap.slice(128,131)).toString(),'llo');assert.equal(bridge.receive(fd,128,10,0,0,0),-2);
  heap.set(Buffer.from('world'),128);const reply=once(client,'data');assert.equal(bridge.send(fd,128,5,'',0),5);assert.equal((await reply)[0].toString(),'world');
});
test('webOS WASM transport receives real UDP datagrams and peer addresses',async t=>{
  const {bridge,heap}=sockets();t.after(()=>bridge.destroy());const fd=bridge.create(2,2);
  bridge.bind(fd,'127.0.0.1',0);await until(()=>bridge.state(fd)===1);
  const sender=dgram.createSocket('udp4');t.after(()=>sender.close());sender.send(Buffer.from('ntp'),bridge.items.get(fd).port,'127.0.0.1');await until(()=>bridge.ready(fd,0));
  new DataView(heap.buffer).setUint32(32,16,true);
  assert.equal(bridge.receive(fd,128,64,0,64,32),3);assert.equal(Buffer.from(heap.slice(128,131)).toString(),'ntp');
  assert.deepEqual(Array.from(heap.slice(68,72)),[127,0,0,1]);assert.equal(new DataView(heap.buffer).getUint16(66,false),sender.address().port);
});
test('transport rejects IPv6 and closes bounded queues on overflow',()=>{
  const {bridge}=sockets();assert.equal(bridge.create(10,1),-5);assert.equal(bridge.receive(900,0,1,0,0,0),-1);
  const fd=bridge.create(2,1),item=bridge.items.get(fd);bridge.push(item,Buffer.alloc(4*1024*1024+1));assert.equal(bridge.state(fd),-6);bridge.destroy();
});
test('shutdown wakes a reader and preserves its descriptor until close',()=>{
  const {bridge}=sockets(),fd=bridge.create(2,1);
  assert.equal(bridge.shutdown(fd),0);assert.equal(bridge.ready(fd,0),1);
  assert.equal(bridge.receive(fd,128,4,0,0,0),0);
  const next=bridge.create(2,1);assert.notEqual(next,fd);
  assert.equal(bridge.close(fd),0);assert.equal(bridge.state(next),0);bridge.destroy();
});
test('stop while the core loads prevents a delayed AirPlay listener from starting',async()=>{
  const controller=new CoreController({});controller.clients.add({});let resolve;const loading=new Promise(r=>resolve=r);controller.load=()=>loading;
  const values=['Test','127.0.0.1','020304050607','[]',1,1280,720,30,1];let started=false;
  const attempt=controller.start(values);controller.stop();resolve({ccall:()=>{started=true;return 0;}});
  await assert.rejects(attempt,/cancelled/);assert.equal(started,false);clearTimeout(controller.stopTimer);
});
