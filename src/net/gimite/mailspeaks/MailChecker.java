package net.gimite.mailspeaks;

import java.io.File;
import java.util.Date;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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
    
    public String getStatus() {
        Cursor accountRow = null;
        try {
            accountRow = db.rawQuery("select * from accounts", new String[]{ });
            String status = "";
            while (accountRow.moveToNext()) {
                Date lastCheckAt = Util.getDate(accountRow, "last_check_at");
                Date lastSuccessAt = Util.getDate(accountRow, "last_success_at");
                String timeStatus = "";
                if (lastSuccessAt.equals(lastCheckAt)) {
                    timeStatus = String.format(
                        "Last check succeeded at %s.",
                        Util.dateToString(lastCheckAt));
                } else {
                    timeStatus = String.format(
                        "Last check failed at %s. Last successful check was at %s.",
                        Util.dateToString(lastCheckAt),
                        Util.dateToString(lastSuccessAt));
                }
                status += String.format("%s:\n%s\n%s.\n\n",
                        Util.getString(accountRow, "email"),
                        timeStatus,
                        Util.getString(accountRow, "last_result"));
            }
            return status;
        } finally {
            if (accountRow != null) accountRow.close();
        }
    }
    
    public void destroy() {
        tts.shutdown();
    }
    
    private void initDatabase() {
        db = context.openOrCreateDatabase("main", Context.MODE_PRIVATE, null);
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
    }
    
}
