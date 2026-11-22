#include "mdns.h"
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <unistd.h>
#include <cstring>
#include <chrono>

namespace discovery {
using Bytes = std::vector<unsigned char>;
static void u16(Bytes& b, unsigned v) { b.push_back(v >> 8); b.push_back(v); }
static void u32(Bytes& b, unsigned v) { u16(b, v >> 16); u16(b, v); }
static void name(Bytes& b, const std::string& s) {
    size_t pos = 0;
    while (pos < s.size()) {
        auto end = s.find('.', pos); if (end == std::string::npos) end = s.size();
        const auto n = end - pos;
        if (!n || n > 63) return;
        b.push_back(n); b.insert(b.end(), s.begin()+pos, s.begin()+end); pos = end+1;
    }
    b.push_back(0);
}
static std::string label(std::string s) {
    if (s.size() > 63) {
        size_t end = 63;
        while (end && (static_cast<unsigned char>(s[end]) & 0xc0) == 0x80) --end;
        s.resize(end);
    }
    // Dots are literal bytes inside the single instance label.
    return s;
}
static Bytes instance(const Service& service) {
    Bytes b; const auto instance = label(service.instance);
    b.push_back(instance.size()); b.insert(b.end(), instance.begin(), instance.end());
    name(b, service.type); return b;
}
static void rr(Bytes& b, const Bytes& owner, unsigned type, bool unique, unsigned ttl, const Bytes& data) {
    b.insert(b.end(), owner.begin(), owner.end()); u16(b,type); u16(b,unique?0x8001:1);
    u32(b,ttl); u16(b,data.size()); b.insert(b.end(),data.begin(),data.end());
}
Bytes response(const std::string& host, const std::string& ip, const std::vector<Service>& services, unsigned ttl) {
    Bytes b; u16(b,0);u16(b,0x8400);u16(b,0);u16(b,1+services.size()*4);u16(b,0);u16(b,0);
    Bytes hostName;name(hostName,host);
    in_addr address{}; if (inet_pton(AF_INET,ip.c_str(),&address) != 1) return {};
    auto p=reinterpret_cast<unsigned char*>(&address.s_addr);
    rr(b,hostName,1,true,ttl,Bytes(p,p+4));
    for(const auto& service:services) {
        Bytes type;name(type,service.type);Bytes enumeration;name(enumeration,"_services._dns-sd._udp.local");
        rr(b,enumeration,12,false,ttl,type);
        rr(b,type,12,false,ttl,instance(service));
        Bytes srv;u16(srv,0);u16(srv,0);u16(srv,service.port);srv.insert(srv.end(),hostName.begin(),hostName.end());
        rr(b,instance(service),33,true,ttl,srv);
        Bytes txt;
        for(const auto& s:service.txt) if(s.size()<=255){txt.push_back(s.size());txt.insert(txt.end(),s.begin(),s.end());}
        if(txt.empty())txt.push_back(0);
        rr(b,instance(service),16,true,ttl,txt);
    }
    return b;
}
bool isQuestion(const unsigned char* data, size_t length) {
    if(length<12 || (data[2]&0x80))return false;
    const unsigned count=(data[4]<<8)|data[5]; if(!count || count>64)return false;
    size_t offset=12;
    for(unsigned i=0;i<count;++i) {
        unsigned labels=0;
        for(;;) {
            if(offset>=length || ++labels>128)return false;
            unsigned n=data[offset++]; if(!n)break;
            if((n&0xc0)==0xc0){if(offset>=length)return false;++offset;break;}
            if(n>63 || offset+n>length)return false;offset+=n;
        }
        if(offset+4>length)return false;offset+=4;
    }
    return true;
}
bool Advertiser::start(std::string host, std::string ip, std::vector<Service> services) {
    stop();socket_=socket(AF_INET,SOCK_DGRAM,0);if(socket_<0)return false;
    int one=1;setsockopt(socket_,SOL_SOCKET,SO_REUSEADDR,&one,sizeof(one));
    sockaddr_in local{};local.sin_family=AF_INET;local.sin_port=htons(5353);local.sin_addr.s_addr=INADDR_ANY;
    ip_mreq group{};inet_pton(AF_INET,"224.0.0.251",&group.imr_multiaddr);inet_pton(AF_INET,ip.c_str(),&group.imr_interface);
    unsigned char ttl=255;
    if(bind(socket_,reinterpret_cast<sockaddr*>(&local),sizeof(local))<0 ||
       setsockopt(socket_,IPPROTO_IP,IP_ADD_MEMBERSHIP,&group,sizeof(group))<0 ||
       setsockopt(socket_,IPPROTO_IP,IP_MULTICAST_IF,&group.imr_interface,sizeof(group.imr_interface))<0 ||
       setsockopt(socket_,IPPROTO_IP,IP_MULTICAST_TTL,&ttl,sizeof(ttl))<0) {
        close(socket_);socket_=-1;return false;
    }
    running_=true;const int fd=socket_;
    worker_=std::thread([this,fd,host,ip,services] {
        sockaddr_in multicast{};multicast.sin_family=AF_INET;multicast.sin_port=htons(5353);inet_pton(AF_INET,"224.0.0.251",&multicast.sin_addr);
        const auto packet=response(host,ip,services,120);
        auto send=[&](const Bytes& b,const sockaddr_in& target){return sendto(fd,b.data(),b.size(),0,reinterpret_cast<const sockaddr*>(&target),sizeof(target));};
        send(packet,multicast); auto next=std::chrono::steady_clock::now()+std::chrono::seconds(1);
        while(running_) {
            fd_set fds;FD_ZERO(&fds);FD_SET(fd,&fds);timeval timeout{0,250000};
            if(select(fd+1,&fds,nullptr,nullptr,&timeout)>0) {
                unsigned char data[9000];sockaddr_in peer{};socklen_t size=sizeof(peer);
                const auto n=recvfrom(fd,data,sizeof(data),0,reinterpret_cast<sockaddr*>(&peer),&size);
                if(n>0 && isQuestion(data,n)) {
                    if(ntohs(peer.sin_port)!=5353) { auto unicast=packet;unicast[0]=data[0];unicast[1]=data[1];send(unicast,peer); }
                    else send(packet,multicast);
                }
            }
            if(std::chrono::steady_clock::now()>=next){send(packet,multicast);next=std::chrono::steady_clock::now()+std::chrono::seconds(60);}
        }
        send(response(host,ip,services,0),multicast);
    });return true;
}
void Advertiser::stop(){running_=false;if(worker_.joinable())worker_.join();if(socket_>=0){close(socket_);socket_=-1;}}
}
