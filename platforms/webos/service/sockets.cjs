'use strict';
// POSIX socket transport for the UxPlay WebAssembly workers. All Node I/O stays
// asynchronous on the service thread; C workers wait using their own futexes.
const net=require('net'),dgram=require('dgram');
const errors={EBADF:-1,EAGAIN:-2,EADDRINUSE:-3,EINVAL:-4,EAFNOSUPPORT:-5,ECONNRESET:-6,EACCES:-7};
const MAX_BYTES=4*1024*1024,MAX_PACKETS=512;
class Sockets {
  constructor(){this.items=new Map();this.heap=()=>{throw new Error('Heap is not attached');};}
  error(error){return errors[error&&error.code]||-8;}
  create(domain,type){
    if(domain!==2||![1,2].includes(type))return -5;
    let fd=64;while(this.items.has(fd)&&fd<1024)fd++;if(fd===1024)return -8;
    this.items.set(fd,{fd,type,state:0,bytes:0,queue:[],accepted:[],closed:false,port:0,ip:'0.0.0.0',timeout:0});return fd;
  }
  fail(item,error){item.error=this.error(error);item.state=item.error;}
  push(item,data,peer){
    if(item.closed)return;
    if(item.bytes+data.length>MAX_BYTES||item.queue.length>=MAX_PACKETS){this.fail(item,{code:'ECONNRESET'});if(item.socket)item.socket.destroy?item.socket.destroy():item.socket.close();return;}
    item.queue.push({data:Buffer.from(data),peer});item.bytes+=data.length;
  }
  bind(fd,ip,port){
    const item=this.items.get(fd);if(!item||item.started)return -1;
    if(!net.isIPv4(ip)||port<0||port>65535)return -4;
    item.started=true;item.ip=ip;item.port=port;
    const ready=()=>{if(item.closed)return;const address=item.socket.address();item.port=address.port;item.state=1;};
    try{
      if(item.type===2){
        item.socket=dgram.createSocket({type:'udp4',reuseAddr:true});
        item.socket.on('message',(data,peer)=>this.push(item,data,{ip:peer.address,port:peer.port}));
        item.socket.on('error',error=>this.fail(item,error));item.socket.bind(port,ip,ready);
      }else{
        item.socket=net.createServer(socket=>{
          if(item.closed||item.accepted.length>=16){socket.destroy();return;}
          const child=this.create(2,1);if(child<0){socket.destroy();return;}
          const peer=this.items.get(child);peer.started=true;peer.state=1;peer.socket=socket;peer.connected=true;
          peer.ip=socket.localAddress;peer.port=socket.localPort;peer.remote={ip:socket.remoteAddress,port:socket.remotePort};
          socket.setNoDelay(true);socket.on('data',data=>this.push(peer,data));
          socket.on('end',()=>{peer.ended=true;});socket.on('error',error=>this.fail(peer,error));
          socket.on('close',()=>{peer.ended=true;});item.accepted.push(child);
        });
        item.socket.on('error',error=>this.fail(item,error));item.socket.listen({host:ip,port,backlog:16},ready);
      }
      return 0;
    }catch(error){this.fail(item,error);return item.error;}
  }
  state(fd){const item=this.items.get(fd);return item?item.state:-1;}
  accept(fd,address,length){
    const item=this.items.get(fd);if(!item)return -1;if(item.error)return item.error;
    if(!item.accepted.length)return -2;const child=item.accepted.shift();
    this.address(this.items.get(child).remote,address,length);return child;
  }
  address(peer,pointer,lengthPointer){
    if(!pointer)return 0;const heap=this.heap();
    const view=new DataView(heap.buffer);if(lengthPointer&&view.getUint32(lengthPointer,true)<16)return -4;
    heap.fill(0,pointer,pointer+16);view.setUint16(pointer,2,true);view.setUint16(pointer+2,peer.port,false);
    const ip=peer.ip.replace(/^::ffff:/,'');if(!net.isIPv4(ip))return -4;
    heap.set(ip.split('.').map(Number),pointer+4);if(lengthPointer)view.setUint32(lengthPointer,16,true);return 0;
  }
  name(fd,address,length,remote){const item=this.items.get(fd);if(!item)return -1;if(remote&&!item.remote)return -4;return this.address(remote?item.remote:{ip:item.ip,port:item.port},address,length);}
  ready(fd,write){
    const item=this.items.get(fd);if(!item)return -1;
    if(item.error||item.ended)return 1;
    if(write)return item.state===1&&(!item.connected||item.socket.writableLength<MAX_BYTES)?1:0;
    return item.accepted.length||item.queue.length?1:0;
  }
  receive(fd,pointer,length,flags,address,lengthPointer){
    const item=this.items.get(fd);if(!item)return -1;if(item.error)return item.error;
    if(!item.queue.length)return item.ended?0:-2;
    const packet=item.queue[0],size=Math.min(length,packet.data.length);this.heap().set(packet.data.subarray(0,size),pointer);
    if(address)this.address(packet.peer,address,lengthPointer);
    if(!(flags&2)){
      if(item.type===2||size===packet.data.length){item.queue.shift();item.bytes-=packet.data.length;}
      else{packet.data=packet.data.subarray(size);item.bytes-=size;}
    }
    return size;
  }
  send(fd,pointer,length,ip,port){
    const item=this.items.get(fd);if(!item)return -1;if(item.error)return item.error;
    if(item.state!==1)return -2;if(length<0||length>MAX_BYTES)return -4;
    const bytes=Buffer.from(this.heap().subarray(pointer,pointer+length));
    try{
      if(item.type===2){if(!net.isIPv4(ip))return -4;item.socket.send(bytes,port,ip,error=>{if(error)this.fail(item,error);});}
      else{if(!item.connected||item.ended)return -6;if(item.socket.writableLength+length>MAX_BYTES)return -2;item.socket.write(bytes);}
      return length;
    }catch(error){this.fail(item,error);return item.error;}
  }
  option(fd,kind,a,b){
    const item=this.items.get(fd);if(!item)return -1;
    try{
      if(kind===1)item.socket.addMembership(a,b);
      else if(kind===2)item.socket.setMulticastInterface(a);
      else if(kind===3)item.socket.setMulticastTTL(a);
      return 0;
    }catch(error){return this.error(error);}
  }
  available(fd){const item=this.items.get(fd);return item?(item.type===2?(item.queue[0]?.data.length||0):item.bytes):-1;}
  shutdown(fd){
    const item=this.items.get(fd);if(!item)return -1;
    // shutdown wakes readers but does not free the descriptor. A later close
    // must not accidentally close a newly reused descriptor from another worker.
    item.ended=true;
    if(item.socket){try{if(item.connected)item.socket.destroy();else item.socket.close();}catch{}}
    return 0;
  }
  close(fd){
    const item=this.items.get(fd);if(!item)return -1;item.closed=true;
    for(const child of item.accepted)this.close(child);
    if(item.socket){try{if(item.type===1&&item.connected)item.socket.destroy();else item.socket.close();}catch{}}
    this.items.delete(fd);return 0;
  }
  destroy(){for(const fd of Array.from(this.items.keys()))if(this.items.has(fd))this.close(fd);}
}
module.exports={Sockets};
