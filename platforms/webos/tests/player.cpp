#include "../native/player.h"
#include <emscripten.h>
#include <memory>
static std::unique_ptr<Player> player;
extern "C" {
EMSCRIPTEN_KEEPALIVE void test_reset(){player.reset(new Player(1280,720,30,1,[](std::string){}));}
EMSCRIPTEN_KEEPALIVE int test_audio(const unsigned char* data,int size,int codec,int spf,double seconds){
    return player->audio(data,size,codec,spf,1000000000ULL+static_cast<uint64_t>(seconds*1e9),false);
}
}
