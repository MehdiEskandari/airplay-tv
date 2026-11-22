#include "player.h"
#include <algorithm>
#include <atomic>
#include <cmath>
#include <condition_variable>
#include <cstring>
#include <mutex>
#include <vector>
#include "samsung/html/html_media_element.h"
#include "samsung/wasm/elementary_media_stream_source.h"
#include "samsung/wasm/elementary_media_stream_source_listener.h"
#include "samsung/wasm/elementary_media_track.h"
#include "samsung/wasm/elementary_media_track_listener.h"
#include "samsung/wasm/elementary_audio_track_config.h"
#include "samsung/wasm/elementary_video_track_config.h"
#include "samsung/wasm/elementary_media_packet.h"
extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/mem.h>
}
using namespace samsung::wasm;

struct Player::Impl {
    struct AsyncResult {
        std::mutex mutex;
        std::condition_variable changed;
        std::atomic<bool> failed{false};
        std::function<void(std::string)> error;
        void fail(const std::string& message){if(!failed.exchange(true))error(message);changed.notify_all();}
    };
    std::shared_ptr<AsyncResult> result=std::make_shared<AsyncResult>();
    std::atomic<unsigned> width,height;
    unsigned fps;
    std::mutex startupMutex,audioMutex,clockMutex;
    std::mutex& eventMutex=result->mutex;
    std::condition_variable& changed=result->changed;
    std::atomic<bool>& failed=result->failed;
    std::atomic<bool> stopping{false},videoOpen{false},audioOpen{false};
    bool configured=false,withVideo=false,sourceClosed=false,sourcePending=false;
    std::atomic<SessionId> videoSession{kIgnoreSessionId},audioSession{kIgnoreSessionId};
    uint64_t baseNtp=0;
    std::atomic<float> gain{1};
    ElementaryMediaStreamSource source{EmssLatencyMode::kUltraLow,EmssRenderingMode::kMediaElement};
    samsung::html::HTMLMediaElement element{"receiver-video"};
    ElementaryMediaTrack videoTrack,audioTrack;
    struct SourceListener:ElementaryMediaStreamSourceListener {
        Impl* p;
        explicit SourceListener(Impl* owner):p(owner){}
        void OnSourceClosed() override {std::lock_guard<std::mutex> lock(p->eventMutex);p->sourceClosed=true;p->changed.notify_all();}
        void OnSourceOpenPending() override {std::lock_guard<std::mutex> lock(p->eventMutex);p->sourcePending=true;p->changed.notify_all();}
        void OnPipelineError(MediaPipelineError,const char*) override {p->fail("Samsung media pipeline failed");}
    } sourceListener{this};
    struct TrackListener:ElementaryMediaTrackListener {
        Impl* p;bool video;
        TrackListener(Impl* owner,bool v):p(owner),video(v){}
        void OnTrackOpen() override {(video?p->videoOpen:p->audioOpen)=true;p->changed.notify_all();}
        void OnTrackClosed(ElementaryMediaTrack::CloseReason) override {(video?p->videoOpen:p->audioOpen)=false;p->changed.notify_all();}
        void OnSessionIdChanged(SessionId id) override {(video?p->videoSession:p->audioSession)=id;}
        void OnAppendError(OperationResult result) override {if(result!=OperationResult::kAppendIgnored)p->fail("Samsung media append failed");}
    } videoListener{this,true},audioListener{this,false};
    AVCodecContext* decoder=nullptr;
    AVPacket* packet=nullptr;AVFrame* frame=nullptr;
    unsigned codec=0,spf=0;

