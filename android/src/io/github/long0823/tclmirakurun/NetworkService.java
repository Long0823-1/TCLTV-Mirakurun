package io.github.long0823.tclmirakurun;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.wifi.WifiManager;

public final class NetworkService extends Service {
    static final String PREFS="network-service";
    private PowerManager.WakeLock cpu;
    private WifiManager.WifiLock wifi;
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("mirakurun","Mirakurun配信",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(44,new Notification.Builder(this,"mirakurun").setSmallIcon(R.drawable.icon).setContentTitle("TCL TV Mirakurun").setContentText("画面消灯中も配信を維持します").setContentIntent(open).setOngoing(true).build());
        cpu=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"TCLMirakurun:cpu");cpu.setReferenceCounted(false);cpu.acquire();
        WifiManager wifiManager=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        if(wifiManager!=null){wifi=wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"TCLMirakurun:wifi");wifi.setReferenceCounted(false);wifi.acquire();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!getSharedPreferences(PREFS,0).getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){if(wifi!=null&&wifi.isHeld())wifi.release();if(cpu!=null&&cpu.isHeld())cpu.release();stopForeground(true);super.onDestroy();}
}
