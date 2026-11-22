#pragma once
#include <cstdint>
#include <functional>
#include <memory>
#include <string>

// The host turns elementary AirPlay packets into clocked 720p/PCM inputs for
// the external HLS encoder. No native executable is installed on the old TV.
class LegacyMedia {
public:
    LegacyMedia(int videoFd,int audioFd,std::function<void(std::string)> error);
    ~LegacyMedia();
    bool video(const unsigned char* data,int length,uint64_t ntpNs);
    bool audio(const unsigned char* data,int length,unsigned codec,unsigned spf,uint64_t ntpNs);
    void volume(float db);
private:
    struct Impl;
    std::unique_ptr<Impl> impl;
};
