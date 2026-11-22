#include "media.h"
#include <dns_sd.h>
#include <arpa/inet.h>
#include <net/if.h>
#include <sys/select.h>
#include <sys/stat.h>
#include <unistd.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstring>
#include <iostream>
#include <mutex>
#include <signal.h>
#include <string>
#include <thread>
extern "C" {
#include "raop.h"
#include "android_dnssd_shim.h"
#include <plist/plist.h>
}
namespace {
std::mutex outputMutex;
std::string quote(const char* text){std::string s="\"";if(text)for(unsigned char c:std::string(text)){if(c=='"'||c=='\\'){s+='\\';s+=c;}else if(c<32){char escaped[7];snprintf(escaped,sizeof(escaped),"\\u%04x",c);s+=escaped;}else s+=c;}return s+'"';}
void event(const std::string& type,const std::string& fields=""){std::lock_guard<std::mutex> lock(outputMutex);std::cout<<"{\"type\":"<<quote(type.c_str())<<(fields.empty()?"":",")<<fields<<"}"<<std::endl;}
struct Context{
    std::atomic<bool> stopping{false},active{false};std::atomic<unsigned> spf{480};
    bool requireApproval=true;std::mutex approvalMutex;std::condition_variable approvalChanged;unsigned request=0;int decision=-1;
    LegacyMedia* media=nullptr;
};
Context context;unsigned nextRequest=0;
void noop(void*){}
void request(void*,char* id,char* model,char* name,const char*,bool* admit){
    *admit=false;std::unique_lock<std::mutex> lock(context.approvalMutex);if(context.stopping)return;
    if(!context.requireApproval){*admit=true;return;}if(context.request)return;
    context.request=++nextRequest;context.decision=-1;
    event("approval","\"requestId\":"+std::to_string(context.request)+",\"name\":"+quote(name?name:id)+",\"model\":"+quote(model));
    context.approvalChanged.wait_for(lock,std::chrono::seconds(30),[]{return context.stopping||context.decision>=0;});
    *admit=!context.stopping&&context.decision==1;context.request=0;event("approval-closed");
}
void connected(){if(!context.active.exchange(true))event("connected");}
void video(void*,raop_ntp_t*,video_decode_struct* packet){if(!context.stopping&&!packet->is_h265&&context.media->video(packet->data,packet->data_len,packet->ntp_time_local))connected();}
void audio(void*,raop_ntp_t*,audio_decode_struct* packet){if(!context.stopping&&context.media->audio(packet->data,packet->data_len,packet->ct,context.spf.load(),packet->ntp_time_local))connected();}
void mirror(void*,bool running){if(!running&&context.active.exchange(false))event("disconnected");}
void videoReset(void*,reset_type_t reason){if(reason==RESET_TYPE_HLS_SHUTDOWN||reason==RESET_TYPE_HLS_EOS)event("remote-stop");}
void remotePlay(void*,const char* url,float position){bool admit=false;char name[]="AirPlay Video",model[]="AirPlay";request(nullptr,nullptr,model,name,nullptr,&admit);if(admit)event("remote-url","\"url\":"+quote(url)+",\"position\":"+std::to_string(position));}

struct Bonjour {
    DNSServiceRef parent=nullptr,airplay=nullptr,raop=nullptr;std::thread worker;
    std::atomic<bool> running{false};std::mutex mutex;std::condition_variable changed;int registered=0;bool failed=false;
    static void callback(DNSServiceRef,DNSServiceFlags,DNSServiceErrorType error,const char*,const char*,const char*,void* value){auto& self=*static_cast<Bonjour*>(value);std::lock_guard<std::mutex> lock(self.mutex);if(error)self.failed=true;else self.registered++;self.changed.notify_all();}
    bool add(DNSServiceRef& service,dnssd_t* records,bool isRaop,const char* name,unsigned short port,unsigned interface){
        TXTRecordRef txt;TXTRecordCreate(&txt,0,nullptr);
        const int n=isRaop?android_dnssd_get_raop_txt_count(records):android_dnssd_get_airplay_txt_count(records);
        for(int i=0;i<n;++i){const char* key=isRaop?android_dnssd_get_raop_txt_key(records,i):android_dnssd_get_airplay_txt_key(records,i);const char* value=isRaop?android_dnssd_get_raop_txt_val(records,i):android_dnssd_get_airplay_txt_val(records,i);if(!strcmp(key,"cn"))value="1,2,3";TXTRecordSetValue(&txt,key,strlen(value),value);}
        service=parent;auto result=DNSServiceRegister(&service,kDNSServiceFlagsShareConnection,interface,name,isRaop?"_raop._tcp":"_airplay._tcp","local.",nullptr,htons(port),TXTRecordGetLength(&txt),TXTRecordGetBytesPtr(&txt),callback,this);TXTRecordDeallocate(&txt);return result==kDNSServiceErr_NoError;
    }
    bool start(dnssd_t* records,const char* name,unsigned short port,unsigned interface){
        if(DNSServiceCreateConnection(&parent)!=kDNSServiceErr_NoError)return false;
        if(!add(airplay,records,false,name,port,interface)||!add(raop,records,true,android_dnssd_get_raop_servname(records),port,interface))return false;
        running=true;worker=std::thread([this]{const int fd=DNSServiceRefSockFD(parent);while(running){fd_set fds;FD_ZERO(&fds);FD_SET(fd,&fds);timeval timeout{0,200000};if(select(fd+1,&fds,nullptr,nullptr,&timeout)>0)if(DNSServiceProcessResult(parent)!=kDNSServiceErr_NoError){std::lock_guard<std::mutex> lock(mutex);failed=true;changed.notify_all();break;}}});
        std::unique_lock<std::mutex> lock(mutex);return changed.wait_for(lock,std::chrono::seconds(5),[this]{return failed||registered==2;})&&!failed;
    }
    ~Bonjour(){running=false;if(worker.joinable())worker.join();if(airplay&&airplay!=parent)DNSServiceRefDeallocate(airplay);if(raop&&raop!=parent)DNSServiceRefDeallocate(raop);if(parent)DNSServiceRefDeallocate(parent);}
};
}
int main(int argc,char** argv){
    if(argc!=9){std::cerr<<"usage: host name ip identity keyfile video-fd audio-fd approve interface\n";return 2;}
    signal(SIGPIPE,SIG_IGN);umask(0077);
    const char* name=argv[1];const char* ip=argv[2];const char* identity=argv[3];const char* keyfile=argv[4];
    if(strlen(identity)!=12||strspn(identity,"0123456789abcdefABCDEF")!=12||strlen(name)>50)return 2;
    char hw[6]={};std::string deviceId;
    for(int i=0;i<6;++i){hw[i]=strtoul(std::string(identity+i*2,2).c_str(),nullptr,16);if(i)deviceId+=':';deviceId.append(identity+i*2,2);}
    context.requireApproval=std::string(argv[7])=="1";
    LegacyMedia media(atoi(argv[5]),atoi(argv[6]),[](std::string error){if(!context.stopping)event("error","\"message\":"+quote(error.c_str()));});context.media=&media;
    raop_callbacks_t c{};c.cls=&context;c.video_process=video;c.audio_process=audio;c.video_pause=noop;c.video_resume=noop;c.conn_feedback=noop;c.conn_init=noop;c.conn_destroy=noop;c.conn_reset=[](void*,int){};c.video_reset=videoReset;c.video_flush=noop;c.audio_flush=noop;
    c.audio_set_client_volume=[](void*){return 0.0;};c.audio_set_volume=[](void*,float db){context.media->volume(db);};c.audio_set_metadata=[](void*,const void*,int){};c.audio_set_coverart=[](void*,const void*,int){};c.audio_stop_coverart_rendering=noop;c.audio_remote_control_id=[](void*,const char*,const char*){};c.audio_set_progress=[](void*,uint32_t*,uint32_t*,uint32_t*){};
    c.audio_get_format=[](void*,unsigned char*,unsigned short* count,bool*,bool*,uint64_t*){context.spf=*count;};c.video_report_size=[](void*,float*,float*,float*,float*){};c.mirror_video_running=mirror;c.report_client_request=request;c.display_pin=[](void*,char* pin){event("pin","\"pin\":"+quote(pin));};c.video_set_codec=[](void*,video_codec_t codec){return codec==VIDEO_CODEC_H264?0:-1;};
    c.on_video_play=remotePlay;c.on_video_wait_ready=noop;c.on_video_scrub=[](void*,float pos){event("remote-seek","\"position\":"+std::to_string(pos));};c.on_video_rate=[](void*,float rate){event("remote-rate","\"rate\":"+std::to_string(rate));};c.on_video_stop=[](void*){event("remote-stop");};c.on_video_acquire_playback_info=[](void*,playback_info_t* info){info->ready_to_play=true;info->rate=1;info->playback_likely_to_keep_up=true;};c.on_video_playlist_remove=[](void*){return 0.0f;};
    raop_t* raop=raop_init(&c);if(!raop)return 3;raop_set_log_level(raop,LOGGER_ERR);
    if(raop_init2(raop,0,deviceId.c_str(),keyfile)<0){event("error","\"message\":\"Cannot initialize pairing identity\"");raop_destroy(raop);return 3;}chmod(keyfile,0600);
    raop_set_plist(raop,"width",1280);raop_set_plist(raop,"height",720);raop_set_plist(raop,"refreshRate",30);
    int error=0;dnssd_t* records=dnssd_init(name,strlen(name),hw,6,&error,0);if(!records){raop_destroy(raop);return 3;}raop_set_dnssd(raop,records);android_dnssd_set_codecs(records,1,1);dnssd_set_airplay_features(records,42,0);
    unsigned short port=7001;if(raop_start_httpd(raop,&port)<0){event("error","\"message\":\"AirPlay TCP port is unavailable\"");dnssd_destroy(records);raop_destroy(raop);return 3;}raop_set_port(raop,port);dnssd_register_airplay(records,port);dnssd_register_raop(records,port);
    Bonjour bonjour;if(!bonjour.start(records,name,port,if_nametoindex(argv[8]))){event("error","\"message\":\"Bonjour registration failed\"");raop_destroy(raop);dnssd_destroy(records);return 3;}
    event("ready","\"ip\":"+quote(ip)+",\"port\":"+std::to_string(port));
    std::string line;
    while(std::getline(std::cin,line)){
        if(line=="stop")break;plist_t command=nullptr;
        if(plist_from_json(line.data(),line.size(),&command)==PLIST_ERR_SUCCESS&&command){uint64_t id=0,decision=0;plist_get_uint_val(plist_dict_get_item(command,"id"),&id);plist_get_uint_val(plist_dict_get_item(command,"decision"),&decision);std::lock_guard<std::mutex> lock(context.approvalMutex);if(context.request&&context.request==id){context.decision=decision==1?1:0;context.approvalChanged.notify_all();}plist_free(command);}
    }
    context.stopping=true;context.approvalChanged.notify_all();raop_destroy(raop);dnssd_destroy(records);return 0;
}