    Impl(unsigned w,unsigned h,unsigned f,std::function<void(std::string)> e):width(w),height(h),fps(f){result->error=std::move(e);}
    ~Impl(){stop();clearDecoder();}
    void fail(const std::string& message){result->fail(message);}
    template<class Predicate> bool wait(Predicate predicate) {
        std::unique_lock<std::mutex> lock(eventMutex);
        return changed.wait_for(lock,std::chrono::seconds(8),[&]{return stopping||failed||predicate();})&&!stopping&&!failed;
    }
    bool ensure(bool video) {
        std::lock_guard<std::mutex> startup(startupMutex);
        if(stopping||failed)return false;
        if(configured)return !video||withVideo;
        withVideo=video;source.SetListener(&sourceListener);
        if(!source.IsValid()||!element.SetSrc(&source)||!wait([&]{return sourceClosed;})){fail("Cannot attach Samsung player");return false;}
        auto audioResult=source.AddTrack(ElementaryAudioTrackConfig{"audio/webm; codecs=\"pcm\"",{},SampleFormat::kS16,ChannelLayout::kStereo,44100});
        if(!audioResult){fail("PCM output is unavailable");return false;}
        audioTrack=std::move(*audioResult);audioTrack.SetListener(&audioListener);
        if(video) {
            auto result=source.AddTrack(ElementaryVideoTrackConfig{fps>30?"video/mp4; codecs=\"avc1.64002a\"":"video/mp4; codecs=\"avc1.640028\"",{},width.load(),height.load(),fps,1});
            if(!result){fail("H.264 decoder is unavailable");return false;}
            videoTrack=std::move(*result);videoTrack.SetListener(&videoListener);
        }
        if(!source.Open([state=result](OperationResult r){if(r!=OperationResult::kSuccess)state->fail("Cannot open Samsung player");})||!wait([&]{return sourcePending;})){fail("Samsung player open timed out");return false;}
        if(!element.Play([state=result](OperationResult r){if(r!=OperationResult::kSuccess)state->fail("Cannot start Samsung player");})||!wait([&]{return audioOpen&&(!video||videoOpen);})){fail("Samsung tracks did not open");return false;}
        configured=true;return true;
    }
    Seconds time(uint64_t ntp) {
        std::lock_guard<std::mutex> lock(clockMutex);if(!baseNtp)baseNtp=ntp;
        return Seconds(static_cast<int64_t>(ntp-baseNtp)/1000000000.0+0.12);
    }
    void clearDecoder(){avcodec_free_context(&decoder);av_packet_free(&packet);av_frame_free(&frame);codec=spf=0;}
    bool configureAudio(unsigned ct,unsigned samples) {
        if(decoder&&codec==ct&&spf==samples)return true;
        clearDecoder();
        if(ct!=2&&ct!=4&&ct!=8)return false;
        const auto* c=avcodec_find_decoder(ct==2?AV_CODEC_ID_ALAC:AV_CODEC_ID_AAC);if(!c)return false;
        decoder=avcodec_alloc_context3(c);if(!decoder)return false;
        uint8_t csd[36]={};unsigned size=0;
        if(ct==2) {
            const uint8_t header[]={0,0,0,36,'a','l','a','c',0,0,0,0};memcpy(csd,header,12);
            csd[12]=samples>>24;csd[13]=samples>>16;csd[14]=samples>>8;csd[15]=samples;
            csd[17]=16;csd[18]=40;csd[19]=10;csd[20]=14;csd[21]=2;csd[23]=255;
            csd[32]=44100>>24;csd[33]=44100>>16;csd[34]=(44100>>8)&255;csd[35]=44100&255;size=36;
        } else if(ct==8){csd[0]=0xf8;csd[1]=0xe8;csd[2]=samples==512?0x40:0x50;size=4;}
        else {csd[0]=0x12;csd[1]=samples==960?0x14:0x10;size=2;}
        decoder->extradata=static_cast<uint8_t*>(av_mallocz(size+AV_INPUT_BUFFER_PADDING_SIZE));
        if(!decoder->extradata)return false;memcpy(decoder->extradata,csd,size);decoder->extradata_size=size;
        decoder->sample_rate=44100;av_channel_layout_default(&decoder->ch_layout,2);decoder->thread_count=1;
        if(avcodec_open2(decoder,c,nullptr)<0)return false;
        packet=av_packet_alloc();frame=av_frame_alloc();
        if(!packet||!frame){clearDecoder();return false;}
        codec=ct;spf=samples;return true;
    }
    void stop() {
        stopping=true;changed.notify_all();
        std::lock_guard<std::mutex> startup(startupMutex);
        source.Close([](OperationResult){});source.SetListener(nullptr);
        videoTrack.SetListener(nullptr);audioTrack.SetListener(nullptr);element.Pause();
    }
};
Player::Player(unsigned w,unsigned h,unsigned fps,std::function<void(std::string)> error):impl_(new Impl(w,h,fps,std::move(error))){}
Player::~Player()=default;
void Player::stop(){impl_->stop();}
void Player::volume(float db){impl_->gain=db<=-144?0:std::min(1.0f,std::pow(10.0f,db/20.0f));}
void Player::size(unsigned w,unsigned h){if(w&&h&&w<=8192&&h<=8192){impl_->width=w;impl_->height=h;}}
bool Player::video(const unsigned char* data,int size,uint64_t ntp) {
    auto& p=*impl_;if(size<=0||size>8*1024*1024||!p.ensure(true))return false;
    bool key=false;for(int i=0;i+4<size;++i)if(data[i]==0&&data[i+1]==0&&data[i+2]==1&&(data[i+3]&31)==5){key=true;break;}
    const auto pts=p.time(ntp);if(pts.count()<0)return true;
    ElementaryMediaPacket packet{pts,pts,Seconds(1.0/p.fps),key,static_cast<size_t>(size),data,p.width.load(),p.height.load(),p.fps,1,p.videoSession.load()};
    return static_cast<bool>(p.videoTrack.AppendPacket(packet));
}
bool Player::audio(const unsigned char* data,int size,unsigned codec,unsigned spf,uint64_t ntp,bool screen) {
    auto& p=*impl_;std::lock_guard<std::mutex> lock(p.audioMutex);
    if(size<=0||size>256*1024||!p.ensure(screen)||!p.configureAudio(codec,spf))return false;
    av_packet_unref(p.packet);if(av_new_packet(p.packet,size)<0)return false;memcpy(p.packet->data,data,size);
    if(avcodec_send_packet(p.decoder,p.packet)<0)return false;
    auto pts=p.time(ntp);
    while(avcodec_receive_frame(p.decoder,p.frame)==0) {
        if(p.frame->ch_layout.nb_channels!=2||p.frame->sample_rate!=44100)return false;
        const int n=p.frame->nb_samples;std::vector<int16_t> pcm(n*2);
        for(int c=0;c<2;++c)for(int i=0;i<n;++i) {
            float value;
            if(p.frame->format==AV_SAMPLE_FMT_S16P)value=reinterpret_cast<int16_t*>(p.frame->data[c])[i];
            else if(p.frame->format==AV_SAMPLE_FMT_FLTP)value=reinterpret_cast<float*>(p.frame->data[c])[i]*32768.0f;
            else return false;
            pcm[i*2+c]=static_cast<int16_t>(std::max(-32768.0f,std::min(32767.0f,value*p.gain.load())));
        }
        ElementaryMediaPacket packet{pts,pts,Seconds(n/44100.0),true,pcm.size()*sizeof(int16_t),pcm.data(),0,0,0,0,p.audioSession.load()};
        if(pts.count()>=0&&!p.audioTrack.AppendPacket(packet))return false;
        pts+=Seconds(n/44100.0);av_frame_unref(p.frame);
    }
    return true;
}
