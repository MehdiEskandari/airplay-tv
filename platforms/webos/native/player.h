#pragma once
#include <functional>
#include <memory>
#include <string>
#include <cstdint>

// FFmpeg audio decoding runs on the core workers; H.264 and PCM go to the LG web app.
class Player {
public:
    Player(unsigned width,unsigned height,unsigned fps,unsigned run,std::function<void(std::string)> error);
    ~Player();
    bool video(const unsigned char* data,int size,uint64_t ntp);
    bool audio(const unsigned char* data,int size,unsigned codec,unsigned spf,uint64_t ntp,bool screen);
    void volume(float db);
    void size(unsigned width,unsigned height);
    void stop();
private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};
