package io.github.long0823.tclmirakurun;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

final class Installer {
    static final String BASE = "/data/local/tcl-mirakurun";

    static String configure(Context context) throws Exception {
        File stage = new File(context.getFilesDir(), "setup");
        if (!stage.isDirectory() && !stage.mkdirs()) throw new IOException("staging failed");
        for (String name : new String[]{"runtime.tar.gz", "runtime.sha256", "patch-runtime.cjs", "setup.sh", "control.sh", "network-watch.sh", "autoip-hook.sh", "migrate-legacy-network.sh", "install.sh", "restore.sh", "90-tsd-inject.sh", "91-tcl-pvr.sh", "99-tcl-mirakurun.sh", "tcl-pvr-net.sh", "libtsd.so", "tsctl", "tcl-tuner", "server.yml", "tuners.yml"}) {
            try (InputStream input = context.getAssets().open(name);
                 OutputStream out = new FileOutputStream(new File(stage, name))) {
                byte[] bytes = new byte[65536]; int count;
                while ((count = input.read(bytes)) != -1) out.write(bytes, 0, count);
            }
        }
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(new File(stage, "runtime.tar.gz"))) {
            byte[] bytes = new byte[65536]; int count;
            while ((count = input.read(bytes)) != -1) hash.update(bytes, 0, count);
        }
        StringBuilder actual = new StringBuilder();
        for (byte b : hash.digest()) actual.append(String.format(Locale.ROOT,"%02x", b & 255));
        String expected;
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(stage,"runtime.sha256")))) {
            expected = reader.readLine().trim().split("\\s+")[0];
        }
        if (!actual.toString().equals(expected)) throw new IOException("runtime SHA-256 mismatch");
        String rows = CatvCatalog.rows(CatvCatalog.read(Root::capture))+Root.capture("content query --uri content://android.media.tv/channel --projection original_network_id:transport_stream_id:service_id:type:service_type:browsable:display_name");
        JSONArray channels = new JSONArray();
        for (ChannelCatalog.Channel scanned : ChannelCatalog.parse(rows,includeSatellite(context,rows))) {
            JSONObject channel = new JSONObject();
            channel.put("name", scanned.name);channel.put("type", scanned.type);
            channel.put("channel", scanned.channel);channel.put("isDisabled", false);
            channels.put(channel);
        }
        write(new File(stage,"channels.yml"), channels.toString(2));
        byte[] random=new byte[32]; new SecureRandom().nextBytes(random);
        StringBuilder token=new StringBuilder();
        for(byte b:random) token.append(String.format(Locale.ROOT,"%02x",b&255));
        write(new File(stage,"token"), token.toString()+"\n");
        return Root.capture("sh " + Root.quote(new File(stage,"setup.sh").getAbsolutePath()) + " " + Root.quote(stage.getAbsolutePath()));
    }

    private static void write(File path, String text) throws Exception {
        try(OutputStream out=new FileOutputStream(path)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
    }

    /** Stops Mirakurun, scans the cable, updates the Mirakurun tuner/channel files and starts it again. */
    static final String PREFS="catalog";
    /** Satellite BS/CS entries from the TV database are kept only when there is no CATV list, or when the user asked for both. */
    static boolean includeSatellite(Context context,String rows){
        boolean hasCatv=rows.contains("type=TYPE_ISDB_C");
        return !hasCatv||context.getSharedPreferences(PREFS,0).getBoolean("satellite_with_catv",false);
    }
    static boolean satelliteWithCatv(Context context){return context.getSharedPreferences(PREFS,0).getBoolean("satellite_with_catv",false);}
    static void setSatelliteWithCatv(Context context,boolean value){context.getSharedPreferences(PREFS,0).edit().putBoolean("satellite_with_catv",value).commit();}
    static String scanCatv(Context context,java.util.function.Consumer<String> status)throws Exception {
        boolean restart=Root.capture("if [ -f "+BASE+"/enabled ]; then echo yes; fi").trim().equals("yes");
        status.accept("【1/4】Mirakurunを停止しています…\nCATVのスキャンはチューナー1を専有するため、先に配信を止めます。");
        command("stop");
        // Wait until Mirakurun and its tuner bridge are really gone.
        for(int attempt=0;attempt<20&&!Root.capture("for p in /proc/[0-9]*; do if [ -r \"$p/root/opt/mirakurun/node_modules/mirakurun/package.json\" ]; then echo busy; break; fi; done").trim().isEmpty();attempt++)Thread.sleep(500);
        try {
            status.accept("【2/4】CATVをスキャンしています…\n開始を待っています（TV側の選局処理が終わるまで最大4分）。");
            java.util.concurrent.atomic.AtomicBoolean scanning=new java.util.concurrent.atomic.AtomicBoolean(true);
            long started=System.currentTimeMillis();
            Thread poller=new Thread(()->{
                while(scanning.get()){
                    try{
                        String[] p=Root.capture("cat /data/local/tmp/tsd_catv_progress 2>/dev/null").trim().split("\\s+");
                        if(p.length==4){
                            int done=Integer.parseInt(p[0]),total=Integer.parseInt(p[1]);long seconds=(System.currentTimeMillis()-started)/1000;
                            int percent=total>0?done*100/total:0;
                            String eta=done>3?"\n残り約"+Math.max(1,(int)Math.ceil((seconds*(total-done)/(double)done)/60))+"分":"";
                            status.accept("【2/4】CATVをスキャンしています… "+percent+"%\n周波数 "+(Integer.parseInt(p[2])/1000.0)+" MHz（"+done+"/"+total+"）\nここまでに見つかった番組："+p[3]+"件\n経過 "+(seconds/60)+"分"+(seconds%60)+"秒"+eta+"\nTVの電源を切らないでください。");
                        }
                    }catch(Exception ignored){}
                    try{Thread.sleep(3000);}catch(InterruptedException e){return;}
                }
            });
            poller.setDaemon(true);poller.start();
            String result;
            try{result=CatvCatalog.scan(context,command->Root.capture(command,40));}finally{scanning.set(false);poller.interrupt();}
            status.accept("【3/4】Mirakurunの局一覧を更新しています…\nスキャン結果："+result.trim());
            int count=applyChannelConfig(context);
            return result+"\nMirakurunの局一覧（"+count+"チャンネル）を更新しました。\n有料放送は契約がないと映像・音声が出ませんが、局一覧には表示されます。";
        }finally{if(restart){status.accept("【4/4】Mirakurunを起動しています…");command("start");waitReady();}}
    }
    /** Writes tuners.yml and channels.yml (CATV list + the TV database) into the Mirakurun runtime. Returns the channel count. */
    static int applyChannelConfig(Context context)throws Exception {
        File tuners=new File(context.getCacheDir(),"catv-tuners.yml");
        try(InputStream input=context.getAssets().open("tuners.yml");OutputStream out=new FileOutputStream(tuners)){byte[] bytes=new byte[4096];int n;while((n=input.read(bytes))!=-1)out.write(bytes,0,n);}
        String tunersTarget=BASE+"/rootfs/usr/local/etc/mirakurun/tuners.yml";
        Root.capture("cp "+Root.quote(tuners.getAbsolutePath())+" "+tunersTarget+".new && chmod 600 "+tunersTarget+".new && mv "+tunersTarget+".new "+tunersTarget);
        String rows=CatvCatalog.rows(CatvCatalog.read(Root::capture))+Root.capture("content query --uri content://android.media.tv/channel --projection original_network_id:transport_stream_id:service_id:type:service_type:browsable:display_name");
        JSONArray channels=new JSONArray();for(ChannelCatalog.Channel scanned:ChannelCatalog.parse(rows,includeSatellite(context,rows))){JSONObject channel=new JSONObject();channel.put("name",scanned.name);channel.put("type",scanned.type);channel.put("channel",scanned.channel);channel.put("isDisabled",false);channels.put(channel);}
        File file=new File(context.getCacheDir(),"catv-mirakurun-channels.json");write(file,channels.toString(2));
        String target=BASE+"/rootfs/usr/local/etc/mirakurun/channels.yml";
        Root.capture("cp "+Root.quote(file.getAbsolutePath())+" "+target+".new && mv "+target+".new "+target);
        return channels.length();
    }
    /** Rebuilds the Mirakurun channel list from the saved CATV scan without scanning again (takes seconds). */
    static String refreshChannels(Context context)throws Exception {
        boolean restart=Root.capture("if [ -f "+BASE+"/enabled ]; then echo yes; fi").trim().equals("yes");
        command("stop");
        for(int attempt=0;attempt<20&&!Root.capture("for p in /proc/[0-9]*; do if [ -r \"$p/root/opt/mirakurun/node_modules/mirakurun/package.json\" ]; then echo busy; break; fi; done").trim().isEmpty();attempt++)Thread.sleep(500);
        try {
            int count=applyChannelConfig(context);
            return "Mirakurunの局一覧（"+count+"チャンネル）を更新しました。"+(satelliteWithCatv(context)?"\n衛星（BS/CS）の局もCATVと併用しています。":"\n衛星（BS/CS）の局は一覧に含めていません。");
        }finally{if(restart){command("start");waitReady();}}
    }
    static String command(String action) throws Exception {
        if (!Arrays.asList("start","stop","status").contains(action)) throw new IllegalArgumentException();
        return Root.capture("sh " + BASE + "/control.sh " + action);
    }

    static String remove(Context context) throws Exception {
        File stage=new File(context.getFilesDir(),"setup");
        if(!stage.isDirectory() && !stage.mkdirs()) throw new IOException("staging failed");
        for(String name:new String[]{"remove.sh","restore.sh"}) {
            try(InputStream input=context.getAssets().open(name); OutputStream out=new FileOutputStream(new File(stage,name))) {
                byte[] bytes=new byte[4096];int count;
                while((count=input.read(bytes))!=-1)out.write(bytes,0,count);
            }
        }
        String result=Root.capture("sh "+Root.quote(new File(stage,"remove.sh").getAbsolutePath())+" "+Root.quote(stage.getAbsolutePath()));
        File[] files=stage.listFiles();
        if(files!=null)for(File file:files)if(file.isFile())file.delete();
        stage.delete();
        return result;
    }

    static void waitReady() throws Exception {
        for (int attempt=0;attempt<30;attempt++) {
            HttpURLConnection connection=null;
            try {
                connection=(HttpURLConnection)new URL("http://127.0.0.1:40772/api/status").openConnection();
                connection.setConnectTimeout(1500); connection.setReadTimeout(1500);
                if(connection.getResponseCode()==200) return;
            }catch(IOException ignored){} finally { if(connection!=null)connection.disconnect(); }
            Thread.sleep(2000);
        }
        throw new IOException("APIの起動を確認できません。ログを確認してください。");
    }
}
