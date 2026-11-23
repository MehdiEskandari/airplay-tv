#pragma once
#include <atomic>
#include <string>
#include <thread>
#include <vector>

// Shared wire encoder is independent of Samsung APIs and tested on the host.
namespace discovery {
struct Service {
    std::string instance, type;
    unsigned short port;
    std::vector<std::string> txt;
};
std::vector<unsigned char> response(const std::string& host, const std::string& ip,
                                  const std::vector<Service>& services, unsigned ttl);
bool isQuestion(const unsigned char* data, size_t length);
class Advertiser {
public:
    bool start(std::string host, std::string ip, std::vector<Service> services);
    void stop();
    ~Advertiser() { stop(); }
private:
    int socket_ = -1;
    std::atomic<bool> running_{false};
    std::thread worker_;
};
}
