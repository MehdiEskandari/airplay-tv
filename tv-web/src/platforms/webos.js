import {WebosMedia} from '../webos-media.js';
const service='luna://io.github.mehdieskandari.airplaytv.webos.receiver/';
export function webosPlatform(scope=window,{createMedia=options=>new WebosMedia(options)}={}){
  const isDevice=typeof scope.PalmSystem!=='undefined',available=isDevice&&typeof scope.PalmServiceBridge==='function';
  let sink=()=>{},media=null,subscription=null,info=null,currentRun=0,loadEpoch=0;
  function rpc(method,parameters={},subscribe=false,onEvent){
    return new Promise((resolve,reject)=>{
      const bridge=new scope.PalmServiceBridge();let first=true;
      const timer=setTimeout(()=>{bridge.cancel();reject(new Error('webosServiceTimeout'));},10000);
      bridge.onservicecallback=raw=>{
        let value;try{value=JSON.parse(raw);}catch{clearTimeout(timer);bridge.cancel();reject(new Error('Invalid receiver service response'));return;}
        if(value.returnValue===false){clearTimeout(timer);bridge.cancel();reject(new Error(value.errorText||'webosServiceUnavailable'));return;}
        if(value.event)onEvent?.(value.event);if(first){first=false;clearTimeout(timer);if(!subscribe)bridge.cancel();resolve({...value,bridge});}
      };
      bridge.call(service+method,JSON.stringify({...parameters,...(subscribe?{subscribe:true}:{})}));
    });
  }
  const emit=event=>{if(event.runId!==undefined&&event.runId!==currentRun)return;if(event.type==='disconnected')media?.start(currentRun);sink(event);};
  if(available)media=createMedia({video:scope.document.getElementById('receiver-video'),scope,onEvent:emit});
  return {
    id:'lg-webos',isDevice,available,receiverImplemented:true,
    unavailableKey:available?null:'webosServiceRequired',previewKey:'webosPreview',
    exit:()=>{if(isDevice)scope.close();},
    async getIp(){if(!available)throw new Error('webosServiceRequired');info=await rpc('info');if(!info.coreBuilt)throw new Error('webosCoreNotBuilt');return info.ip;},
    cancelLoad(){++loadEpoch;if(!currentRun){media?.close();subscription?.cancel();subscription=null;}},
    async loadCore(onEvent){
      const epoch=++loadEpoch;
      if(!available)throw new Error('webosServiceRequired');sink=onEvent;if(!info)info=await rpc('info');
      if(!media)media=createMedia({video:scope.document.getElementById('receiver-video'),scope,onEvent:emit});
      await media.connect(info.mediaUrl);
      if(epoch!==loadEpoch)throw new Error('Receiver start cancelled');
      const events=(await rpc('events',{},true,emit)).bridge;
      if(epoch!==loadEpoch){events.cancel();throw new Error('Receiver start cancelled');}subscription=events;
      return {ccall(name,_return,_types,values=[]){
        let request;
        if(name==='airplay_start'){currentRun=values[8];media.start(currentRun);request=rpc('start',{values});}
        else if(name==='airplay_stop'){++loadEpoch;request=rpc('stop');currentRun=0;media.close();subscription?.cancel();subscription=null;}
        else if(name==='airplay_approve')request=rpc('approve',{id:values[0],decision:values[1]});
        else if(name==='airplay_playback')request=rpc('playback',{values});
        else throw new Error('Unknown receiver command');
        const run=currentRun;request?.then(value=>{if(value.result&&run===currentRun)emit({type:'error',message:'Native start failed: '+value.result,runId:run});}).catch(error=>emit({type:'error',message:error.message,runId:run}));return 0;
      }};
    }
  };
}
export {webosPlatform as createPlatform};
