package net.gimite.mailspeaks;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import com.google.tts.TTS;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class MailCheckerService extends Service {

	boolean alive = true;
	private AlarmManager alarmManager;
	private PowerManager powerManager;
	private WifiManager wifiManager;
	private TTS tts;
	private MailChecker mailChecker;
	private PendingIntent checkMailPendingIntent;
	private PowerManager.WakeLock plock;
	private Thread thread;
	
	private final String ACTION_CHECK_MAIL = "net.gimite.mailspeaks.action.CHECK_MAIL";
	private WifiLock wifiLock;
	
	@Override
	public void onCreate() {
		super.onCreate();
		Log.i("MailCheckerService", "created");
        alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock("MailCheckerService");
		plock = powerManager.newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
				"MailCheckerService");
        tts = new TTS(this, onTtsInit, true);
        mailChecker = new MailChecker(this);
	}

	@Override
	public void onStart(Intent intent, int startId) {
		log(String.format("started: %s", intent.getAction()));
		super.onStart(intent, startId);
		log("2");
		if (ACTION_CHECK_MAIL.equals(intent.getAction())) {
			log("3");
			plock.acquire();
			log("4");
			if (thread != null && thread.isAlive()) thread.interrupt();
			log("5");
			thread = new Thread(new Runnable() {
				public void run() {
					log("thread started");
					try{
						for (int i = 0; i < 12; ++i) {
							try {
								Thread.sleep(10 * 1000);
								log(String.format("%dth try", i + 1));
								log(wifiStatus());
								if (mailChecker.checkMails()) {
									log("success");
									break;
								} else {
									log("failed");
								}
							} catch (InterruptedException e) {
								log("interrupted exception");
								break;
							} catch (Exception e) {
								e.printStackTrace();
								//tts.speak("Error. " + e.getMessage(), 1, null);
								log(String.format("error: %s (%s)",
										e.getClass().toString(), e.getMessage()));
							}
							if (Thread.interrupted()) {
								log("interrupted");
								break;
							}
							if (!inWifiArea()) {
								log("probably out of wifi area");
								break;
							}
						}
					} finally {
						plock.release();
					}
				}
			});
			thread.start();
		} else {
	        setTimer(1000);
		}
	}
	
	private void log(String str) {
		Log.i("MailCheckerService", str);
//		try {
//			FileWriter writer = new FileWriter("/sdcard/debug.log", true);
//			writer.write(new Date().toString() + ": " + str + "\n");
//			writer.close();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
	
	private String wifiStatus() {
		String result = "";
		WifiInfo info = wifiManager.getConnectionInfo();
		if (info != null) {
			result += "wifi: " + info.getSupplicantState().toString() + "\n";
		} else {
			result += "wifi: not available\n";
		}
		DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
		result += "dhcp: " + (dhcpInfo == null ? "none" : dhcpInfo.toString()) + "\n";
		return result;
	}
	
	private boolean inWifiArea() {
		WifiInfo info = wifiManager.getConnectionInfo();
		if (info != null) {
			SupplicantState state = info.getSupplicantState();
			if (state == SupplicantState.DORMANT) {
				log("reenable wifi");
				wifiManager.setWifiEnabled(false);
				wifiManager.setWifiEnabled(true);
				return true;
			}
			return state != SupplicantState.DISCONNECTED &&
				state != SupplicantState.DORMANT &&
				state != SupplicantState.INACTIVE &&
				state != SupplicantState.INVALID &&
				state != SupplicantState.SCANNING &&
				state != SupplicantState.UNINITIALIZED;
		} else {
			return false;
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i("MailCheckerService", "destroyed");
		alive = false;
		tts.shutdown();
		mailChecker.destroy();
		alarmManager.cancel(checkMailPendingIntent);
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
    private TTS.InitListener onTtsInit = new TTS.InitListener() {
		public void onInit(int version) {
		}
    };
    
	private void setTimer(int msec) {
		if (checkMailPendingIntent != null) {
			alarmManager.cancel(checkMailPendingIntent);
		}
        Intent i = new Intent();
        i.setClassName("net.gimite.mailspeaks", "net.gimite.mailspeaks.MailCheckerService");
        i.setAction(ACTION_CHECK_MAIL);
        if (checkMailPendingIntent != null) {
        	alarmManager.cancel(checkMailPendingIntent);
        }
        checkMailPendingIntent = PendingIntent.getService(this, 0, i, 0);
        alarmManager.setRepeating(
        		AlarmManager.RTC_WAKEUP,
        		System.currentTimeMillis() + msec,
        		5 * 60 * 1000,
                checkMailPendingIntent);
        log(String.format("setTimer %d", msec));
	}
	
}
