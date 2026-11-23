import JMuxer from 'jmuxer';

export class WebosMedia {
  constructor({video,scope=window,onEvent=()=>{},createMuxer=options=>new JMuxer(options)}){
    this.video=video;this.scope=scope;this.onEvent=onEvent;this.run=0;this.sources=new Set();this.audioQueue=[];this.stats={videoPackets:0,audioPackets:0,audioSamples:0,audioPeak:0};
    this.createMuxer=createMuxer;this.videoQueue=[];this.videoQueueBytes=0;this.mseReady=false;this.mediaEpoch=0;
    this.activate=()=>{try{if(!this.audio)this.audio=new (scope.AudioContext||scope.webkitAudioContext)({sampleRate:44100});this.audio.resume().catch(()=>{});}catch{}};
    scope.addEventListener('pointerdown',this.activate);scope.addEventListener('keydown',this.activate);
    this.video.muted=true;this.video.autoplay=true;
    this.video.addEventListener('playing',()=>{if(!this.run)return;clearTimeout(this.videoStartupTimer);this.videoPlaying=true;this.onEvent({type:'video-start',runId:this.run});this.drainAudio();});
    this.video.addEventListener('error',()=>this.fail('H.264 playback failed'));
  }
  async connect(url){
    if(!/^ws:\/\/127\.0\.0\.1:\d+\/media\/[a-f0-9]{48}$/.test(url))throw new Error('Invalid local media channel');
    this.socket?.close();const socket=new this.scope.WebSocket(url);this.socket=socket;socket.binaryType='arraybuffer';
    socket.onmessage=event=>{if(this.socket===socket)this.packet(event.data);};
    await new Promise((resolve,reject)=>{
      let opened=false;socket.onopen=()=>{opened=true;resolve();};socket.onerror=()=>reject(new Error('Media connection failed'));
      socket.onclose=()=>{if(!opened)reject(new Error('Media connection cancelled'));if(this.socket===socket&&this.run)this.fail('Media service disconnected');};
    });
  }
  start(run){this.reset();this.failed=false;this.run=run;this.stats={videoPackets:0,audioPackets:0,audioSamples:0,audioPeak:0};}
  packet(data){
    if(!(data instanceof ArrayBuffer)||data.byteLength<32||data.byteLength>8*1024*1024+32)return;
    const view=new DataView(data),run=view.getUint32(4,true),pts=view.getFloat64(8,true);if(run!==this.run||!run||!Number.isFinite(pts))return;
    const payload=new Uint8Array(data,32);
    if(view.getUint8(0)===1)this.pushVideo(payload,pts,view.getUint32(24,true));
    else if(view.getUint8(0)===2)this.pushAudio(payload,pts);
  }
  pushVideo(data,pts,fps){
    if(!this.muxer){
      const epoch=this.mediaEpoch,current=()=>this.run&&this.mediaEpoch===epoch;
      this.videoFirstPts=pts;this.lastVideoPts=pts;
      // A display:none video can defer MediaSource startup on TV Chromium.
      // Keep the element laid out while waiting for the real playing event.
      this.video.style.display='block';
      this.videoStartupTimer=setTimeout(()=>this.fail('Video playback startup timed out'),10000);
      try{this.muxer=this.createMuxer({node:this.video,mode:'video',fps:[30,60].includes(fps)?fps:30,flushingTime:50,maxDelay:1000,clearBuffer:true,debug:false,onReady:()=>{
        if(!current()||!this.muxer)return;this.mseReady=true;
        const queued=this.videoQueue;this.videoQueue=[];this.videoQueueBytes=0;
        for(const packet of queued){if(!this.run||!this.muxer)return;try{this.muxer.feed(packet);}catch{this.fail('Media buffer failed');return;}}
        if(current())this.video.play().catch(()=>{if(current())this.fail('Video playback could not start');});
      },onError:()=>{if(current())this.fail('Media buffer failed');},onUnsupportedCodec:()=>{if(current())this.fail('H.264 is unavailable');}});}
      catch{this.fail('Media Source is unavailable');return;}
    }
    const delta=pts-this.lastVideoPts;if(delta>3||delta<-.5){this.fail('Video timestamp discontinuity');return;}
    const packet={video:data,duration:delta>0?Math.min(250,delta*1000):1000/(fps||30),isLastVideoFrameComplete:true};
    // JMuxer creates its SourceBuffer when it first sees SPS/PPS. Feeding before
    // sourceopen loses that one-time signal, leaving the video stuck forever.
    if(this.mseReady){try{this.muxer.feed(packet);}catch{this.fail('Media buffer failed');return;}}
    else{this.videoQueue.push(packet);this.videoQueueBytes+=data.length;if(this.videoQueue.length>120||this.videoQueueBytes>8*1024*1024){this.fail('Media Source startup timed out');return;}}
    this.lastVideoPts=pts;this.stats.videoPackets++;
    if(this.video.buffered.length&&this.video.buffered.end(this.video.buffered.length-1)-this.video.currentTime>4){this.fail('Video player fell behind');}
  }
  pushAudio(data,pts){
    if(data.byteLength%4)return;this.stats.audioPackets++;this.stats.audioSamples+=data.byteLength/4;
    this.audioQueue.push({data,pts});while(this.audioQueue.length>256)this.audioQueue.shift();this.drainAudio();
  }
  drainAudio(){
    if(!this.audio||this.audio.state!=='running')return;
    if(this.videoFirstPts!==undefined&&!this.videoPlaying)return;
    while(this.audioQueue.length){
      const {data,pts}=this.audioQueue.shift();
      if(this.audioFirstPts===undefined){this.audioFirstPts=pts;this.audioBase=this.audio.currentTime+.2;this.onEvent({type:'audio-start',runId:this.run});}
      const when=this.videoPlaying?this.audio.currentTime+pts-this.videoFirstPts-this.video.currentTime+.04:this.audioBase+pts-this.audioFirstPts;
      if(when<this.audio.currentTime-.1)continue;
      if(when>this.audio.currentTime+3){this.fail('Audio timestamp discontinuity');return;}
      const count=data.byteLength/4,view=new DataView(data.buffer,data.byteOffset,data.byteLength),buffer=this.audio.createBuffer(2,count,44100);
      for(let c=0;c<2;c++){const samples=buffer.getChannelData(c);for(let i=0;i<count;i++){const value=view.getInt16((i*2+c)*2,true)/32768;samples[i]=value;this.stats.audioPeak=Math.max(this.stats.audioPeak,Math.abs(value));}}
      const source=this.audio.createBufferSource();source.buffer=buffer;source.connect(this.audio.destination);
      this.sources.add(source);source.onended=()=>{this.sources.delete(source);source.disconnect();};source.start(Math.max(this.audio.currentTime,when));
      if(this.sources.size>300){this.fail('Audio output fell behind');return;}
    }
  }
  fail(message){if(!this.run||this.failed)return;this.failed=true;this.onEvent({type:'error',message,runId:this.run});this.reset();}
  reset(){
    ++this.mediaEpoch;
    clearTimeout(this.videoStartupTimer);
    this.run=0;for(const source of this.sources){try{source.stop();}catch{}}this.sources.clear();this.audioQueue=[];
    if(this.muxer){const muxer=this.muxer;this.muxer=null;try{muxer.destroy();}catch{}}
    this.videoPlaying=false;this.videoFirstPts=this.audioFirstPts=undefined;
    this.mseReady=false;this.videoQueue=[];this.videoQueueBytes=0;
    this.video.style.removeProperty('display');
  }
  close(){this.reset();if(this.socket)this.socket.close();this.socket=null;}
  diagnostics(){return {...this.stats,decodedFrames:this.video.getVideoPlaybackQuality?.().totalVideoFrames||0,audioState:this.audio?.state||'locked'};}
}
