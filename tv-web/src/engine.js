export class ReceiverEngine {
  constructor({available,loadCore,cancelLoad,getIp,settings,deadlineMs=15000}){
    this.platform=available;this.loadCore=loadCore;this.getIp=getIp;this.settings=settings;this.deadlineMs=deadlineMs;
    this.cancelLoad=cancelLoad;
    this.status='stopped';this.listeners=[];this.module=null;this.starting=null;this.timer=null;this.generation=0;
  }
  on(fn){this.listeners.push(fn);return()=>{this.listeners=this.listeners.filter(x=>x!==fn);};}
  emit(event){for(const fn of this.listeners)fn(event);}
  change(status,detail=''){this.status=status;this.emit({type:'state',status,detail});}
  async start(){
    if(this.starting||['listening','connected'].includes(this.status))return this.starting;
    if(!this.platform){this.change('unsupported');return;}
    const generation=++this.generation;this.change('starting');
    this.timer=setTimeout(()=>{if(generation===this.generation&&this.status==='starting'){this.stop();this.change('error','startupTimeout');}},this.deadlineMs);
    this.starting=(async()=>{
      try{
        let ip=this.getIp();if(ip&&typeof ip.then==='function')ip=await ip;
        if(generation!==this.generation)return;
        if(!/^\d+\.\d+\.\d+\.\d+$/.test(ip)||ip==='0.0.0.0')throw new Error('networkUnavailable');
        const module=await this.loadCore(event=>{if(generation===this.generation&&(event.runId===undefined||event.runId===generation))this.nativeEvent(event);});
        if(generation!==this.generation)return;
        this.module=module;
        const v=this.settings.values;
        const result=module.ccall('airplay_start','number',['string','string','string','string','number','number','number','number','number'],[v.serverName,ip,v.deviceId,JSON.stringify(v.trusted),Number(v.approveDevices),Number(v.resolution.split('x')[0]),Number(v.resolution.split('x')[1]),v.maxFps,generation]);
        if(result!==0)throw new Error('nativeStart:'+result);
      }catch(error){if(generation===this.generation){clearTimeout(this.timer);this.cancelLoad?.();this.change('error',error.message);}}
      finally{if(generation===this.generation)this.starting=null;}
    })();return this.starting;
  }
  stop(){++this.generation;clearTimeout(this.timer);this.timer=null;this.starting=null;this.cancelLoad?.();if(this.module){try{this.module.ccall('airplay_stop',null,[],[]);}catch(error){this.emit({type:'log',level:'error',message:error.message});}}this.module=null;this.change('stopped');}
  nativeEvent(event){
    if(event.type==='ready'){clearTimeout(this.timer);this.change('listening',event.ip);}
    else if(event.type==='connected')this.change('connected');
    else if(event.type==='disconnected')this.change('listening');
    else if(event.type==='error'){this.stop();this.change('error',event.message);}
    this.emit(event);
  }
  approve(id,decision){if(!this.module)return;this.module.ccall('airplay_approve',null,['number','number'],[id,decision]);}
  playback({position=0,duration=0,rate=0,ready=false}){this.module?.ccall('airplay_playback',null,['number','number','number','number'],[position,duration,rate,Number(ready)]);}
}
