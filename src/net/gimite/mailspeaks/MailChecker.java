package net.gimite.mailspeaks;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.android.email.mail.internet.BinaryTempFileBody;
import com.google.tts.TTS;

public class MailChecker {
    
    private TTS tts;
    private Context context;
    private SQLiteDatabase db;
    
    final int MAX_NOTIFICATIONS = 3;
    
    public MailChecker(Context context) {
        this.context = context;
        BinaryTempFileBody.setTempDirectory(new File("/sdcard/net/gimite/tmp"));
        tts = new TTS(context, onTtsInit, true);
        initDatabase();
    }
    
    private TTS.InitListener onTtsInit = new TTS.InitListener() {
        public void onInit(int version) {
        }
    };
    
    public boolean checkMails() {
        String speech = "";
        try {
            boolean success = true;
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
            if (!speech.equals("")) {
                tts.setLanguage("en-us");
                tts.speak(speech, 1, null);
            }
        }
    }
    
    public void getAccountsStatus(ArrayList<HashMap<String, String>> data) {
        Account.getStatus(db, data);
    }
    
    public String getGlobalStatus() {
        Cursor cursor = db.rawQuery("select * from global", new String[]{ });
        try {
            cursor.moveToFirst();
            return Util.getString(cursor, "status");
        } finally {
            cursor.close();
        }
    }
    
    public void setGlobalStatus(String status) {
        Log.i("MailChecker", String.format("global: %s", status));
        db.execSQL("update global set status = ?", new Object[]{ status });
    }
    
    public void destroy() {
        tts.shutdown();
    }
    
    private void initDatabase() {
        db = context.openOrCreateDatabase("main", Context.MODE_PRIVATE, null);
        db.execSQL("begin immediate transaction");
        try {
            db.execSQL(
                "create table if not exists global (" +
                "  status string" +
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
