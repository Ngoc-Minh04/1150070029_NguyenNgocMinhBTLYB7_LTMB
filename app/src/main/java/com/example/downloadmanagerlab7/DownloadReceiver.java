package com.example.downloadmanagerlab7;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DownloadReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d("DownloadReceiver", "Nhận action: " + action);

        Intent serviceIntent = new Intent(context, DownloadService.class);
        serviceIntent.setAction(action);
        context.startService(serviceIntent);
    }
}
