#include "media.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstring>
#include <deque>
#include <mutex>
#include <thread>
#include <unistd.h>
#include <vector>
extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/mem.h>
#include <libswscale/swscale.h>
#include "raop.h"
}

namespace {
constexpr int W=1280,H=720,RATE=44100;
constexpr uint64_t CUSHION_NS=200000000;
bool writeAll(int fd,const unsigned char* data,size_t n){while(n){const auto count=write(fd,data,n);if(count<0&&errno==EINTR)continue;if(count<=0)return false;data+=count;n-=count;}return true;}
struct Picture{uint64_t time;std::vector<unsigned char> pixels;};
struct Samples{uint64_t time;std::vector<int16_t> pcm;};
}
struct LegacyMedia::Impl {
    int videoFd,audioFd;std::atomic<bool> running{true},failed{false};std::atomic<float> gain{1};
    std::function<void(std::string)> error;std::mutex videoLock,audioLock,pictureLock,sampleLock;
    std::deque<Picture> pictures;std::deque<Samples> samples;
    AVCodecContext *videoDecoder=nullptr,*audioDecoder=nullptr;AVCodecParserContext* parser=nullptr;
    AVPacket *vpacket=nullptr,*apacket=nullptr;AVFrame *vframe=nullptr,*aframe=nullptr;SwsContext* scaler=nullptr;
    unsigned codec=0,spf=0;std::thread videoThread,audioThread;
    Impl(int v,int a,std::function<void(std::string)> report):videoFd(v),audioFd(a),error(std::move(report)) {
        av_log_set_level(AV_LOG_QUIET);
        const auto* h264=avcodec_find_decoder(AV_CODEC_ID_H264);videoDecoder=avcodec_alloc_context3(h264);parser=av_parser_init(AV_CODEC_ID_H264);
        vpacket=av_packet_alloc();vframe=av_frame_alloc();
        if(!h264||!videoDecoder||!parser||!vpacket||!vframe||avcodec_open2(videoDecoder,h264,nullptr)<0){fail("H.264 decoder initialization failed");return;}
        videoThread=std::thread([this]{videoClock();});audioThread=std::thread([this]{audioClock();});
    }
    void fail(const std::string& message){if(!failed.exchange(true))error(message);}
    ~Impl(){running=false;close(videoFd);close(audioFd);if(videoThread.joinable())videoThread.join();if(audioThread.joinable())audioThread.join();av_parser_close(parser);avcodec_free_context(&videoDecoder);avcodec_free_context(&audioDecoder);av_packet_free(&vpacket);av_packet_free(&apacket);av_frame_free(&vframe);av_frame_free(&aframe);sws_freeContext(scaler);}
    void videoClock(){
        std::vector<unsigned char> last(W*H*3/2,128);std::fill(last.begin(),last.begin()+W*H,16);
        auto next=std::chrono::steady_clock::now();
        while(running){
            const uint64_t target=get_local_time()-CUSHION_NS;
            {std::lock_guard<std::mutex> lock(pictureLock);while(!pictures.empty()&&pictures.front().time<=target){last=std::move(pictures.front().pixels);pictures.pop_front();}}
            if(!writeAll(videoFd,last.data(),last.size())){if(running)fail("Video encoder pipe closed");break;}
            next+=std::chrono::nanoseconds(1000000000/30);std::this_thread::sleep_until(next);
            if(std::chrono::steady_clock::now()-next>std::chrono::seconds(1))next=std::chrono::steady_clock::now();
        }
    }
    void audioClock(){
        auto next=std::chrono::steady_clock::now();std::vector<int16_t> output(441*2);
        while(running){
            std::fill(output.begin(),output.end(),0);const uint64_t target=get_local_time()-CUSHION_NS;
            {std::lock_guard<std::mutex> lock(sampleLock);
                for(unsigned i=0;i<441;++i){const uint64_t at=target+i*1000000000ull/RATE;
                    while(!samples.empty()&&samples.front().time+(samples.front().pcm.size()/2)*1000000000ull/RATE<=at)samples.pop_front();
                    if(!samples.empty()&&samples.front().time<=at){const auto offset=(at-samples.front().time)*RATE/1000000000ull;if(offset*2+1<samples.front().pcm.size()){output[i*2]=samples.front().pcm[offset*2];output[i*2+1]=samples.front().pcm[offset*2+1];}}
                }
            }
            if(!writeAll(audioFd,reinterpret_cast<unsigned char*>(output.data()),output.size()*2)){if(running)fail("Audio encoder pipe closed");break;}
            next+=std::chrono::milliseconds(10);std::this_thread::sleep_until(next);
            if(std::chrono::steady_clock::now()-next>std::chrono::seconds(1))next=std::chrono::steady_clock::now();
        }
    }
    void clearAudio(){avcodec_free_context(&audioDecoder);av_packet_free(&apacket);av_frame_free(&aframe);codec=spf=0;}
    bool configureAudio(unsigned ct,unsigned count){
        if(audioDecoder&&ct==codec&&count==spf)return true;clearAudio();if(ct!=2&&ct!=4&&ct!=8)return false;
        const auto* c=avcodec_find_decoder(ct==2?AV_CODEC_ID_ALAC:AV_CODEC_ID_AAC);if(!c)return false;
        audioDecoder=avcodec_alloc_context3(c);if(!audioDecoder)return false;
        unsigned char config[36]={};unsigned size=0;
        if(ct==2){const unsigned char header[]={0,0,0,36,'a','l','a','c',0,0,0,0};memcpy(config,header,12);config[12]=count>>24;config[13]=count>>16;config[14]=count>>8;config[15]=count;config[17]=16;config[18]=40;config[19]=10;config[20]=14;config[21]=2;config[23]=255;config[34]=44100>>8;config[35]=44100&255;size=36;}
        else if(ct==8){config[0]=0xf8;config[1]=0xe8;config[2]=count==512?0x40:0x50;size=4;}
        else{config[0]=0x12;config[1]=count==960?0x14:0x10;size=2;}
        audioDecoder->extradata=static_cast<unsigned char*>(av_mallocz(size+AV_INPUT_BUFFER_PADDING_SIZE));if(!audioDecoder->extradata)return false;
        memcpy(audioDecoder->extradata,config,size);audioDecoder->extradata_size=size;audioDecoder->sample_rate=RATE;av_channel_layout_default(&audioDecoder->ch_layout,2);audioDecoder->thread_count=1;
        if(avcodec_open2(audioDecoder,c,nullptr)<0)return false;apacket=av_packet_alloc();aframe=av_frame_alloc();if(!apacket||!aframe){clearAudio();return false;}codec=ct;spf=count;return true;
    }
};
LegacyMedia::LegacyMedia(int v,int a,std::function<void(std::string)> e):impl(new Impl(v,a,std::move(e))){}
LegacyMedia::~LegacyMedia()=default;
void LegacyMedia::volume(float db){impl->gain=db<=-144?0:std::min(1.0f,std::pow(10.0f,db/20.0f));}
bool LegacyMedia::video(const unsigned char* data,int length,uint64_t ntp){
    auto& p=*impl;std::lock_guard<std::mutex> lock(p.videoLock);if(p.failed||length<=0||length>8*1024*1024)return false;
    std::vector<unsigned char> padded(length+AV_INPUT_BUFFER_PADDING_SIZE,0);memcpy(padded.data(),data,length);data=padded.data();
    bool decoded=false;const uint64_t timestamp=ntp?ntp:get_local_time();
    while(length>0){unsigned char* packetData=nullptr;int packetSize=0;const int consumed=av_parser_parse2(p.parser,p.videoDecoder,&packetData,&packetSize,data,length,AV_NOPTS_VALUE,AV_NOPTS_VALUE,0);if(consumed<0)break;data+=consumed;length-=consumed;if(!packetSize){if(!consumed)break;continue;}
        av_packet_unref(p.vpacket);if(av_new_packet(p.vpacket,packetSize)<0)break;memcpy(p.vpacket->data,packetData,packetSize);
        if(avcodec_send_packet(p.videoDecoder,p.vpacket)<0)continue;
        while(avcodec_receive_frame(p.videoDecoder,p.vframe)==0){
            const int sourceW=p.vframe->width,sourceH=p.vframe->height;if(sourceW<1||sourceH<1||sourceW>8192||sourceH>8192)return false;
            const double ratio=std::min(double(W)/sourceW,double(H)/sourceH);const int w=std::max(2,(int(sourceW*ratio)/2)*2),h=std::max(2,(int(sourceH*ratio)/2)*2),x=((W-w)/4)*2,y=((H-h)/4)*2;
            Picture picture{timestamp,std::vector<unsigned char>(W*H*3/2,128)};std::fill(picture.pixels.begin(),picture.pixels.begin()+W*H,16);
            unsigned char* output[]={picture.pixels.data()+y*W+x,picture.pixels.data()+W*H+(y/2)*(W/2)+x/2,picture.pixels.data()+W*H*5/4+(y/2)*(W/2)+x/2,nullptr};int stride[]={W,W/2,W/2,0};
            p.scaler=sws_getCachedContext(p.scaler,sourceW,sourceH,static_cast<AVPixelFormat>(p.vframe->format),w,h,AV_PIX_FMT_YUV420P,SWS_FAST_BILINEAR,nullptr,nullptr,nullptr);if(!p.scaler)return false;
            sws_scale(p.scaler,p.vframe->data,p.vframe->linesize,0,sourceH,output,stride);
            {std::lock_guard<std::mutex> lock(p.pictureLock);while(p.pictures.size()>=15)p.pictures.pop_front();p.pictures.push_back(std::move(picture));}decoded=true;av_frame_unref(p.vframe);
        }
    }
    return decoded;
}
bool LegacyMedia::audio(const unsigned char* data,int length,unsigned ct,unsigned spf,uint64_t ntp){
    auto& p=*impl;std::lock_guard<std::mutex> lock(p.audioLock);if(length<=0||length>256*1024||!p.configureAudio(ct,spf))return false;
    av_packet_unref(p.apacket);if(av_new_packet(p.apacket,length)<0)return false;memcpy(p.apacket->data,data,length);if(avcodec_send_packet(p.audioDecoder,p.apacket)<0)return false;
    uint64_t timestamp=ntp?ntp:get_local_time();bool decoded=false;
    while(avcodec_receive_frame(p.audioDecoder,p.aframe)==0){
        if(p.aframe->ch_layout.nb_channels!=2||p.aframe->sample_rate!=RATE)return false;
        Samples chunk{timestamp,std::vector<int16_t>(p.aframe->nb_samples*2)};
        for(int c=0;c<2;++c)for(int i=0;i<p.aframe->nb_samples;++i){float value;if(p.aframe->format==AV_SAMPLE_FMT_S16P)value=reinterpret_cast<int16_t*>(p.aframe->data[c])[i];else if(p.aframe->format==AV_SAMPLE_FMT_FLTP)value=reinterpret_cast<float*>(p.aframe->data[c])[i]*32768.0f;else return false;chunk.pcm[i*2+c]=static_cast<int16_t>(std::max(-32768.0f,std::min(32767.0f,value*p.gain.load())));}
        {std::lock_guard<std::mutex> lock(p.sampleLock);while(p.samples.size()>=180)p.samples.pop_front();p.samples.push_back(std::move(chunk));}
        timestamp+=p.aframe->nb_samples*1000000000ull/RATE;decoded=true;av_frame_unref(p.aframe);
    }
    return decoded;
}
