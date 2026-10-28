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
        for (String name : new String[]{"runtime.tar.gz", "runtime.sha256", "patch-runtime.cjs", "setup.sh", "control.sh", "install.sh", "restore.sh", "90-tsd-inject.sh", "91-tcl-pvr.sh", "99-tcl-mirakurun.sh", "tcl-pvr-net.sh", "libtsd.so", "tsctl", "tcl-tuner", "server.yml", "tuners.yml"}) {
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
        String rows = Root.capture("content query --uri content://android.media.tv/channel --projection original_network_id:transport_stream_id:service_id:type:service_type:browsable:display_name");
        Pattern pattern = Pattern.compile("original_network_id=(\\d+), transport_stream_id=(\\d+), service_id=(\\d+), type=TYPE_ISDB_T, service_type=(?:EN_TCL_DTV|SERVICE_TYPE_AUDIO_VIDEO), browsable=1, display_name=(.+)");
        JSONArray channels = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (String row : rows.split("\\r?\\n")) {
            Matcher match = pattern.matcher(row);
            if (!match.find()) continue;
            String key = match.group(1) + "_" + match.group(2);
            if (!seen.add(key)) continue;
            for (int i=1;i<=3;i++) {
                int id=Integer.parseInt(match.group(i));
                if (id<1 || id>65535) throw new IOException("invalid channel IDs");
            }
            JSONObject channel = new JSONObject();
            channel.put("name", match.group(4)); channel.put("type", "GR");
            channel.put("channel", key + "_" + match.group(3));
            channel.put("isDisabled", false);
            channels.put(channel);
        }
        if (channels.length()==0) throw new IOException("TV本体で先に地デジのチャンネルスキャンを行ってください");
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

    static String command(String action) throws Exception {
        if (!Arrays.asList("start","stop","status").contains(action)) throw new IllegalArgumentException();
        return Root.capture("sh " + BASE + "/control.sh " + action);
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
