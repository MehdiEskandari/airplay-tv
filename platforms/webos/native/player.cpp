#include "player.h"
#include <emscripten.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>
#include <mutex>
#include <vector>
extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/mem.h>
}
struct Player::Impl {
    unsigned width,height,fps,run,codec=0,spf=0;
    std::atomic<bool> stopping{false};std::atomic<float> gain{1};
    std::mutex audioMutex,clockMutex;uint64_t base=0;
    std::function<void(std::string)> error;
    AVCodecContext* decoder=nullptr;AVPacket* packet=nullptr;AVFrame* frame=nullptr;
    Impl(unsigned w,unsigned h,unsigned f,unsigned r,std::function<void(std::string)> e):width(w),height(h),fps(f),run(r),error(std::move(e)){}
    ~Impl(){clear();}
    void clear(){avcodec_free_context(&decoder);av_packet_free(&packet);av_frame_free(&frame);codec=spf=0;}
    double time(uint64_t ns){std::lock_guard<std::mutex> lock(clockMutex);if(!base)base=ns;return static_cast<int64_t>(ns-base)/1000000000.0;}
    bool audioConfig(unsigned ct,unsigned samples){
        if(decoder&&codec==ct&&spf==samples)return true;clear();if(ct!=2&&ct!=4&&ct!=8)return false;
        const AVCodec* c=avcodec_find_decoder(ct==2?AV_CODEC_ID_ALAC:AV_CODEC_ID_AAC);if(!c)return false;
        decoder=avcodec_alloc_context3(c);if(!decoder)return false;
        uint8_t csd[36]={};unsigned size=0;
        if(ct==2){const uint8_t header[]={0,0,0,36,'a','l','a','c',0,0,0,0};memcpy(csd,header,12);csd[12]=samples>>24;csd[13]=samples>>16;csd[14]=samples>>8;csd[15]=samples;csd[17]=16;csd[18]=40;csd[19]=10;csd[20]=14;csd[21]=2;csd[23]=255;csd[32]=44100>>24;csd[33]=44100>>16;csd[34]=(44100>>8)&255;csd[35]=44100&255;size=36;}
        else if(ct==8){csd[0]=0xf8;csd[1]=0xe8;csd[2]=samples==512?0x40:0x50;size=4;}
        else{csd[0]=0x12;csd[1]=samples==960?0x14:0x10;size=2;}
        decoder->extradata=static_cast<uint8_t*>(av_mallocz(size+AV_INPUT_BUFFER_PADDING_SIZE));if(!decoder->extradata)return false;
        memcpy(decoder->extradata,csd,size);decoder->extradata_size=size;decoder->sample_rate=44100;decoder->thread_count=1;av_channel_layout_default(&decoder->ch_layout,2);
        if(avcodec_open2(decoder,c,nullptr)<0)return false;packet=av_packet_alloc();frame=av_frame_alloc();
        if(!packet||!frame){clear();return false;}codec=ct;spf=samples;return true;
    }
    void emit(unsigned type,const uint8_t* data,size_t size,double pts){
        uint8_t* copy=static_cast<uint8_t*>(malloc(size));if(!copy){error("Media buffer allocation failed");return;}memcpy(copy,data,size);
        MAIN_THREAD_ASYNC_EM_ASM({var p=$0;try{if(Module.onAirPlayMedia)Module.onAirPlayMedia({kind:$2===1?'video':'audio',runId:$3,pts:$4,width:$5,height:$6,fps:$7,data:HEAPU8.slice(p,p+$1)});}finally{_free(p);}},copy,size,type,run,pts,width,height,fps);
    }
};
Player::Player(unsigned w,unsigned h,unsigned fps,unsigned run,std::function<void(std::string)> error):impl_(new Impl(w,h,fps,run,std::move(error))){}
Player::~Player()=default;
void Player::stop(){impl_->stopping=true;}
void Player::volume(float db){impl_->gain=db<=-144?0:std::min(1.0f,std::pow(10.0f,db/20.0f));}
void Player::size(unsigned w,unsigned h){if(w&&h&&w<=8192&&h<=8192){impl_->width=w;impl_->height=h;}}
bool Player::video(const unsigned char* data,int size,uint64_t ns){auto& p=*impl_;if(p.stopping||size<=0||size>8*1024*1024)return false;p.emit(1,data,size,p.time(ns));return true;}
bool Player::audio(const unsigned char* data,int size,unsigned codec,unsigned spf,uint64_t ns,bool){
    auto& p=*impl_;std::lock_guard<std::mutex> lock(p.audioMutex);
    if(p.stopping||size<=0||size>256*1024||!p.audioConfig(codec,spf))return false;
    av_packet_unref(p.packet);if(av_new_packet(p.packet,size)<0)return false;memcpy(p.packet->data,data,size);
    if(avcodec_send_packet(p.decoder,p.packet)<0)return false;double pts=p.time(ns);
    while(avcodec_receive_frame(p.decoder,p.frame)==0){
        if(p.frame->ch_layout.nb_channels!=2||p.frame->sample_rate!=44100)return false;
        const int count=p.frame->nb_samples;std::vector<int16_t> pcm(count*2);
        for(int c=0;c<2;++c)for(int i=0;i<count;++i){
            float sample;if(p.frame->format==AV_SAMPLE_FMT_S16P)sample=reinterpret_cast<int16_t*>(p.frame->data[c])[i];
            else if(p.frame->format==AV_SAMPLE_FMT_FLTP)sample=reinterpret_cast<float*>(p.frame->data[c])[i]*32768.0f;else return false;
            pcm[i*2+c]=static_cast<int16_t>(std::max(-32768.0f,std::min(32767.0f,sample*p.gain.load())));
        }
        p.emit(2,reinterpret_cast<const uint8_t*>(pcm.data()),pcm.size()*2,pts);pts+=count/44100.0;av_frame_unref(p.frame);
    }
    return true;
}
