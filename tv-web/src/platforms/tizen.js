export function tizenPlatform(){
  const isTizen=typeof window.tizen!=='undefined'&&typeof window.webapis!=='undefined';
  let loading=null,sink=()=>{};
  return {id:'samsung-tizen',isTizen,isDevice:isTizen,available:isTizen,avplay:window.webapis?.avplay,exit:()=>window.tizen.application.getCurrentApplication().exit(),getIp:()=>window.webapis.network.getIp(),loadCore(onEvent){
    sink=onEvent;
    if(loading){return loading.then(module=>{module.onAirPlayEvent=onEvent;return module;});}
    loading=new Promise((resolve,reject)=>{
      const script=document.createElement('script');script.src='core/airplay-core.js';script.onerror=()=>{loading=null;reject(new Error('coreLoadFailed'));};
      script.onload=()=>{if(typeof window.AirPlayCore!=='function'){loading=null;reject(new Error('coreFactoryMissing'));return;}
        window.AirPlayCore({locateFile:name=>'core/'+name,onAirPlayEvent:event=>sink(event),print:message=>sink({type:'log',level:'info',message}),printErr:message=>sink({type:'log',level:'error',message})}).then(resolve,reject);
      };document.head.appendChild(script);
    }).catch(error=>{loading=null;throw error;});return loading;
  }};
}
export {tizenPlatform as createPlatform};
