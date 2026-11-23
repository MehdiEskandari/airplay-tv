'use strict';
const {WebSocketServer}=require('ws');
const {CoreController,APP}=require('./controller.cjs');
const SERVICE=APP+'.receiver';
{
  const Service=require('webos-service');
  const service=new Service(SERVICE),subscribers=new Map();
  const controller=new CoreController({WebSocketServer,onEvent:event=>{
    for(const message of subscribers.values())message.respond({returnValue:true,event});
  }});
  const allowed=message=>{
    const caller=message.applicationID||message.sender;
    return typeof caller==='string'&&(caller===APP||caller.startsWith(APP+' '));
  };
  function method(name,callback,cancel){
    service.register(name,message=>{
      if(!allowed(message)){message.respond({returnValue:false,errorText:'Caller is not the AirPlay TV app'});return;}
      Promise.resolve().then(()=>callback(message)).then(result=>{
        if(result!==undefined)message.respond({returnValue:true,...result});
      }).catch(error=>message.respond({returnValue:false,errorText:error.message}));
    },cancel);
  }
  method('info',async()=>await controller.open());
  method('events',async message=>{
    if(!message.payload.subscribe)throw new Error('Subscription required');
    subscribers.set(message.uniqueToken,message);return {subscribed:true};
  },message=>{subscribers.delete(message.uniqueToken);if(!subscribers.size)controller.stop();});
  method('start',async message=>({result:await controller.start(message.payload.values)}));
  method('stop',()=>{controller.stop();return {};});
  method('approve',message=>{controller.approve(message.payload.id,message.payload.decision);return {};});
  method('playback',message=>{controller.playback(message.payload.values);return {};});
  module.exports={controller};
}
