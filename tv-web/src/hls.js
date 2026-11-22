// AVPlay handles AirPlay video URLs; elementary mirroring uses the WASM player.
export class HlsPlayer {
  constructor({api,onStart,onStop,onError,onProgress}){Object.assign(this,{api,onStart,onStop,onError,onProgress});this.generation=0;this.active=false;this.paused=false;}
  play(url,position=0){
    this.stop();const generation=++this.generation;
    try{
      const parsed=new URL(url);if(!['http:','https:'].includes(parsed.protocol))throw new Error('Invalid video URL');
      if(!this.api)throw new Error('Samsung AVPlay is unavailable');
      const api=this.api;this.active=true;api.open(parsed.href);
      const current=()=>this.active&&this.generation===generation;
      const progress=(time,rate=1,ready=true)=>{if(current())this.onProgress({position:time/1000,duration:Math.max(0,api.getDuration())/1000,rate,ready});};
      api.setListener({oncurrentplaytime:time=>progress(time),onstreamcompleted:()=>{if(current())this.stop();},onerror:()=>{if(current()){this.stop();this.onError('Video playback failed');}}});
      api.setDisplayRect(0,0,1920,1080);api.setDisplayMethod('PLAYER_DISPLAY_MODE_LETTER_BOX');
      api.prepareAsync(()=>{
        if(!current())return;
        const begin=()=>{if(current()){api.play();this.paused=false;this.onStart();progress(api.getCurrentTime());}};
        try{if(position>0)api.seekTo(Math.round(position*1000),begin,()=>{if(current())this.onError('Video seek failed');});else begin();}
        catch(error){this.stop();this.onError(error.message);}
      },()=>{if(current()){this.stop();this.onError('Video preparation failed');}});
    }catch(error){this.stop();this.onError(error.message);}
  }
  seek(seconds){if(!this.active)return;try{this.api.seekTo(Math.max(0,Math.round(seconds*1000)));}catch(error){this.onError(error.message);}}
  rate(value){if(!this.active)return;try{value===0?this.api.pause():this.api.play();this.paused=value===0;this.onProgress({position:this.api.getCurrentTime()/1000,duration:Math.max(0,this.api.getDuration())/1000,rate:value===0?0:1,ready:true});}catch(error){this.onError(error.message);}}
  stop(){
    ++this.generation;if(!this.active)return;this.active=false;
    try{this.api.stop();}catch{}try{this.api.close();}catch{}
    this.onProgress({position:0,duration:0,rate:0,ready:false});this.onStop();
  }
}
