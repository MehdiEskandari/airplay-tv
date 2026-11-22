#include "mdns.h"
#include "player.h"
#include <emscripten.h>
#include <arpa/inet.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <memory>
#include <mutex>
#include <set>
#include <string>
extern "C" {
#include "raop.h"
#include "android_dnssd_shim.h"
#include <plist/plist.h>
}

namespace {
std::string quoted(const char* value) {
    std::string out="\"";
    if(value)for(const unsigned char c:std::string(value)) {
        if(c=='"'||c=='\\'){out+='\\';out+=c;}
        else if(c<32){char b[7];snprintf(b,sizeof(b),"\\u%04x",c);out+=b;}
        else out+=c;
    }
    return out+'"';
}
void event(unsigned run,const std::string& body) {
    const auto json="{\"runId\":"+std::to_string(run)+","+body+"}";
    // The asynchronous browser callback owns this copy. A stack pointer here
    // would become dangling before the main thread has consumed the event.
    char* owned=strdup(json.c_str());
    MAIN_THREAD_ASYNC_EM_ASM({var p=$0;var value=JSON.parse(UTF8ToString(p));_free(p);if(Module.onAirPlayEvent)Module.onAirPlayEvent(value);},owned);
}
bool validKey(const std::string& s) {
    return s.size()==44&&s.back()=='='&&s.substr(0,43).find_first_not_of("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")==std::string::npos;
}
struct Config {std::string name,ip,id,trusted;bool approval;unsigned width,height,fps,run;};
struct Context {
    Config config;raop_t* raop=nullptr;dnssd_t* dnssd=nullptr;
    discovery::Advertiser discovery;
    std::unique_ptr<Player> player;
    std::atomic<bool> stopping{false},videoStarted{false},mediaStarted{false},screen{true};
    std::atomic<unsigned> spf{480};
    std::mutex approvalMutex,playbackMutex;
    std::condition_variable approvalChanged,playbackChanged;
    unsigned requestId=0;int decision=-1;
    std::set<std::string> trusted;
    double position=0,duration=0;float rate=0;bool ready=false;
    explicit Context(Config c):config(std::move(c)){}
    void send(const std::string& type,const std::string& fields="") {
        event(config.run,"\"type\":"+quoted(type.c_str())+(fields.empty()?"":","+fields));
    }
};
std::mutex controlMutex;std::condition_variable commandChanged;
std::shared_ptr<Context> current;
Config pending;bool hasPending=false,stopPending=false;
std::atomic<unsigned> requestSequence{0};
Context& ctx(void* p){return *static_cast<Context*>(p);}
void noOp(void*){}
void onVideo(void* p,raop_ntp_t*,video_decode_struct* data) {
    auto& c=ctx(p);if(c.stopping||data->is_h265)return;
    if(c.player->video(data->data,data->data_len,data->ntp_time_local)){
        if(!c.mediaStarted.exchange(true))c.send("connected");
        if(!c.videoStarted.exchange(true))c.send("video-start");
    }
}
void onAudio(void* p,raop_ntp_t*,audio_decode_struct* data) {
    auto& c=ctx(p);if(c.stopping)return;
    if(c.player->audio(data->data,data->data_len,data->ct,c.spf.load(),data->ntp_time_local,c.screen.load())&&!c.mediaStarted.exchange(true))c.send("connected");
}
void onFormat(void* p,unsigned char*,unsigned short* spf,bool* screen,bool*,uint64_t*) {ctx(p).spf=*spf;ctx(p).screen=*screen;}
void onVolume(void* p,float db){ctx(p).player->volume(db);}
void onMirror(void* p,bool running){if(!running){ctx(p).videoStarted=false;ctx(p).send("video-stop");}}
void onDisconnected(void* p){ctx(p).videoStarted=false;ctx(p).mediaStarted=false;ctx(p).send("disconnected");}
void onReset(void* p,int){onDisconnected(p);}
void onVideoReset(void* p,reset_type_t){ctx(p).send("hls-stop");ctx(p).send("video-stop");}
void onPin(void* p,char* pin){ctx(p).send("pin","\"pin\":"+quoted(pin));}
void onRequest(void* p,char* device,char* model,char* name,const char* verifiedKey,bool* admit) {
    auto& c=ctx(p);*admit=false;
    std::unique_lock<std::mutex> lock(c.approvalMutex);
    if(c.stopping)return;
    const std::string key=verifiedKey?verifiedKey:"";
    if(!c.config.approval||(validKey(key)&&c.trusted.count(key))){*admit=true;return;}
    // Only one local prompt at a time. Concurrent requests are denied.
    if(c.requestId)return;
    c.requestId=++requestSequence;c.decision=-1;
    c.send("approval","\"requestId\":"+std::to_string(c.requestId)+",\"name\":"+quoted(name?name:device)+",\"source\":"+quoted(model)+",\"canRemember\":"+(validKey(key)?"true":"false"));
    c.approvalChanged.wait_for(lock,std::chrono::seconds(30),[&]{return c.stopping||c.decision>=0;});
    *admit=!c.stopping&&c.decision>0;
    if(*admit&&c.decision==2&&validKey(key)){
        c.trusted.insert(key);
        c.send("trusted","\"key\":"+quoted(key.c_str())+",\"id\":"+quoted(device)+",\"name\":"+quoted(name));
    }
    c.requestId=0;c.send("approval-closed");
}
void hlsPlay(void* p,const char* location,float start) {
    auto& c=ctx(p);
    // HLS /play can arrive without an RTSP SETUP. It needs its own consent;
    // URL senders do not provide a verified identity that can be remembered.
    bool admit=false;char name[]="AirPlay Video";char model[]="AirPlay";
    onRequest(p,nullptr,model,name,nullptr,&admit);if(!admit)return;
    c.player->stop();
    {std::lock_guard<std::mutex> lock(c.playbackMutex);c.ready=false;c.position=start;}
    c.send("hls-play","\"url\":"+quoted(location)+",\"position\":"+std::to_string(start));
}
void hlsWait(void* p){auto& c=ctx(p);std::unique_lock<std::mutex> lock(c.playbackMutex);c.playbackChanged.wait_for(lock,std::chrono::seconds(10),[&]{return c.stopping||c.ready;});}
void hlsSeek(void* p,float pos){ctx(p).send("hls-seek","\"position\":"+std::to_string(pos));}
void hlsRate(void* p,float rate){ctx(p).send("hls-rate","\"rate\":"+std::to_string(rate));}
void hlsStop(void* p){ctx(p).send("hls-stop");}
void hlsInfo(void* p,playback_info_t* info){auto& c=ctx(p);std::lock_guard<std::mutex> lock(c.playbackMutex);info->position=c.position;info->duration=c.duration;info->rate=c.rate;info->ready_to_play=c.ready;info->playback_buffer_empty=!c.ready;info->playback_buffer_full=false;info->playback_likely_to_keep_up=c.ready;info->seek_start=0;info->seek_duration=c.duration;}
float hlsRemove(void* p){auto& c=ctx(p);std::lock_guard<std::mutex> lock(c.playbackMutex);return c.position;}
void readTrusted(Context& c) {
    plist_t array=nullptr;
    if(plist_from_json(c.config.trusted.data(),c.config.trusted.size(),&array)!=PLIST_ERR_SUCCESS||!array)return;
    if(plist_get_node_type(array)==PLIST_ARRAY)for(uint32_t i=0;i<plist_array_get_size(array)&&i<128;++i) {
        auto key=plist_dict_get_item(plist_array_get_item(array,i),"key");
        if(key&&plist_get_node_type(key)==PLIST_STRING){auto value=plist_get_string_ptr(key,nullptr);if(value&&validKey(value))c.trusted.insert(value);}
    }
    plist_free(array);
}
void cancel(Context& c){c.stopping=true;c.approvalChanged.notify_all();c.playbackChanged.notify_all();if(c.player)c.player->stop();}
void teardown(Context& c) {
    cancel(c);c.discovery.stop();
    if(c.raop){raop_destroy(c.raop);c.raop=nullptr;}
    c.player.reset();if(c.dnssd){dnssd_destroy(c.dnssd);c.dnssd=nullptr;}
}
bool start(Context& c) {
    readTrusted(c);
    c.player.reset(new Player(c.config.width,c.config.height,c.config.fps,[run=c.config.run](const std::string& message){event(run,"\"type\":\"error\",\"message\":"+quoted(message.c_str()));}));
    raop_callbacks_t callbacks{};callbacks.cls=&c;
    callbacks.audio_process=onAudio;callbacks.video_process=onVideo;callbacks.audio_get_format=onFormat;
    callbacks.video_pause=noOp;callbacks.video_resume=noOp;callbacks.conn_feedback=noOp;callbacks.conn_reset=onReset;callbacks.video_reset=onVideoReset;
    callbacks.conn_init=noOp;callbacks.conn_destroy=onDisconnected;callbacks.audio_flush=noOp;callbacks.video_flush=noOp;
    callbacks.audio_set_client_volume=[](void*){return 0.0;};callbacks.audio_set_volume=onVolume;
    callbacks.audio_set_metadata=[](void*,const void*,int){};callbacks.audio_set_coverart=[](void*,const void*,int){};
    callbacks.audio_stop_coverart_rendering=noOp;callbacks.audio_remote_control_id=[](void*,const char*,const char*){};
    callbacks.audio_set_progress=[](void*,uint32_t*,uint32_t*,uint32_t*){};
    callbacks.video_report_size=[](void* p,float* w,float* h,float*,float*){if(w&&h&&*w>0&&*h>0)ctx(p).player->size(*w,*h);};callbacks.mirror_video_running=onMirror;
    callbacks.report_client_request=onRequest;callbacks.display_pin=onPin;
    callbacks.video_set_codec=[](void*,video_codec_t codec){return codec==VIDEO_CODEC_H264?0:-1;};
    callbacks.on_video_play=hlsPlay;callbacks.on_video_wait_ready=hlsWait;callbacks.on_video_scrub=hlsSeek;
    callbacks.on_video_rate=hlsRate;callbacks.on_video_stop=hlsStop;callbacks.on_video_acquire_playback_info=hlsInfo;callbacks.on_video_playlist_remove=hlsRemove;
    c.raop=raop_init(&callbacks);if(!c.raop)return false;
    raop_set_log_level(c.raop,LOGGER_ERR);
    char hw[6]={};std::string id;
    for(int i=0;i<6;++i){hw[i]=std::stoi(c.config.id.substr(i*2,2),nullptr,16);if(i)id+=':';id+=c.config.id.substr(i*2,2);}
    if(raop_init2(c.raop,0,id.c_str(),"/persist/receiver.key")<0)return false;
    raop_set_plist(c.raop,"width",c.config.width);raop_set_plist(c.raop,"height",c.config.height);raop_set_plist(c.raop,"refreshRate",c.config.fps);
    int error=0;c.dnssd=dnssd_init(c.config.name.c_str(),c.config.name.size(),hw,6,&error,0);if(!c.dnssd)return false;
    raop_set_dnssd(c.raop,c.dnssd);android_dnssd_set_codecs(c.dnssd,1,1);
    unsigned short port=7000;if(raop_start_httpd(c.raop,&port)<0)return false;raop_set_port(c.raop,port);
    if(dnssd_register_airplay(c.dnssd,port)<0||dnssd_register_raop(c.dnssd,port)<0)return false;
    discovery::Service airplay{c.config.name,"_airplay._tcp.local",port,{}},raop{android_dnssd_get_raop_servname(c.dnssd),"_raop._tcp.local",port,{}};
    for(int i=0;i<android_dnssd_get_airplay_txt_count(c.dnssd);++i)airplay.txt.push_back(std::string(android_dnssd_get_airplay_txt_key(c.dnssd,i))+"="+android_dnssd_get_airplay_txt_val(c.dnssd,i));
    for(int i=0;i<android_dnssd_get_raop_txt_count(c.dnssd);++i){
        const std::string key=android_dnssd_get_raop_txt_key(c.dnssd,i);
        raop.txt.push_back(key+"="+(key=="cn"?"1,2,3":android_dnssd_get_raop_txt_val(c.dnssd,i)));
    }
    if(!c.discovery.start("airplay-tv-"+c.config.id+".local",c.config.ip,{airplay,raop}))return false;
    MAIN_THREAD_ASYNC_EM_ASM({FS.syncfs(false,function(error){if(error&&Module.onAirPlayEvent)Module.onAirPlayEvent({type:'error',message:'Cannot save pairing identity'});});});
    c.send("ready","\"ip\":"+quoted(c.config.ip.c_str())+",\"port\":"+std::to_string(port));return true;
}
}

