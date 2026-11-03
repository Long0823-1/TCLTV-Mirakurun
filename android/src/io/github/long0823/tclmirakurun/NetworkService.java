package io.github.long0823.tclmirakurun;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.wifi.WifiManager;

public final class NetworkService extends Service {
    static final String PREFS="network-service";
    private PowerManager.WakeLock cpu;
    private WifiManager.WifiLock wifi;
    private WifiManager.MulticastLock multicast;
    private final java.util.concurrent.ScheduledExecutorService monitor=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private final MdnsPublisher mdns=new MdnsPublisher();
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("mirakurun","Mirakurun配信",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(44,new Notification.Builder(this,"mirakurun").setSmallIcon(R.drawable.icon).setContentTitle("TCL TV Mirakurun").setContentText("画面消灯中も配信を維持します").setContentIntent(open).setOngoing(true).build());
        cpu=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"TCLMirakurun:cpu");cpu.setReferenceCounted(false);cpu.acquire();
        WifiManager wifiManager=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        if(wifiManager!=null){wifi=wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"TCLMirakurun:wifi");wifi.setReferenceCounted(false);wifi.acquire();multicast=wifiManager.createMulticastLock("TCLMirakurun:mdns");multicast.setReferenceCounted(false);multicast.acquire();}
        monitor.execute(()->{try{NetworkSettings.syncWatcher(this);}catch(Exception e){getSharedPreferences(NetworkSettings.PREFS,0).edit().putString("mdns-status","有線維持の更新に失敗："+e.getMessage()).apply();}});
        monitor.scheduleWithFixedDelay(()->{if(!getSharedPreferences(PREFS,0).getBoolean("enabled",false))return;try{NetworkSettings.refreshRootState(this);}catch(Exception ignored){}try{mdns.refresh(this);}catch(Exception e){getSharedPreferences(NetworkSettings.PREFS,0).edit().putString("mdns-status","mDNS待機／エラー："+e.getMessage()).apply();}},1,5,java.util.concurrent.TimeUnit.SECONDS);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!getSharedPreferences(PREFS,0).getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){monitor.shutdownNow();new Thread(()->mdns.close(),"mdns-close").start();if(multicast!=null&&multicast.isHeld())multicast.release();if(wifi!=null&&wifi.isHeld())wifi.release();if(cpu!=null&&cpu.isHeld())cpu.release();getSharedPreferences(NetworkSettings.PREFS,0).edit().putString("mdns-status","mDNS停止中").apply();stopForeground(true);super.onDestroy();}
}
