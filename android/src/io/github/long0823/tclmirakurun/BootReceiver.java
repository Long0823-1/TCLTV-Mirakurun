package io.github.long0823.tclmirakurun;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(context.getSharedPreferences(NetworkService.PREFS,0).getBoolean("enabled",false))context.startForegroundService(new Intent(context,NetworkService.class));
    }
}