extern "C" {
EMSCRIPTEN_KEEPALIVE int airplay_start(const char* name,const char* ip,const char* id,const char* trusted,int approval,int width,int height,int fps,int run) {
    in_addr address{};
    if(!name||!*name||strlen(name)>63||!ip||inet_pton(AF_INET,ip,&address)!=1||!id||strlen(id)!=12||strspn(id,"0123456789abcdefABCDEF")!=12||!trusted||strlen(trusted)>65536)return -1;
    if(!((width==1280&&height==720)||(width==1920&&height==1080))||(fps!=30&&fps!=60))return -2;
    std::lock_guard<std::mutex> lock(controlMutex);
    pending={name,ip,id,trusted,approval!=0,static_cast<unsigned>(width),static_cast<unsigned>(height),static_cast<unsigned>(fps),static_cast<unsigned>(run)};
    hasPending=true;commandChanged.notify_one();return 0;
}
EMSCRIPTEN_KEEPALIVE void airplay_stop() {
    std::lock_guard<std::mutex> lock(controlMutex);
    if(current){current->stopping=true;current->approvalChanged.notify_all();current->playbackChanged.notify_all();}
    hasPending=false;stopPending=true;commandChanged.notify_one();
}
EMSCRIPTEN_KEEPALIVE void airplay_approve(int request,int decision) {
    std::shared_ptr<Context> c;{std::lock_guard<std::mutex> lock(controlMutex);c=current;}if(!c)return;
    std::lock_guard<std::mutex> lock(c->approvalMutex);
    if(c->requestId!=static_cast<unsigned>(request)||decision<0||decision>2)return;
    c->decision=decision;c->approvalChanged.notify_all();
}
EMSCRIPTEN_KEEPALIVE void airplay_playback(double position,double duration,float rate,int ready) {
    std::shared_ptr<Context> c;{std::lock_guard<std::mutex> lock(controlMutex);c=current;}if(!c)return;
    std::lock_guard<std::mutex> lock(c->playbackMutex);c->position=position;c->duration=duration;c->rate=rate;c->ready=ready!=0;c->playbackChanged.notify_all();
}
}
int main() {
    for(;;) {
        std::shared_ptr<Context> old,next;
        {
            std::unique_lock<std::mutex> lock(controlMutex);commandChanged.wait(lock,[]{return stopPending||hasPending;});
            old=current;current.reset();if(hasPending)next=std::make_shared<Context>(pending);
            hasPending=stopPending=false;
        }
        if(old)teardown(*old);
        if(next) {
            {std::lock_guard<std::mutex> lock(controlMutex);current=next;}
            if(!start(*next)){next->send("error","\"message\":\"Cannot start AirPlay sockets, discovery or player\"");teardown(*next);}
        }
    }
}
