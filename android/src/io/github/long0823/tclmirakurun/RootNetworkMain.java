package io.github.long0823.tclmirakurun;

import android.os.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Android 11 Ethernet service adapter, executed by app_process as root. */
public final class RootNetworkMain {
    static final File BASE=new File("/data/local/tcl-mirakurun");
    static File f(String name){return new File(BASE,name);}
    static Object service()throws Exception {IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"ethernet");if(binder==null)throw new IOException("Ethernetサービスがありません");return Class.forName("android.net.IEthernetManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);}
    static Method method(String name,int count)throws Exception {for(Method m:Class.forName("android.net.IEthernetManager").getMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==count)return m;return null;}
    static Object get()throws Exception {Object svc=service();Method m=method("getConfiguration",1);if(m!=null)return m.invoke(svc,"eth0");m=method("getConfiguration",0);if(m==null)throw new IOException("対応するEthernet設定APIがありません");return m.invoke(svc);}
    static void set(Object config)throws Exception {Object svc=service();Method m=method("setConfiguration",2);if(m!=null){m.invoke(svc,"eth0",config);return;}m=method("setConfiguration",1);if(m==null)throw new IOException("対応するEthernet設定APIがありません");m.invoke(svc,config);}
    static void write(File file,byte[] bytes)throws Exception {File temp=new File(file.getPath()+".new");try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}if(!temp.renameTo(file))throw new IOException("ネットワーク設定を保存できません");}
    static byte[] bytes(File file)throws Exception {if(file.length()>65536)throw new IOException("設定サイズが不正です");try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toByteArray();}}
    static String text(File file,String fallback)throws Exception{return file.isFile()?new String(bytes(file),StandardCharsets.UTF_8).trim():fallback;}
    static byte[] parcel(Object config){Parcel p=Parcel.obtain();try{((Parcelable)config).writeToParcel(p,0);return p.marshall();}finally{p.recycle();}}
    static Object config(byte[] value)throws Exception {Parcel p=Parcel.obtain();try{p.unmarshall(value,0,value.length);p.setDataPosition(0);return ((Parcelable.Creator<?>)Class.forName("android.net.IpConfiguration").getField("CREATOR").get(null)).createFromParcel(p);}finally{p.recycle();}}
    @SuppressWarnings({"unchecked","rawtypes"}) static Object make(NetworkProfile profile)throws Exception {
        Class<?> type=Class.forName("android.net.IpConfiguration"),assignment=Class.forName("android.net.IpConfiguration$IpAssignment"),proxy=Class.forName("android.net.IpConfiguration$ProxySettings");Object ip=type.getConstructor().newInstance();
        type.getMethod("setIpAssignment",assignment).invoke(ip,Enum.valueOf((Class)assignment,profile.mode.equals("static")?"STATIC":"DHCP"));type.getMethod("setProxySettings",proxy).invoke(ip,Enum.valueOf((Class)proxy,"NONE"));
        if(profile.mode.equals("static")){Class<?> staticType=Class.forName("android.net.StaticIpConfiguration");Object st=staticType.getConstructor().newInstance();Object address=Class.forName("android.net.LinkAddress").getConstructor(InetAddress.class,int.class).newInstance(InetAddress.getByName(profile.address),Integer.parseInt(profile.prefix));st.getClass().getField("ipAddress").set(st,address);if(!profile.gateway.isEmpty())st.getClass().getField("gateway").set(st,InetAddress.getByName(profile.gateway));List dns=(List)st.getClass().getField("dnsServers").get(st);for(String value:new String[]{profile.dns1,profile.dns2})if(!value.isEmpty())dns.add(InetAddress.getByName(value));type.getMethod("setStaticIpConfiguration",staticType).invoke(ip,st);}
        return ip;
    }
    static boolean pending(String token)throws Exception{return token.matches("[a-f0-9]{32}")&&text(f("ethernet-pending"),"").equals(token);}
    static void clearPending(){for(String name:new String[]{"ethernet-pending","ethernet-pending.parcel","ethernet-pending-mode"})f(name).delete();}
    static void rollback(String token)throws Exception {if(!pending(token))return;set(config(bytes(f("ethernet-pending.parcel"))));write(f("network-mode"),text(f("ethernet-pending-mode"),"auto").getBytes(StandardCharsets.UTF_8));clearPending();System.out.println("接続設定を変更前に戻しました");}
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0||args.length<1)throw new SecurityException();if(!BASE.isDirectory()&&!BASE.mkdirs())throw new IOException("設定フォルダーを作成できません");if(!BASE.getCanonicalPath().equals(BASE.getAbsolutePath()))throw new SecurityException("設定フォルダーがリンクです");
        try(FileOutputStream lock=new FileOutputStream(f("ethernet-config.lock"),true);java.nio.channels.FileLock guard=lock.getChannel().lock()){run(args);}
    }
    static void run(String[] args)throws Exception {
        if(args[0].equals("inspect")){System.out.println("Ethernet current: "+get());System.out.println("setConfiguration API: "+(method("setConfiguration",2)!=null?"interface + config":method("setConfiguration",1)!=null?"config":"unsupported"));return;}
        if(args[0].equals("self-test")){Object before=get(),restored=config(parcel(before));if(!before.equals(restored))throw new IOException("Ethernet configuration parcel roundtrip mismatch");NetworkProfile test=new NetworkProfile();test.mode="static";test.hostname="network-test";test.address="10.244.245.246";test.prefix="24";test.gateway="10.244.245.1";test.dns1="1.1.1.1";test.validate();Object created=make(test);if(created==null)throw new IOException("Static configuration construction failed");test.mode="auto";if(make(test)==null)throw new IOException("DHCP configuration construction failed");if(!before.equals(get()))throw new IOException("Read-only test observed changed configuration");System.out.println("PASS read-only Ethernet parcel/static/DHCP construction; no setConfiguration called");return;}
        if(args[0].equals("recover-pending")){String token=text(f("ethernet-pending"),"");if(!token.isEmpty())rollback(token);return;}
        if(args[0].equals("confirm")){if(args.length!=2||!pending(args[1]))throw new IOException("確認期限が切れました。変更前の設定へ戻します");clearPending();System.out.println("ネットワーク設定を確定しました");return;}
        if(args[0].equals("rollback")){if(args.length!=2)throw new IllegalArgumentException();rollback(args[1]);return;}
        if(args[0].equals("restore-original")){String pending=text(f("ethernet-pending"),"");if(!pending.isEmpty())rollback(pending);if(f("ethernet-original.parcel").isFile()){set(config(bytes(f("ethernet-original.parcel"))));f("ethernet-original.parcel").delete();}return;}
        if(!args[0].equals("apply")||args.length!=9||!args[8].matches("[a-f0-9]{32}"))throw new IllegalArgumentException();
        NetworkProfile profile=new NetworkProfile();profile.mode=args[1];profile.hostname=args[2];profile.address=args[3];profile.prefix=args[4];profile.gateway=args[5];profile.dns1=args[6];profile.dns2=args[7];profile.validate();
        String previous=text(f("ethernet-pending"),"");if(!previous.isEmpty())rollback(previous);
        byte[] old=parcel(get());if(!f("ethernet-original.parcel").isFile())write(f("ethernet-original.parcel"),old);write(f("ethernet-pending.parcel"),old);write(f("ethernet-pending-mode"),text(f("network-mode"),"auto").getBytes(StandardCharsets.UTF_8));write(f("ethernet-pending"),args[8].getBytes(StandardCharsets.UTF_8));
        String command="(/system/bin/sleep 60; for attempt in 1 2 3 4 5; do CLASSPATH=/data/local/tcl-mirakurun/network-helper.jar /system/bin/app_process /system/bin io.github.long0823.tclmirakurun.RootNetworkMain rollback "+args[8]+" && break; /system/bin/sleep 5; done) </dev/null >>/data/local/tcl-mirakurun/network-rollback.log 2>&1 &";
        new ProcessBuilder("/system/bin/sh","-c",command).start().waitFor();
        try{set(make(profile));write(f("network-mode"),profile.mode.getBytes(StandardCharsets.UTF_8));}catch(Exception e){rollback(args[8]);throw e;}
        System.out.println("設定を仮適用しました。60秒以内にTVの画面で確定してください。未確認なら自動で戻します。");
    }
}
