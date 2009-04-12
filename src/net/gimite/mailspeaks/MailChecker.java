package net.gimite.mailspeaks;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.util.Log;

import com.android.email.mail.internet.BinaryTempFileBody;
import com.google.tts.TTS;

public class MailChecker {
    
    private static String tempDirPath = "/data/data/net.gimite.mailspeaks/tmp";

    private TTS tts;
    private Context context;
    private SQLiteDatabase db;
    private AudioManager audioManager;
    
    final int MAX_NOTIFICATIONS = 3;
    
    public MailChecker(Context context) {
        this.context = context;
        BinaryTempFileBody.setTempDirectory(new File(tempDirPath));
        initDatabase();
        audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    }
    
    private TTS.InitListener onTtsInit = new TTS.InitListener() {
        public void onInit(int version) {
        }
    };
    
    public boolean checkMails() {
        String speech = "";
        try {
            boolean success = true;
            cleanUpTempDirectory();
            for (Account account : Account.getAll(db)) {
                String result = account.checkMails();
                if (result != null) {
                    speech += result;
                } else {
                    success = false;
                }
            }
            return success;
        } finally {
            if (!speech.equals("") &&
                    audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                if (tts == null) tts = new TTS(context, onTtsInit, true);
                tts.setLanguage("en-us");
                tts.speak(speech, 1, null);
            }
        }
    }
    
    private void cleanUpTempDirectory() {
        Log.i("MailChecker", "cleanUpTempDirectory");
        File dir = new File(tempDirPath);
        dir.mkdirs();
        for (File file : dir.listFiles()) {
            file.delete();
        }
    }
    
    public Vector<Account> accounts() {
        return Account.getAll(db);
    }
    
    public Account getAccount(int id) {
        return Account.get(db, id);
    }
    
    public Account newAccount() {
        return new Account(db);
    }
    
    public void getAccountsStatus(ArrayList<HashMap<String, String>> data) {
        Account.getStatus(db, data);
    }
    
    public String getGlobalStatus() {
        Cursor cursor = getGlobalCursor();
        try {
            return Util.getString(cursor, "status");
        } finally {
            cursor.close();
        }
    }
    
    public void setGlobalStatus(String status) {
        Log.i("MailChecker", String.format("global: %s", status));
        db.execSQL("update global set status = ?", new Object[]{ status });
    }
    
    public boolean isEnabled() {
        Cursor cursor = getGlobalCursor();
        try {
            return Util.getBoolean(cursor, "enabled");
        } finally {
            cursor.close();
        }
    }
    
    public void setEnabled(boolean enabled) {
        db.execSQL("update global set enabled = ?", new Object[]{ enabled ? 1 : 0 });
        if (enabled) {
            startService();
        } else {
            stopService();
        }
    }
    
    public void startService() {
        Intent intent = new Intent();
        intent.setClass(context, MailCheckerService.class);
        context.startService(intent);
    }
    
    public void stopService() {
        Intent intent = new Intent();
        intent.setClass(context, MailCheckerService.class);
        context.stopService(intent);
    }
    
    public int getFailureCount() {
        Cursor cursor = getGlobalCursor();
        try {
            return Util.getInt(cursor, "failure_count");
        } finally {
            cursor.close();
        }
    }
    
    public void setFailureCount(int count) {
        db.execSQL("update global set failure_count = ?", new Object[]{ count });
    }
    
    private Cursor getGlobalCursor() {
        Cursor cursor = db.rawQuery("select * from global", new String[]{ });
        try {
            cursor.moveToFirst();
            return cursor;
        } catch (RuntimeException ex) {
            cursor.close();
            throw ex;
        }
    }
    
    public void destroy() {
        if (tts != null) tts.shutdown();
        db.close();
    }
    
    private void initDatabase() {
        db = context.openOrCreateDatabase("main", Context.MODE_PRIVATE, null);
        db.execSQL("begin immediate transaction");
        try {
            db.execSQL(
                "create table if not exists global (" +
                "  status string," +
                "  enabled boolean," +
                "  failure_count integer" +
                ")"
            );
            Cursor cursor = db.rawQuery("select * from global", new String[]{ });
            try {
                if (cursor.getCount() == 0) {
                    db.execSQL("insert into global default values");
                }
            } finally {
                cursor.close();
            }
            db.execSQL(
                "create table if not exists accounts (" +
                "  id integer primary key autoincrement," +
                "  protocol string," +
                "  user string," +
                "  password string," +
                "  host string," +
                "  port integer," +
                "  email string," +
                "  last_uid integer," +
                "  last_check_at integer," +
                "  last_success_at integer," +
                "  last_result string" +
                ")"
            );
            db.execSQL(
                "create table if not exists folders (" +
                "  id integer primary key autoincrement," +
                "  account_id integer," +
                "  name string" +
                ")"
            );
            db.execSQL("end transaction");
        } catch (RuntimeException ex) {
            db.execSQL("rollback transaction");
        }
    }
    
}
