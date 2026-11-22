#!/usr/bin/env python3
import importlib.util,json,threading,unittest,urllib.error,urllib.request
from pathlib import Path
import http.server

path=Path(__file__).resolve().parents[1]/'scripts/companion.py';spec=importlib.util.spec_from_file_location('legacy_companion',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class Fake:
 tv='192.0.2.20';host='127.0.0.1';token='test-only-token';name='Test';lock=threading.RLock();events=[{'id':1,'event':{'type':'ready'}}];widget_poll=0
 def __init__(self):self.calls=[]
 def status(self):return {'state':'stopped'}
 def start(self,data):self.calls.append(('start',data))
 def stop(self):self.calls.append(('stop',))
 def approve(self,data):self.calls.append(('approve',data))
 def test(self):self.calls.append(('test',))
 def record_telemetry(self,data,source):self.calls.append(('telemetry',data,source))
class Contract(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.fake=Fake();cls.server=http.server.ThreadingHTTPServer(('127.0.0.1',0),module.handler_for(cls.fake));cls.thread=threading.Thread(target=cls.server.serve_forever);cls.thread.start();cls.base='http://127.0.0.1:'+str(cls.server.server_port)
 @classmethod
 def tearDownClass(cls):cls.server.shutdown();cls.server.server_close();cls.thread.join()
 def request(self,path,body=None,token=True):
  url=self.base+path
  if token:url+=('&' if '?' in path else '?')+'token='+self.fake.token
  return urllib.request.urlopen(urllib.request.Request(url,data=body,headers={'Content-Type':'application/json'}),timeout=3)
 def test_unapproved_calls_cannot_control_receiver(self):
  before=len(self.fake.calls)
  for path,body in [('/api/start',b'{}'),('/api/status',None)]:
   with self.assertRaises(urllib.error.HTTPError) as error:self.request(path,body,False)
   self.assertEqual(error.exception.code,403)
  self.assertEqual(len(self.fake.calls),before)
 def test_start_and_cursor_require_valid_contract(self):
  with self.request('/api/start',b'{"approve":true}') as r:self.assertEqual(json.load(r),{'ok':True})
  self.assertEqual(self.fake.calls[-1],('start',{'approve':True}))
  with self.request('/api/events?after=1') as r:self.assertEqual(json.load(r)['events'],[])
 def test_malformed_payload_does_not_mutate_receiver(self):
  before=len(self.fake.calls)
  for body in [b'[]',b'{broken']:
   with self.assertRaises(urllib.error.HTTPError) as error:self.request('/api/start',body)
   self.assertEqual(error.exception.code,400)
  self.assertEqual(len(self.fake.calls),before)
 def test_browser_preview_never_discloses_widget_credential(self):
  with self.request('/preview/bridge-config.js',token=False) as r:
   content=r.read();self.assertNotIn(self.fake.token.encode(),content);self.assertIn(b'Config={}',content)
   self.assertIsNone(r.headers.get('Access-Control-Allow-Origin'))

if __name__=='__main__':unittest.main()
