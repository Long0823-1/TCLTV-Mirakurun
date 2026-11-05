/* Bridge between a Mirakurun tuner command and the TV's TS service (replaces the former python script). */
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

static void on_term(int s){(void)s;_exit(0);}
static void note(const char *tag,const char *msg){
    int fd=open("/var/run/tcl-tuner.log",O_WRONLY|O_CREAT|O_APPEND,0644);
    if(fd<0)return;
    char line[300];int n=snprintf(line,sizeof line,"%ld %s %s\n",(long)time(0),tag,msg);
    if(n>0)(void)!write(fd,line,(size_t)n);
    close(fd);
}
static int connect_local(int port,int timeout_s){
    int fd=socket(AF_INET,SOCK_STREAM,0);if(fd<0)return -1;
    struct sockaddr_in a;memset(&a,0,sizeof a);a.sin_family=AF_INET;a.sin_port=htons((unsigned short)port);a.sin_addr.s_addr=htonl(INADDR_LOOPBACK);
    struct timeval tv={timeout_s,0};
    setsockopt(fd,SOL_SOCKET,SO_SNDTIMEO,&tv,sizeof tv);
    if(connect(fd,(struct sockaddr*)&a,sizeof a)){close(fd);return -1;}
    return fd;
}
static void set_recv_timeout(int fd,int s){struct timeval tv={s,0};setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&tv,sizeof tv);}

int main(int argc,char **argv){
    if(argc<3)return 2;
    int tuner=atoi(argv[1]);unsigned onid,tsid,sid;
    if((tuner!=0&&tuner!=1)||sscanf(argv[2],"%u_%u_%u",&onid,&tsid,&sid)!=3)return 2;
    signal(SIGPIPE,SIG_IGN);signal(SIGTERM,on_term);signal(SIGINT,on_term);
    char path[64];snprintf(path,sizeof path,"/var/run/tcl-tuner-%d.lock",tuner);
    int lock=open(path,O_WRONLY|O_CREAT,0644);
    if(lock<0){note("lock-open",strerror(errno));return 1;}
    /* the previous bridge may still be shutting down: wait for it instead of failing */
    int got=0;for(int i=0;i<200;i++){if(!flock(lock,LOCK_EX|LOCK_NB)){got=1;break;}usleep(100000);}
    if(!got){note("lock-timeout",argv[2]);return 1;}
    char token[128]={0};FILE *tf=fopen("/run/tcl/tsd_token","r");
    if(!tf||!fgets(token,sizeof token,tf)){note("token","unreadable");return 1;}
    fclose(tf);token[strcspn(token,"\r\n")]=0;
    char reply[256]={0};
    for(int attempt=0;attempt<2;attempt++){
        int ctl=connect_local(40557,10);
        if(ctl<0){note("ctl-connect",strerror(errno));return 1;}
        set_recv_timeout(ctl,45);
        char cmd[256];int n=snprintf(cmd,sizeof cmd,"AUTH %s TUNE %d %u %u %u\n",token,tuner,onid,tsid,sid);
        if(write(ctl,cmd,(size_t)n)!=n){close(ctl);note("ctl-write",strerror(errno));return 1;}
        memset(reply,0,sizeof reply);ssize_t r=read(ctl,reply,sizeof reply-1);close(ctl);
        if(r>0){reply[strcspn(reply,"\r\n")]=0;}else reply[0]=0;
        if(!strncmp(reply,"OK ",3))break;
        char msg[300];snprintf(msg,sizeof msg,"%s %s try=%d",argv[2],reply[0]?reply:"(no reply)",attempt);note("tune",msg);
        if(!strcmp(reply,"ERR superseded"))return 0;   /* a newer request took over this tuner */
        if(attempt==0)usleep(700000);else return 0;   /* channel cannot be tuned: end cleanly so Mirakurun does not respawn it forever */
    }
    int s=connect_local(tuner==0?40556:40558,10);
    if(s<0){note("stream-connect",strerror(errno));return 1;}
    set_recv_timeout(s,20);
    static char buf[65536];
    for(;;){
        ssize_t r=read(s,buf,sizeof buf);
        if(r==0)return 0;
        if(r<0){if(errno==EINTR)continue;note("stream",errno==EAGAIN?"stalled":strerror(errno));return 1;}
        ssize_t off=0;while(off<r){ssize_t w=write(1,buf+off,(size_t)(r-off));if(w<0){if(errno==EINTR)continue;return 0;}off+=w;}
    }
}
