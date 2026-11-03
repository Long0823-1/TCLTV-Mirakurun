package io.github.long0823.tclmirakurun;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;

final class NetworkSettings {
    static final String PREFS="network-options";
    static String deviceId(Context c){android.content.SharedPreferences p=c.getSharedPreferences(PREFS,0);String id=p.getString("device-id","");if(id.isEmpty()){id=UUID.randomUUID().toString().replace("-","");p.edit().putString("device-id",id).commit();}return id;}
    static void refreshRootState(Context c)throws Exception {
        try(BufferedReader reader=new BufferedReader(new FileReader(new File(c.getFilesDir(),"network-state")))){
            String carrier=reader.readLine(),gateway=reader.readLine(),updated=reader.readLine();
            if((!"0".equals(carrier)&&!"1".equals(carrier))||updated==null)return;
            long timestamp=Long.parseLong(updated)*1000L;
            if(Math.abs(System.currentTimeMillis()-timestamp)>15000)return;
            c.getSharedPreferences(PREFS,0).edit().putString("wired-carrier",carrier).putBoolean("wired-gateway","1".equals(gateway)).putLong("wired-updated",timestamp).apply();
        }
    }
    static NetworkProfile load(Context c){android.content.SharedPreferences p=c.getSharedPreferences(PREFS,0);NetworkProfile n=new NetworkProfile();String hostname=p.getString("hostname","");if(hostname.isEmpty()){hostname="tcltv-mirakurun-"+UUID.randomUUID().toString().substring(0,6);p.edit().putString("hostname",hostname).commit();}n.hostname=hostname;n.mode=p.getString("mode","auto");n.address=p.getString("address","");n.prefix=p.getString("prefix","24");n.gateway=p.getString("gateway","");n.dns1=p.getString("dns1","");n.dns2=p.getString("dns2","");return n;}
    static void save(Context c,NetworkProfile p){c.getSharedPreferences(PREFS,0).edit().putString("hostname",p.hostname).putString("mode",p.mode).putString("address",p.address).putString("prefix",p.prefix).putString("gateway",p.gateway).putString("dns1",p.dns1).putString("dns2",p.dns2).commit();}
    static boolean allowMdnsHosts(Context c,java.util.List<String> names)throws Exception {
        org.json.JSONArray hosts=new org.json.JSONArray();
        for(String name:names){NetworkProfile check=new NetworkProfile();check.hostname=name;check.validate();hosts.put(check.hostname+".local");}
        File temporary=new File(c.getFilesDir(),"mdns-hosts-request.json.new");
        try(FileOutputStream out=new FileOutputStream(temporary)){out.write(hosts.toString().getBytes("UTF-8"));out.getFD().sync();}
        if(!temporary.renameTo(new File(c.getFilesDir(),"mdns-hosts-request.json")))throw new IOException("Cannot publish mDNS hosts");
        return true;
    }
    static String root(Context c,String... args)throws Exception {StringBuilder command=new StringBuilder("CLASSPATH=").append(Root.quote(c.getApplicationInfo().sourceDir)).append(" /system/bin/app_process /system/bin io.github.long0823.tclmirakurun.RootNetworkMain");for(String arg:args)command.append(' ').append(Root.quote(arg));return Root.capture(command.toString());}
    static synchronized void syncWatcher(Context c)throws Exception {
        new File(c.getFilesDir(),"network-state").createNewFile();
        File stage=new File(c.getFilesDir(),"network");stage.mkdirs();File script=new File(stage,"network-watch.sh");try(InputStream in=c.getAssets().open("network-watch.sh");OutputStream out=new FileOutputStream(script)){byte[] b=new byte[4096];int count;while((count=in.read(b))>0)out.write(b,0,count);}
        File hook=new File(stage,"autoip-hook.sh");try(InputStream in=c.getAssets().open("autoip-hook.sh");OutputStream out=new FileOutputStream(hook)){byte[] b=new byte[4096];int count;while((count=in.read(b))>0)out.write(b,0,count);}
        File migration=new File(stage,"migrate-legacy-network.sh");try(InputStream in=c.getAssets().open("migrate-legacy-network.sh");OutputStream out=new FileOutputStream(migration)){byte[] b=new byte[4096];int count;while((count=in.read(b))>0)out.write(b,0,count);}
        File helper=new File(stage,"network-helper.jar");try(InputStream in=c.getAssets().open("network-helper.jar");OutputStream out=new FileOutputStream(helper)){byte[] b=new byte[4096];int count;while((count=in.read(b))>0)out.write(b,0,count);}
        Root.capture("mkdir -p "+Installer.BASE+" && chmod 700 "+Installer.BASE+" && cp "+Root.quote(helper.getAbsolutePath())+" "+Installer.BASE+"/network-helper.jar.new && chmod 400 "+Installer.BASE+"/network-helper.jar.new && mv -f "+Installer.BASE+"/network-helper.jar.new "+Installer.BASE+"/network-helper.jar");
        root(c,"recover-pending");
        Root.capture("/system/bin/sh "+Root.quote(migration.getAbsolutePath()));
        Root.capture("if [ -d "+Installer.BASE+"/rootfs ]; then mkdir -p "+Installer.BASE+"/rootfs/run/tcl && cp "+Root.quote(hook.getAbsolutePath())+" "+Installer.BASE+"/rootfs/run/tcl/autoip-hook.sh && chmod 700 "+Installer.BASE+"/rootfs/run/tcl/autoip-hook.sh; fi");
        Root.capture("if [ -f "+Installer.BASE+"/control.sh ]; then p=$(cat "+Installer.BASE+"/network.pid 2>/dev/null); case \"$p\" in ''|*[!0-9]*) ;; *) if grep -q '/data/local/tcl-mirakurun/network-watch.sh' /proc/$p/cmdline 2>/dev/null; then kill \"$p\" 2>/dev/null || true; for n in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do kill -0 \"$p\" 2>/dev/null || break; sleep 0.1; done; fi;; esac; cp "+Root.quote(script.getAbsolutePath())+" "+Installer.BASE+"/network-watch.sh.new && chmod 700 "+Installer.BASE+"/network-watch.sh.new && mv -f "+Installer.BASE+"/network-watch.sh.new "+Installer.BASE+"/network-watch.sh; if [ -f "+Installer.BASE+"/enabled ]; then nohup /system/bin/sh "+Installer.BASE+"/network-watch.sh </dev/null >>"+Installer.BASE+"/network.log 2>&1 & fi; fi");
    }
    static String describe(Context c) {
        StringBuilder text=new StringBuilder();List<String> wired=new ArrayList<>();boolean wiredUp=false,gateway=false;
        try {ConnectivityManager manager=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);for(Network network:manager.getAllNetworks()){NetworkCapabilities caps=manager.getNetworkCapabilities(network);LinkProperties lp=manager.getLinkProperties(network);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)&&lp!=null)for(RouteInfo r:lp.getRoutes())if(r.isDefaultRoute()&&r.getGateway() instanceof Inet4Address)gateway=true;}}
        catch(Exception ignored){}
        try {for(NetworkInterface iface:Collections.list(NetworkInterface.getNetworkInterfaces())){if(iface.getName().equals("eth0")){wiredUp=iface.isUp();for(InetAddress address:Collections.list(iface.getInetAddresses()))if(address instanceof Inet4Address)wired.add(address.getHostAddress());}for(InetAddress address:Collections.list(iface.getInetAddresses()))if(address instanceof Inet4Address&&!address.isLoopbackAddress())text.append(iface.getName()).append(" : http://").append(address.getHostAddress()).append(":40772/\n");}}
        catch(Exception ignored){}
        try(BufferedReader reader=new BufferedReader(new FileReader("/sys/class/net/eth0/carrier"))){wiredUp=reader.readLine().trim().equals("1");}catch(Exception ignored){}
        android.content.SharedPreferences snapshot=c.getSharedPreferences(PREFS,0);if(System.currentTimeMillis()-snapshot.getLong("wired-updated",0)<15000){String carrier=snapshot.getString("wired-carrier","unknown");if(!carrier.equals("unknown"))wiredUp=carrier.equals("1");gateway=snapshot.getBoolean("wired-gateway",gateway);}
        String host=c.getSharedPreferences(PREFS,0).getString("mdns-status","");NetworkProfile p=load(c);
        return "有線："+NetworkProfile.classify(wiredUp,gateway,wired)+"\nモード："+p.mode+"\n"+text+"希望の接続名（起動中に広告）：http://"+p.hostname+".local:40772/\n"+host;
    }
    static EditText field(Context c,LinearLayout body,String label,String value){TextView title=new TextView(c);title.setText(label);body.addView(title);EditText input=new EditText(c);input.setSingleLine(true);input.setText(value);body.addView(input);return input;}
    static void showName(MainActivity activity){EditText name=new EditText(activity);name.setSingleLine(true);name.setText(load(activity).hostname);AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(".localの接続名").setView(name).setNegativeButton("戻る",null).setPositiveButton("接続名を保存",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{NetworkProfile p=new NetworkProfile();p.hostname=name.getText().toString();try{p.validate();activity.getSharedPreferences(PREFS,0).edit().putString("hostname",p.hostname).commit();dialog.dismiss();activity.networkOperation(()->"接続名を保存しました。配信中なら数秒後に再広告します。\n"+describe(activity));}catch(Exception e){name.setError(e.getMessage());}}));dialog.show();}
    static void show(MainActivity activity) {
        NetworkProfile old=load(activity);LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(32,12,32,12);ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        Spinner modes=new Spinner(activity);modes.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"自動（DHCP／Windows直結）","ルーター優先（DHCP）","直結対応（自動リンクローカル）","固定IP"}));String[] values={"auto","router","direct","static"};modes.setSelection(Arrays.asList(values).indexOf(old.mode));body.addView(modes);
        EditText hostname=field(activity,body,"接続名（.localを除く）",old.hostname),ip=field(activity,body,"固定IPv4（固定IPモードのみ）",old.address),prefix=field(activity,body,"プレフィックス長（例：24＝255.255.255.0）",old.prefix),gateway=field(activity,body,"ゲートウェイ（直結固定IPなら空欄）",old.gateway),dns1=field(activity,body,"DNS 1（ゲートウェイを使う場合は必要）",old.dns1),dns2=field(activity,body,"DNS 2（任意）",old.dns2);
        TextView help=new TextView(activity);help.setText("自動はDHCPで取得します。有線接続後30秒以上取得できない場合は、重複を確認して169.254.x.x/16を自動選択します。Windows側もIP自動取得で直結できます。直結かDHCP未取得かは確定できないため推定表示です。固定IPはルーターの割当範囲と重複しない値を指定してください。変更は仮適用し、60秒以内にTV画面で確定しない場合は元へ戻します。PC側のIP設定は変更しません。");body.addView(help);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("ネットワーク設定").setView(scroll).setNegativeButton("戻る",null).setPositiveButton("仮適用",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            NetworkProfile p=new NetworkProfile();p.mode=values[modes.getSelectedItemPosition()];p.hostname=hostname.getText().toString().trim();p.address=ip.getText().toString().trim();p.prefix=prefix.getText().toString().trim();p.gateway=gateway.getText().toString().trim();p.dns1=dns1.getText().toString().trim();p.dns2=dns2.getText().toString().trim();
            try{p.validate();}catch(Exception e){new AlertDialog.Builder(activity).setMessage(e.getMessage()).setPositiveButton("閉じる",null).show();return;}dialog.dismiss();activity.networkOperation(()->{syncWatcher(activity);String token=UUID.randomUUID().toString().replace("-","");String result=root(activity,"apply",p.mode,p.hostname,p.address,p.prefix,p.gateway,p.dns1,p.dns2,token);activity.runOnUiThread(()->confirm(activity,p,token));return result;});
        }));dialog.show();
    }
    static void confirm(MainActivity activity,NetworkProfile p,String token) {
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("この設定を使いますか？").setMessage("TV画面に表示されたIPでPCから接続を確認してください。確認しない場合、60秒以内に変更前の設定へ戻します。\n"+describe(activity)).setCancelable(false).setNegativeButton("元に戻す",(d,w)->activity.networkOperation(()->root(activity,"rollback",token))).setPositiveButton("この設定を確定",(d,w)->activity.networkOperation(()->{String result=root(activity,"confirm",token);save(activity,p);return result+"\n"+describe(activity);})).create();
        dialog.show();new CountDownTimer(60000,1000){public void onTick(long left){if(dialog.isShowing())dialog.setTitle("この設定を使いますか？ 残り"+(left/1000)+"秒");}public void onFinish(){if(dialog.isShowing()){dialog.dismiss();activity.networkOperation(()->root(activity,"rollback",token));}}}.start();
    }
}
