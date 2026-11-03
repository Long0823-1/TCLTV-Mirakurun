package io.github.long0823.tclmirakurun;

import java.util.*;

/** Validation and classification shared with JVM tests; never executes user shell text. */
public final class NetworkProfile {
    public String mode="auto",hostname="",address="",prefix="24",gateway="",dns1="",dns2="";
    public static long ipv4(String value) {
        String[] parts=value.split("\\.",-1);if(parts.length!=4)throw new IllegalArgumentException("IPv4を4つの数字で入力してください");long result=0;
        for(String part:parts){if(!part.matches("0|[1-9][0-9]{0,2}"))throw new IllegalArgumentException("IPv4の形式が不正です");int n=Integer.parseInt(part);if(n>255)throw new IllegalArgumentException("IPv4は0～255です");result=(result<<8)|n;}return result;
    }
    public static void unicast(String value) {long n=ipv4(value);int first=(int)(n>>>24);if(first==0||first==127||first>=224||n==0xffffffffL)throw new IllegalArgumentException("通常の端末用IPv4アドレスを指定してください");}
    public void validate() {
        if(!Arrays.asList("auto","router","direct","static").contains(mode))throw new IllegalArgumentException("接続モードが不正です");
        hostname=hostname.trim().toLowerCase(Locale.ROOT);if(hostname.endsWith(".local"))hostname=hostname.substring(0,hostname.length()-6);
        if(!hostname.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))throw new IllegalArgumentException("接続名は英小文字・数字・ハイフンの1～63文字です。先頭・末尾にハイフンは使えません");
        if(!mode.equals("static"))return;
        unicast(address);if(!prefix.matches("[0-9]{1,2}"))throw new IllegalArgumentException("プレフィックス長は1～30です");int bits=Integer.parseInt(prefix);if(bits<1||bits>30)throw new IllegalArgumentException("プレフィックス長は1～30です");
        long mask=(0xffffffffL<<(32-bits))&0xffffffffL,ip=ipv4(address),host=ip&~mask;if(host==0||host==(~mask&0xffffffffL))throw new IllegalArgumentException("ネットワークアドレス・ブロードキャストアドレスは指定できません");
        if(!gateway.isEmpty()){unicast(gateway);long gw=ipv4(gateway);if((gw&mask)!=(ip&mask)||gw==ip||(gw&~mask)==0||(gw&~mask)==(~mask&0xffffffffL))throw new IllegalArgumentException("ゲートウェイは同じサブネットの別の端末アドレスを指定してください");if(dns1.isEmpty()&&dns2.isEmpty())throw new IllegalArgumentException("ゲートウェイを使う場合はDNSも指定してください");}
        if(!dns1.isEmpty())unicast(dns1);if(!dns2.isEmpty())unicast(dns2);
    }
    public static String classify(boolean link,boolean gateway,List<String> addresses) {
        if(!link)return "有線ケーブル未接続";
        boolean routable=false,local=false;for(String address:addresses){if(address.startsWith("169.254."))local=true;else if(!address.startsWith("127."))routable=true;}
        if(gateway&&routable)return "ルーター／PC共有接続（推定）";
        if(routable)return "有線IPあり・経路未確認（直結固定IP等の可能性）";
        if(local)return "PC直結、またはDHCP未取得（推定）";
        return "有線接続あり・IP取得待ち";
    }
}
