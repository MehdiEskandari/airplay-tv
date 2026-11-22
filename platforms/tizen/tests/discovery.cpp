#include "mdns.h"
#include <cassert>
#include <cstring>
#include <iostream>
#include <random>

int main() {
    const unsigned char query[]={0,0,0,0,0,1,0,0,0,0,0,0,8,'_','a','i','r','p','l','a','y',4,'_','t','c','p',5,'l','o','c','a','l',0,0,12,0,1};
    assert(discovery::isQuestion(query,sizeof(query)));
    assert(!discovery::isQuestion(query,sizeof(query)-1));
    auto invalid=query[2];(void)invalid;
    std::mt19937 random(421);
    for(unsigned i=0;i<10000;++i){std::vector<unsigned char> data(random()%200);for(auto& c:data)c=random();discovery::isQuestion(data.data(),data.size());}
    auto packet=discovery::response("airplay-test.local","192.168.1.20",{{"ایر پلی.تی وی","_airplay._tcp.local",7000,{"features=0x5A7FFFF7,0x1E","name=test"}},{"02AABBCCDDEE@AirPlay TV","_raop._tcp.local",7000,{"cn=1,2,3"}}},120);
    assert(!discovery::isQuestion(packet.data(),packet.size()));
    std::cout.write(reinterpret_cast<const char*>(packet.data()),packet.size());
}
