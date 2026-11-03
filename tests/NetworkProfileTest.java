package io.github.long0823.tclmirakurun;
import java.util.*;
import javax.jmdns.*;
import java.net.*;
public final class NetworkProfileTest {
 static int checks;static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static NetworkProfile profile(){NetworkProfile p=new NetworkProfile();p.mode="static";p.hostname="my-tv.local";p.address="192.168.1.160";p.prefix="24";p.gateway="192.168.1.1";p.dns1="192.168.1.1";return p;}
 static void reject(NetworkProfile p,String name){try{p.validate();throw new AssertionError(name);}catch(IllegalArgumentException expected){checks++;System.out.println("PASS "+name);}}
 public static void main(String[] args)throws Exception {
  if(args.length>0&&args[0].equals("--publish")){try(JmDNS dns=JmDNS.create(InetAddress.getByName(args[1]),args[2])){dns.registerService(ServiceInfo.create("_mirakurun._tcp.local.","Network test "+args[2],Integer.parseInt(args[3]),"path=/"));System.out.println("PUBLISHED "+dns.getHostName());System.out.flush();Thread.sleep(45000);}return;}
  NetworkProfile p=profile();p.validate();check(p.hostname.equals("my-tv"),"local suffix normalized");
  p=profile();p.address="169.254.95.200";p.prefix="16";p.gateway=p.dns1="";p.validate();check(true,"static direct link without gateway and DNS accepted");
  p=profile();p.hostname="bad;reboot";reject(p,"hostname shell input rejected");p=profile();p.hostname="-tv";reject(p,"hostname cannot begin with hyphen");p=profile();p.address="192.168.1.256";reject(p,"IPv4 octets bounded");p=profile();p.address="192.168.1.0";reject(p,"network address rejected");p=profile();p.address="192.168.1.255";reject(p,"broadcast address rejected");p=profile();p.gateway="192.168.2.1";reject(p,"gateway must be in local subnet");p=profile();p.dns1="";reject(p,"router configuration requires DNS");p=profile();p.prefix="0";reject(p,"default route cannot be used as static subnet");
  check(NetworkProfile.classify(false,false,Collections.emptyList()).contains("未接続"),"disconnected cable classification");check(NetworkProfile.classify(true,true,Arrays.asList("192.168.1.160")).contains("推定"),"router or shared PC is explicitly inferred");check(NetworkProfile.classify(true,false,Arrays.asList("169.254.95.200")).contains("DHCP未取得"),"link local cannot falsely prove physical direct connection");check(NetworkProfile.classify(true,false,Arrays.asList("192.168.1.160")).contains("経路未確認"),"private static IP without gateway is not called a router");
  System.out.println("ALL PASS "+checks);
 }
}
