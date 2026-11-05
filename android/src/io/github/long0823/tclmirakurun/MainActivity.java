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
    private TextView networkAddresses;
    private final Handler networkRefresh=new Handler(Looper.getMainLooper());
    private final Runnable refreshNetwork=new Runnable(){public void run(){if(networkAddresses!=null)networkAddresses.setText(NetworkSettings.describe(MainActivity.this));networkRefresh.postDelayed(this,5000);}};
    private volatile boolean rebootScheduled;
    private final List<Button> buttons=new ArrayList<>();
    interface Operation { String run() throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(48,24,48,24);
        scroll.addView(body); setContentView(scroll);
        TextView title=new TextView(this); title.setText("TCL TV Mirakurun 0.3.0-pre.3 / CATV"); title.setTextSize(28); body.addView(title);
        TextView description=new TextView(this); description.setText("対応TCL G06・Android 11・Magisk向け\n地デジ・BS/110度CS・CATV（J:COMなど、ISDB-C）/ 2チューナー / ポート40772\nCATVは同軸をTVの「地デジ端子」へ接続してください（BS/CS端子は非対応）。\nCATVの有料放送は契約とB-CASの条件で、映像が出ない局があります。\nMirakurun本体のバージョンは「本体の更新」で確認できます。\n衛星の実受信は未検証のプレリリースです。\n初回はMagiskでrootを許可してください。導入中は電源を切らないでください。"); description.setTextSize(17); body.addView(description);
        TextView addresses=new TextView(this);networkAddresses=addresses;addresses.setText(NetworkSettings.describe(this)); addresses.setTextSize(18); addresses.setTextColor(Color.rgb(100,210,255)); body.addView(addresses);
        Button network=new Button(this);network.setText("ネットワーク設定 / .local名・固定IP");buttons.add(network);body.addView(network);network.setOnClickListener(v->NetworkSettings.show(this));networkRefresh.postDelayed(refreshNetwork,5000);
        Button host=new Button(this);host.setText(".localの接続名だけ変更");buttons.add(host);body.addView(host);host.setOnClickListener(v->NetworkSettings.showName(this));
        add(body,"1. セットアップ / 局更新（完了後に自動再起動）",()-> {
            String result=Installer.configure(this);
            TclPolicy.configure(this,true);
            getSharedPreferences(NetworkService.PREFS,0).edit().putBoolean("enabled",true).commit();
            startForegroundService(new Intent(this,NetworkService.class));
            Root.capture("touch "+Installer.BASE+"/enabled");
            Root.capture("nohup /system/bin/sh -c '/system/bin/sleep 5; /system/bin/sync; /system/bin/reboot' </dev/null >/dev/null 2>&1 &");
            rebootScheduled=true;
            return result+"\n5秒後にTVを自動再起動します。再起動後はMirakurunを自動起動します。";
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
        add(body,"3. CATVをスキャン・局更新（Mirakurunを自動で停止し、完了後に再開）",()->Installer.scanCatv(this,text->runOnUiThread(()->{if(!isFinishing())output.setText(text);})));
        Button satellite=new Button(this);buttons.add(satellite);body.addView(satellite);
        Runnable refreshSatellite=()->satellite.setText("衛星（BS/CS）の局もCATVと併用する： "+(Installer.satelliteWithCatv(this)?"する":"しない（初期値）"));
        refreshSatellite.run();
        satellite.setOnClickListener(v->{
            Installer.setSatelliteWithCatv(this,!Installer.satelliteWithCatv(this));refreshSatellite.run();
            execute(satellite,"局一覧を更新しています…（Mirakurunを停止して数十秒で再開します）",()->Installer.refreshChannels(this)+"\n「しない」では、TV本体の衛星スキャンの局（アンテナがないと受信できず、CATV経由のBSと局番号が重なります）を除きます。実際のBS/CSアンテナも使う場合は「する」にしてください。");
        });
        add(body,"局一覧だけ更新（スキャンなし）",()->Installer.refreshChannels(this));
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
        Button coreUpdate=new Button(this);coreUpdate.setText("Mirakurun本体の更新を確認");buttons.add(coreUpdate);body.addView(coreUpdate);
        coreUpdate.setOnClickListener(v->execute(coreUpdate,"Mirakurun本体と公式npmの更新情報を確認しています…",()->{
            CoreUpdate.Info info=CoreUpdate.check(this);
            runOnUiThread(()->{
                if(isFinishing())return;
                String note=!info.online?"更新情報を取得できません。インターネット接続を確認してください。前の本体がある場合はオフラインで戻せます。":info.newer()?(info.supported()?"新しい本体があります。TVのメモリーを確保するため、更新中は配信を停止します。視聴・録画中は適用しません。\n設定・番組DBをバックアップし、起動確認に失敗した場合は元へ戻します。更新中はTVの電源を切らないでください。":"このメジャーバージョンは未対応です。アプリの対応版をお待ちください。") : "本体は最新です。";
                AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Mirakurun本体の更新").setMessage(info.summary()+"\n\n"+note).setNegativeButton("閉じる",null);
                if(info.newer()&&info.supported())dialog.setPositiveButton("本体を更新する",(d,w)->execute(coreUpdate,"Mirakurun本体を更新しています。更新中は配信を停止します。数分かかることがあります。TVの電源を切らないでください。",()->CoreUpdate.install(this,info)));
                if(info.previous!=null)dialog.setNeutralButton("前の本体に戻す",(d,w)->new AlertDialog.Builder(this).setTitle("Mirakurun "+info.previous+" に戻しますか？").setMessage("本体と、前の本体に対応する設定・番組DBの保存状態へ戻します。視聴・録画中は実行できません。").setNegativeButton("キャンセル",null).setPositiveButton("戻す",(dd,ww)->execute(coreUpdate,"前のMirakurun本体と設定・番組DBへ戻しています…",()->CoreUpdate.rollback(this))).show());
                dialog.show();
            });
            return info.summary();
        }));
        add(body,"Mirakurun本体・依存パッケージのライセンス",()->CoreUpdate.licenses());
        add(body,"本体更新のログを表示",()->Root.capture("tail -n 100 "+Installer.BASE+"/core-update.log"));
        Button remove=new Button(this); remove.setText("環境を削除"); buttons.add(remove); body.addView(remove);
        remove.setOnClickListener(view->new AlertDialog.Builder(this)
            .setTitle("Mirakurun環境を削除しますか？")
            .setMessage("Mirakurun本体・局設定・番組DB・ログ・自動起動を削除します。既存のBonDriver環境は保持します。このアプリが導入したチューナー設定は復元します。アプリ自体は残ります。")
            .setNegativeButton("キャンセル",null)
            .setPositiveButton("環境を削除",(dialog,which)->execute(remove,()-> {
                Root.capture("rm -f "+Installer.BASE+"/enabled");
                getSharedPreferences(NetworkService.PREFS,0).edit().putBoolean("enabled",false).commit();
                stopService(new Intent(this,NetworkService.class));
                NetworkSettings.root(this,"restore-original");
                TclPolicy.configure(this,false);
                return Installer.remove(this);
            })).show());
        output=new TextView(this); output.setTextSize(16); output.setText("セットアップ完了後はTVを自動再起動し、Mirakurunを起動します。\nCATVを使う場合は、起動後に「3. CATVをスキャン・局更新」を実行してください（約5〜10分、進行状況を表示します）。\n既存のBonDriver・IPTVとの同じチューナーの同時利用は避けてください。"); body.addView(output);
        buttons.get(0).requestFocus();
    }

    private void add(LinearLayout body,String label,Operation op) {
        Button button=new Button(this); button.setText(label); buttons.add(button); body.addView(button);
        button.setOnClickListener(view->execute(button,op));
    }

    private void execute(Button selected,Operation op) {
        execute(selected,"処理中です。初回セットアップは数分かかる場合があります。",op);
    }

    private void execute(Button selected,String message,Operation op) {
            for(Button item:buttons)item.setEnabled(false);
            output.setText(message);
            worker.execute(()-> {
                String text;
                try { text=op.run(); } catch(Exception e) { text="処理に失敗しました\n"+e.getMessage(); }
                final String result=text;
                runOnUiThread(()-> { if(isFinishing())return; output.setText(result); for(Button item:buttons)item.setEnabled(!rebootScheduled); if(!rebootScheduled)selected.requestFocus(); });
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
    void networkOperation(Operation operation){execute(buttons.get(0),operation);}
    @Override public void onDestroy(){networkRefresh.removeCallbacks(refreshNetwork);worker.shutdown();super.onDestroy();}
}
