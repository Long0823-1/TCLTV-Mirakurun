"""Read-only black-box regression: Android one-shot and existing QU mDNS queries."""
import argparse,ipaddress,secrets,socket,struct,time

parser=argparse.ArgumentParser();parser.add_argument('--source',required=True);parser.add_argument('--tv-ip',required=True);parser.add_argument('--host',required=True);args=parser.parse_args()
assert ipaddress.ip_address(args.source).version==4 and ipaddress.ip_address(args.tv_ip).version==4
def read_name(data,at):
    labels=[];end=None;seen=set()
    for _ in range(128):
        assert at<len(data) and at not in seen;seen.add(at);n=data[at];at+=1
        if n==0:return '.'.join(labels).lower(),end or at
        if n&192==192:
            assert at<len(data)
            if end is None:end=at+1
            at=((n&63)<<8)|data[at];continue
        assert n<=63 and at+n<=len(data);labels.append(data[at:at+n].decode('ascii'));at+=n
    raise AssertionError('DNS name pointer loop')
checks=0
for klass in [1,0x8001]:
    for flags in [0,0x100]:
        ident=secrets.randbelow(65535)+1
        query=b''.join(bytes([len(x)])+x.encode('ascii') for x in args.host.split('.'))+b'\0'+struct.pack('!HH',1,klass)
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as udp:
            udp.bind((args.source,0));udp.setsockopt(socket.IPPROTO_IP,socket.IP_MULTICAST_IF,socket.inet_aton(args.source));udp.setsockopt(socket.IPPROTO_IP,socket.IP_MULTICAST_TTL,255)
            udp.settimeout(2);udp.sendto(struct.pack('!6H',ident,flags,1,0,0,0)+query,('224.0.0.251',5353))
            until=time.monotonic()+2;response=None
            while time.monotonic()<until:
                try:data,peer=udp.recvfrom(9000)
                except socket.timeout:break
                if peer==(args.tv_ip,5353) and len(data)>=12 and struct.unpack_from('!H',data)[0]==ident:response=data;break
            assert response is not None,f'No unicast response for class={klass:#x}, flags={flags:#x}'
            msg_id,response_flags,questions,answers,authority,additional=struct.unpack_from('!6H',response)
            assert msg_id==ident and response_flags&0x8000 and response_flags&15==0 and questions==1
            name,at=read_name(response,12);kind,qclass=struct.unpack_from('!HH',response,at);at+=4
            assert name==args.host.lower() and kind==1 and qclass&0x7fff==1
            ips=[]
            for _ in range(answers):
                name,at=read_name(response,at);kind,rclass,ttl,length=struct.unpack_from('!HHIH',response,at);at+=10
                assert not rclass&0x8000,'Legacy response must not set cache-flush'
                if name==args.host.lower() and kind==1 and length==4:ips.append(socket.inet_ntoa(response[at:at+4]))
                at+=length
            assert args.tv_ip in ips,(args.host,ips)
            checks+=1;print('PASS unicast reply, matching transaction/question/address; class',hex(klass),'flags',hex(flags),flush=True)
print('ALL PASS',checks,'Android legacy and existing QU cases')
