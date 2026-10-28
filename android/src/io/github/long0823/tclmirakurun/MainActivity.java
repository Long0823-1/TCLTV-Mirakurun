package io.github.long0823.tclmirakurun;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.widget.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView output;
    private final List<Button> buttons=new ArrayList<>();
    interface Operation { String run() throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(48,24,48,24);
        scroll.addView(body); setContentView(scroll);
        TextView title=new TextView(this); title.setText("TCL TV Mirakurun 0.1.0"); title.setTextSize(28); body.addView(title);
        TextView description=new TextView(this); description.setText("対応TCL G06・Android 11・Magisk向け\n地デジ2チューナー / Mirakurun 4.1.5 / ポート40772\n初回はMagiskでrootを許可してください。導入中は電源を切らないでください。"); description.setTextSize(17); body.addView(description);
        TextView addresses=new TextView(this); addresses.setText(urls()); addresses.setTextSize(20); addresses.setTextColor(Color.rgb(100,210,255)); body.addView(addresses);
        add(body,"1. セットアップ / 局一覧を更新",()-> {
            String result=Installer.configure(this);
            return result+"\n次に「2. 起動」を選んでください。初回のチューナー導入後はTVの再起動が必要です。";
        });
        add(body,"2. 起動・自動起動を有効化",()-> {
            String result=Installer.command("start");
            TclPolicy.configure(this,true);
            Root.capture("touch "+Installer.BASE+"/enabled");
            getSharedPreferences(NetworkService.PREFS,0).edit().putBoolean("enabled",true).commit();
            startForegroundService(new Intent(this,NetworkService.class));
            Installer.waitReady();
            return result+"\n"+urls();
        });
        add(body,"状態を確認",()->Installer.command("status"));
        add(body,"停止・自動起動を無効化",()-> {
            Root.capture("rm -f "+Installer.BASE+"/enabled");
            String result=Installer.command("stop");
            getSharedPreferences(NetworkService.PREFS,0).edit().putBoolean("enabled",false).commit();
            stopService(new Intent(this,NetworkService.class));
            TclPolicy.configure(this,false);
            return result;
        });
        add(body,"ログを表示",()->Root.capture("tail -n 60 "+Installer.BASE+"/server.log"));
        output=new TextView(this); output.setTextSize(16); output.setText("セットアップ → 起動 の順に操作してください。\n既存のBonDriver・IPTVとの同じチューナーの同時利用は避けてください。"); body.addView(output);
        buttons.get(0).requestFocus();
    }

    private void add(LinearLayout body,String label,Operation op) {
        Button button=new Button(this); button.setText(label); buttons.add(button); body.addView(button);
        button.setOnClickListener(view->{
            for(Button item:buttons)item.setEnabled(false);
            output.setText("処理中です。初回セットアップは数分かかる場合があります。");
            worker.execute(()-> {
                String text;
                try { text=op.run(); } catch(Exception e) { text="処理に失敗しました\n"+e.getMessage(); }
                final String result=text;
                runOnUiThread(()-> { if(isFinishing())return; output.setText(result); for(Button item:buttons)item.setEnabled(true); });
            });
        });
    }

    private String urls() {
        StringBuilder result=new StringBuilder("接続先（同じLAN内のブラウザーから）\n");
        try {
            for(NetworkInterface iface:Collections.list(NetworkInterface.getNetworkInterfaces()))
                for(InetAddress ip:Collections.list(iface.getInetAddresses()))
                    if(ip instanceof Inet4Address && !ip.isLoopbackAddress()) result.append("http://").append(ip.getHostAddress()).append(":40772/\n");
        }catch(Exception ignored){}
        return result.toString().trim();
    }
    @Override public void onDestroy(){worker.shutdown();super.onDestroy();}
}
