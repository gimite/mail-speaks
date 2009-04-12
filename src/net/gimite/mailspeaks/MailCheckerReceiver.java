package net.gimite.mailspeaks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MailCheckerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("MailSpeaks", "boot");
        MailChecker checker = new MailChecker(context);
        if (checker.isEnabled()) {
            checker.startService();
        }
        checker.destroy();
    }

}
