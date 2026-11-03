package io.github.long0823.tclmirakurun;

import android.content.Context;
import java.net.*;
import java.util.*;
import javax.jmdns.*;

/** JmDNS probes host-name conflicts and publishes A, SRV, PTR and TXT records. */
final class MdnsPublisher {
    private final Map<String,JmDNS> active=new HashMap<>();private String lastHost="",allowed="";
    synchronized void refresh(Context context)throws Exception {
        NetworkProfile profile=NetworkSettings.load(context);String host=profile.hostname;Map<String,InetAddress> selected=new TreeMap<>();
        for(NetworkInterface iface:Collections.list(NetworkInterface.getNetworkInterfaces()))if(iface.isUp()&&!iface.isLoopback()){
            if(!iface.getName().equals("eth0")&&!iface.getName().startsWith("wlan"))continue;InetAddress best=null;
            if(iface.getName().equals("eth0")&&context.getSharedPreferences(NetworkSettings.PREFS,0).getString("wired-carrier","unknown").equals("0"))continue;
            for(InetAddress address:Collections.list(iface.getInetAddresses()))if(address instanceof Inet4Address&&!address.isLoopbackAddress()){if(best==null||best.isLinkLocalAddress()&&!address.isLinkLocalAddress())best=address;}
            if(best!=null&&!(profile.mode.equals("router")&&iface.getName().equals("eth0")&&best.isLinkLocalAddress()))selected.put(iface.getName()+"/"+best.getHostAddress(),best);
        }
        String primary="";for(Map.Entry<String,InetAddress> pair:selected.entrySet())if(pair.getKey().startsWith("eth0/")){primary=pair.getKey();break;}
        if(primary.isEmpty())for(Map.Entry<String,InetAddress> pair:selected.entrySet())if(!pair.getValue().isLinkLocalAddress()){primary=pair.getKey();break;}
        if(primary.isEmpty()&&!selected.isEmpty())primary=selected.keySet().iterator().next();
        String configuration=host+"|"+primary;if(!lastHost.equals(configuration)){close();lastHost=configuration;}
        for(String key:new ArrayList<>(active.keySet()))if(!selected.containsKey(key)){active.remove(key).close();}
        for(Map.Entry<String,InetAddress> pair:selected.entrySet())if(!active.containsKey(pair.getKey())){
            String suffix=pair.getKey().startsWith("eth0/")?"-wired":"-wifi";String advertised=pair.getKey().equals(primary)?host:host.substring(0,Math.min(host.length(),63-suffix.length())).replaceAll("-+$","")+suffix;
            JmDNS dns=JmDNS.create(pair.getValue(),advertised);try{Map<String,String> properties=new HashMap<>();properties.put("path","/");properties.put("api","/api/status");properties.put("app","TCLTV-Mirakurun");properties.put("id",NetworkSettings.deviceId(context));properties.put("transport",pair.getKey().startsWith("eth0/")?"wired":"wifi");dns.registerService(ServiceInfo.create("_mirakurun._tcp.local.","TCL TV Mirakurun "+advertised,40772,0,0,properties));active.put(pair.getKey(),dns);}catch(Exception e){dns.close();throw e;}
        }
        List<String> names=new ArrayList<>();StringBuilder status=new StringBuilder();for(Map.Entry<String,JmDNS> pair:active.entrySet()){String name=pair.getValue().getHostName().replaceAll("\\.$","").toLowerCase(Locale.ROOT);names.add(name);status.append("名前解決：http://").append(name).append(":40772/ （").append(pair.getKey()).append("）\n");}
        Collections.sort(names);if(!names.toString().equals(allowed)&&NetworkSettings.allowMdnsHosts(context,names))allowed=names.toString();
        context.getSharedPreferences(NetworkSettings.PREFS,0).edit().putString("mdns-status",status.length()==0?"mDNS：ネットワーク待ち":status.toString().trim()).apply();
    }
    synchronized void close(){for(JmDNS dns:active.values())try{dns.close();}catch(Exception ignored){}active.clear();}
}
