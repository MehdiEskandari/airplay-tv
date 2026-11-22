#include "media.h"
#include <atomic>
#include <cassert>
#include <chrono>
#include <cstring>
#include <csignal>
#include <algorithm>
#include <fstream>
#include <iterator>
#include <mutex>
#include <thread>
#include <unistd.h>
#include <vector>
extern "C" uint64_t get_local_time(){timespec now{};clock_gettime(CLOCK_REALTIME,&now);return uint64_t(now.tv_sec)*1000000000+now.tv_nsec;}
int main(int argc,char** argv){
 assert(argc==3);signal(SIGPIPE,SIG_IGN);
 int video[2],audio[2];assert(pipe(video)==0&&pipe(audio)==0);
 std::atomic<unsigned> pictures{0},sound{0};std::vector<unsigned char> last;std::mutex frameLock;
 std::thread videoReader([&]{std::vector<unsigned char> frame(1280*720*3/2);size_t received=0;for(;;){auto n=read(video[0],frame.data()+received,frame.size()-received);if(n<=0)break;received+=n;if(received==frame.size()){std::lock_guard<std::mutex> lock(frameLock);last=frame;pictures++;received=0;}}close(video[0]);});
 std::thread audioReader([&]{int16_t buffer[882];for(;;){auto n=read(audio[0],buffer,sizeof(buffer));if(n<=0)break;for(int i=0;i<n/2;i++)if(buffer[i]>30||buffer[i]<-30)sound++;}close(audio[0]);});
 {
  LegacyMedia media(video[1],audio[1],[](std::string message){fprintf(stderr,"%s\n",message.c_str());});
  std::ifstream source(argv[1],std::ios::binary);std::vector<unsigned char> bytes((std::istreambuf_iterator<char>(source)),{});
  const auto timestamp=get_local_time();bool decoded=false;
  for(size_t at=0;at<bytes.size();at+=1024)decoded=media.video(bytes.data()+at,std::min(size_t(1024),bytes.size()-at),timestamp)||decoded;
  assert(decoded);
  std::ifstream aac(argv[2],std::ios::binary);std::vector<unsigned char> encoded((std::istreambuf_iterator<char>(aac)),{});size_t at=0;uint64_t pts=timestamp;
  while(at+7<=encoded.size()){assert(encoded[at]==0xff&&(encoded[at+1]&0xf6)==0xf0);size_t length=((encoded[at+3]&3)<<11)|(encoded[at+4]<<3)|(encoded[at+5]>>5),header=(encoded[at+1]&1)?7:9;assert(length>=header&&at+length<=encoded.size());assert(media.audio(encoded.data()+at+header,length-header,4,1024,pts));pts+=1024ull*1000000000/44100;at+=length;}
  std::this_thread::sleep_for(std::chrono::milliseconds(900));
  std::lock_guard<std::mutex> lock(frameLock);assert(pictures>10);assert(last.size()==1280*720*3/2);
  assert(last[360*1280+640]>50);assert(last[360*1280+10]==16);assert(sound>500);
 }
 videoReader.join();audioReader.join();printf("Decoded H.264 portrait frames, verified 720p letterboxing and scheduled non-silent AAC audio; %u output frames.\n",pictures.load());
}
