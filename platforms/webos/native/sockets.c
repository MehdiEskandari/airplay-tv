#include <sys/socket.h>
#include <sys/select.h>
#include <sys/ioctl.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <errno.h>
#include <stdarg.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <emscripten.h>

extern int ap_js_socket(int,int),ap_js_bind(int,const char*,int),ap_js_state(int),ap_js_accept(int,void*,void*),ap_js_name(int,void*,void*,int),ap_js_ready(int,int),ap_js_recv(int,void*,int,int,void*,void*),ap_js_send(int,const void*,int,const char*,int),ap_js_option(int,int,const void*,const void*),ap_js_available(int),ap_js_close(int);
static int timeouts[FD_SETSIZE],nonblocking[FD_SETSIZE];
extern int ap_js_shutdown(int);
static int result(int value){
    if(value>=0)return value;
    const int codes[]={EBADF,EAGAIN,EADDRINUSE,EINVAL,EAFNOSUPPORT,ECONNRESET,EACCES,EIO};
    errno=codes[-value>8?7:-value-1];return -1;
}
static void pause_worker(void){struct timespec delay={0,2000000};nanosleep(&delay,NULL);}
static int wait_bound(int fd){
    const double end=emscripten_get_now()+5000;int status;
    while((status=ap_js_state(fd))==0&&emscripten_get_now()<end)pause_worker();
    return status==1?0:result(status<0?status:-8);
}
int ap_socket(int family,int type,int protocol){(void)protocol;int fd=ap_js_socket(family,type);if(fd>=0){timeouts[fd]=0;nonblocking[fd]=0;}return result(fd);}
int ap_bind(int fd,const struct sockaddr* address,socklen_t length){
    if(!address||length<sizeof(struct sockaddr_in)||address->sa_family!=AF_INET){errno=EAFNOSUPPORT;return -1;}
    const struct sockaddr_in* v=(const void*)address;char ip[INET_ADDRSTRLEN];inet_ntop(AF_INET,&v->sin_addr,ip,sizeof(ip));
    if(result(ap_js_bind(fd,ip,ntohs(v->sin_port)))<0)return -1;return wait_bound(fd);
}
int ap_listen(int fd,int backlog){(void)backlog;return wait_bound(fd);}
int ap_accept(int fd,struct sockaddr* address,socklen_t* len){return result(ap_js_accept(fd,address,len));}
int ap_getsockname(int fd,struct sockaddr* address,socklen_t* len){return result(ap_js_name(fd,address,len,0));}
int ap_getpeername(int fd,struct sockaddr* address,socklen_t* len){return result(ap_js_name(fd,address,len,1));}
ssize_t ap_recvfrom(int fd,void* data,size_t size,int flags,struct sockaddr* address,socklen_t* len){
    if(fd<0||fd>=FD_SETSIZE){errno=EBADF;return -1;}
    double end=timeouts[fd]?emscripten_get_now()+timeouts[fd]:0;
    int value;
    while((value=ap_js_recv(fd,data,size,flags,address,len))==-2&&!nonblocking[fd]&&!(flags&MSG_DONTWAIT)){
        if(end&&emscripten_get_now()>=end)break;pause_worker();
    }
    return result(value);
}
ssize_t ap_recv(int fd,void* data,size_t size,int flags){return ap_recvfrom(fd,data,size,flags,NULL,NULL);}
ssize_t ap_sendto(int fd,const void* data,size_t size,int flags,const struct sockaddr* address,socklen_t len){
    (void)flags;char ip[INET_ADDRSTRLEN];int port=0;const char* target=NULL;
    if(address){if(len<sizeof(struct sockaddr_in)||address->sa_family!=AF_INET){errno=EINVAL;return -1;}const struct sockaddr_in* v=(const void*)address;inet_ntop(AF_INET,&v->sin_addr,ip,sizeof(ip));port=ntohs(v->sin_port);target=ip;}
    return result(ap_js_send(fd,data,size,target,port));
}
ssize_t ap_send(int fd,const void* data,size_t size,int flags){return ap_sendto(fd,data,size,flags,NULL,0);}
int ap_close(int fd){return result(ap_js_close(fd));}
int ap_shutdown(int fd,int how){if(how!=SHUT_RDWR){errno=ENOTSUP;return -1;}return result(ap_js_shutdown(fd));}
int ap_fcntl(int fd,int command,...){if(fd<0||fd>=FD_SETSIZE){errno=EBADF;return -1;}va_list args;va_start(args,command);if(command==F_SETFL)nonblocking[fd]=(va_arg(args,int)&O_NONBLOCK)!=0;va_end(args);return command==F_GETFL?(nonblocking[fd]?O_NONBLOCK:0):0;}
int ap_ioctl(int fd,int command,...){
    if(command!=FIONREAD){errno=ENOTSUP;return -1;}va_list args;va_start(args,command);int* value=va_arg(args,int*);va_end(args);int n=ap_js_available(fd);if(n<0)return result(n);*value=n;return 0;
}
int ap_setsockopt(int fd,int level,int option,const void* value,socklen_t len){
    if(fd<0||fd>=FD_SETSIZE){errno=EBADF;return -1;}
    if(level==SOL_SOCKET&&option==SO_RCVTIMEO){if(len<sizeof(struct timeval)){errno=EINVAL;return -1;}const struct timeval* t=value;timeouts[fd]=t->tv_sec*1000+t->tv_usec/1000;return 0;}
    if(level==IPPROTO_IP&&option==IP_ADD_MEMBERSHIP){if(len<sizeof(struct ip_mreq)){errno=EINVAL;return -1;}const struct ip_mreq* group=value;char ip[16],interface[16];inet_ntop(AF_INET,&group->imr_multiaddr,ip,sizeof(ip));inet_ntop(AF_INET,&group->imr_interface,interface,sizeof(interface));return result(ap_js_option(fd,1,ip,interface));}
    if(level==IPPROTO_IP&&option==IP_MULTICAST_IF){char ip[16];inet_ntop(AF_INET,value,ip,sizeof(ip));return result(ap_js_option(fd,2,ip,NULL));}
    if(level==IPPROTO_IP&&option==IP_MULTICAST_TTL)return result(ap_js_option(fd,3,(void*)(uintptr_t)*(const unsigned char*)value,NULL));
    return 0;
}
int ap_select(int count,fd_set* readfds,fd_set* writefds,fd_set* errors,struct timeval* timeout){
    if(count<0||count>FD_SETSIZE){errno=EINVAL;return -1;}
    fd_set originalRead,originalWrite;if(readfds)originalRead=*readfds;if(writefds)originalWrite=*writefds;
    const double end=timeout?emscripten_get_now()+timeout->tv_sec*1000+timeout->tv_usec/1000.0:0;
    for(;;){
        int ready=0;if(readfds)FD_ZERO(readfds);if(writefds)FD_ZERO(writefds);if(errors)FD_ZERO(errors);
        for(int fd=0;fd<count;++fd){
            int has=0;if(readfds&&FD_ISSET(fd,&originalRead)){int n=ap_js_ready(fd,0);if(n<0)return result(n);if(n){FD_SET(fd,readfds);has=1;}}
            if(writefds&&FD_ISSET(fd,&originalWrite)){int n=ap_js_ready(fd,1);if(n<0)return result(n);if(n){FD_SET(fd,writefds);has=1;}}ready+=has;
        }
        if(ready||(timeout&&emscripten_get_now()>=end))return ready;pause_worker();
    }
}
