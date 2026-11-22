#pragma once
#include <functional>
#include <memory>
#include <string>
#include <cstdint>

// All Samsung EMSS operations run on worker threads, never the browser thread.
class Player {
public:
    Player(unsigned width,unsigned height,unsigned fps,std::function<void(std::string)> error);
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
