mergeInto(LibraryManager.library,{
  ap_js_socket__proxy:'sync',ap_js_socket:function(domain,type){return Module.airplaySockets.create(domain,type);},
  ap_js_bind__proxy:'sync',ap_js_bind:function(fd,ip,port){return Module.airplaySockets.bind(fd,UTF8ToString(ip),port);},
  ap_js_state__proxy:'sync',ap_js_state:function(fd){return Module.airplaySockets.state(fd);},
  ap_js_accept__proxy:'sync',ap_js_accept:function(fd,address,len){return Module.airplaySockets.accept(fd,address,len);},
  ap_js_name__proxy:'sync',ap_js_name:function(fd,address,len,remote){return Module.airplaySockets.name(fd,address,len,remote);},
  ap_js_ready__proxy:'sync',ap_js_ready:function(fd,write){return Module.airplaySockets.ready(fd,write);},
  ap_js_recv__proxy:'sync',ap_js_recv:function(fd,buf,len,flags,address,addressLen){return Module.airplaySockets.receive(fd,buf,len,flags,address,addressLen);},
  ap_js_send__proxy:'sync',ap_js_send:function(fd,buf,len,ip,port){return Module.airplaySockets.send(fd,buf,len,ip?UTF8ToString(ip):'',port);},
  ap_js_option__proxy:'sync',ap_js_option:function(fd,kind,a,b){return Module.airplaySockets.option(fd,kind,kind===3?a:UTF8ToString(a),b?UTF8ToString(b):undefined);},
  ap_js_available__proxy:'sync',ap_js_available:function(fd){return Module.airplaySockets.available(fd);},
  ap_js_shutdown__proxy:'sync',ap_js_shutdown:function(fd){return Module.airplaySockets.shutdown(fd);},
  ap_js_close__proxy:'sync',ap_js_close:function(fd){return Module.airplaySockets.close(fd);}
});
