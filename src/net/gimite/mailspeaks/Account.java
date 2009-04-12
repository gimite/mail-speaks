package net.gimite.mailspeaks;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;

import com.android.email.mail.Address;
import com.android.email.mail.FetchProfile;
import com.android.email.mail.Folder;
import com.android.email.mail.Message;
import com.android.email.mail.MessagingException;
import com.android.email.mail.store.ImapStore;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/*
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
 */
public class Account {

    static final int MAX_NOTIFICATIONS = 3;
    static final boolean FOURCE_SPEAK = false;
    
    private int id;
    public String protocol;
    public String user;
    public String password;
    public String host;
    public int port;
    public String email;
    private long lastUid;
    private Date lastCheckAt;
    private Date lastSuccessAt;
    private String lastResult;
    private Vector<String> folderNames;
    private SQLiteDatabase db;
    
    public static Vector<Account> getAll(SQLiteDatabase db) {
        Vector<Account> accounts = new Vector<Account>();
        Cursor cursor = db.rawQuery("select * from accounts", new String[]{ });
        try {
            while (cursor.moveToNext()) {
                accounts.add(new Account(db, cursor));
            }
            return accounts;
        } finally {
            cursor.close();
        }
    }
    
    public static Account get(SQLiteDatabase db, int id) {
        Cursor cursor = db.rawQuery("select * from accounts where id = ?",
                new String[]{ Integer.toString(id) });
        try {
            cursor.moveToFirst();
            return new Account(db, cursor);
        } finally {
            cursor.close();
        }
    }
    
    public static void getStatus(SQLiteDatabase db, ArrayList<HashMap<String, String>> data) {
        data.clear();
        for (Account account : getAll(db)) {
            HashMap<String, String> entry = new HashMap<String, String>();
            entry.put("email", account.email());
            entry.put("status", account.getFullStatus());
            data.add(entry);
        }
    }
    
    public Account(SQLiteDatabase db, Cursor cursor) {
        this.db = db;
        id = Util.getInt(cursor, "id");
        protocol = Util.getString(cursor, "protocol");
        user = Util.getString(cursor, "user");
        password = Util.getString(cursor, "password");
        host = Util.getString(cursor, "host");
        port = Util.getInt(cursor, "port");
        email = Util.getString(cursor, "email");
        lastUid = Util.getLong(cursor, "last_uid");
        lastCheckAt = Util.getDate(cursor, "last_check_at");
        lastSuccessAt = Util.getDate(cursor, "last_success_at");
        lastResult = Util.getString(cursor, "last_result");
    }
    
    public Account(SQLiteDatabase db) {
        this.db = db;
        id = 0;
        protocol = "imap+ssl";
        user = "";
        password = "";
        host = "imap.gmail.com";
        port = 993;
        email = "";
    }
    
    public void save() {
        if (id != 0) {
            db.execSQL(
                "update accounts set protocol = ?, user = ?, password = ?, " +
                "host = ?, port = ?, email = ? where id = ?",
                new Object[]{ protocol, user, password, host, port, email, id});
        } else {
            db.execSQL(
                "insert into accounts (protocol, user, password, host, port, email) " +
                "values (?, ?, ?, ?, ?, ?)",
                new Object[]{ protocol, user, password, host, port, email});
            // I don't know the right way to do this.
            Cursor cursor = db.rawQuery("select max(id) from accounts", new String[]{ });
            try {
                cursor.moveToFirst();
                id = cursor.getInt(0);
            } finally {
                cursor.close();
            }
        }
    }
    
    public void delete() {
        if (id == 0) throw new RuntimeException("Cannot delete unsaved account");
        db.execSQL("delete from folders where account_id = ?", new Object[]{ id });
        db.execSQL("delete from accounts where id = ?", new Object[]{ id });
    }
    
    public String email() { return email; }
    public int id() { return id; }
    
    public Vector<String> allFolderNames() throws MessagingException {
        ImapStore store = getStore();
        Vector<String> result = new Vector<String>();
        for (Folder folder : store.getPersonalNamespaces()){
            result.add(folder.getName());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public String checkMails() {
        String notification = "";
        try {
            Log.i("MailChecker", String.format("check started: %s (%s)",
                    email, getStoreUrl(true)));
            ImapStore store = getStore();
    //      for (Folder f : st.getPersonalNamespaces()){
    //          Log.i("MailChecker", "Folder: " + f.getName());
    //      }
            for (String folderName : folderNames()) {
                setStatus(String.format("Opening folder %s ...", folderName));
                Folder folder = store.getFolder(folderName);
                Log.i("MailChecker", "got folder");
                if (folder.exists()) {
                    setStatus("Loading message list of folder %s ...", folderName);
                    folder.open(Folder.OpenMode.READ_ONLY);
                    int count = folder.getMessageCount();
                    Log.i("MailChecker", String.format("%d mails", count));
                    int start = Math.max(count - MAX_NOTIFICATIONS - 1, 0) + 1;
                    Message[] messages = folder.getMessages(start, count, null);
                    setStatus("Fetching messages in folder %s ...", folderName);
                    FetchProfile fp = new FetchProfile();
                    fp.add(FetchProfile.Item.FLAGS);
                    fp.add(FetchProfile.Item.ENVELOPE);
                    folder.fetch(messages, fp, null);
                    Log.i("MailChecker", "fetched messages");
                    
                    Log.i("MailChecker", String.format("lastUid: %d", lastUid));
                    int numNew = 0;
                    for (int i = 0; i < messages.length; ++i) {
                        Message m = messages[i];
                        long uid = Long.parseLong(m.getUid());
                        if (!FOURCE_SPEAK && (lastUid == 0 || uid <= lastUid)) continue;
                        if (i > 0 || messages.length <= MAX_NOTIFICATIONS) {
                            String fromName = getFromName(m);
                            Log.i("MailChecker", String.format("%s: %s /%s\n",
                                    m.getUid(), fromName, m.getSubject()));
                            notification += "Mail from " + fromName + ".\n";
                        }
                        ++numNew;
                    }
                    if (numNew > MAX_NOTIFICATIONS) {
                        setStatus("Found more than %d mails in folder %s",
                                MAX_NOTIFICATIONS, folderName);
                        notification += "And more mails.\n";
                    } else if (numNew > 0){
                        setStatus("Found %d mail(s) in folder %s", numNew, folderName);
                    } else {
                        setStatus("No new mails found in folder %s", folderName);
                        if (FOURCE_SPEAK) notification += "No new mails.\n";
                    }
                    
                    folder.close(false);
                    if (messages.length > 0){
                        long uid = Long.parseLong(messages[messages.length - 1].getUid());
                        if (uid > lastUid) lastUid = uid;
                    }
                } else {
                    setStatus(
                        "Error reading folder %s: Connection failed or " +
                        "folder %s doesn't exist", folderName, folderName);
                    if (FOURCE_SPEAK) notification += "Error reading mailbox.\n";
                    notification = null;
                    break;
                }
            }
        } catch (MessagingException ex) {
            ex.printStackTrace();
            setStatus("Error: %s", ex.getMessage());
            notification = null;
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            setStatus("Unexpected error: %s (%s)",
                    ex.getMessage(), ex.getClass().getName());
            throw ex;
        }
        Log.i("MailChecker", "check finished");
        Date now = new Date();
        db.execSQL(
                "update accounts set last_uid = ?, last_check_at = ? where id = ?",
                new Object[]{ lastUid, now.getTime(), id });
        if (notification != null) {
            db.execSQL("update accounts set last_success_at = ? where id = ?",
                new Object[]{ now.getTime(), id });
        }
        return notification;
    }
    
    private ImapStore getStore() throws MessagingException {
        return new ImapStore(getStoreUrl(false));
    }
    
    private String getStoreUrl(boolean forLog) {
        try {
            return String.format("%s://%s:%s@%s:%d",
                    protocol,
                    URLEncoder.encode(user, "UTF-8"),
                    forLog ? "****" : URLEncoder.encode(password, "UTF-8"),
                    host,
                    port);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    
    public Vector<String> folderNames() {
        if (folderNames == null && id != 0) {
            Cursor cursor = db.rawQuery(
                    "select * from folders where account_id = ?",
                    new String[]{ Integer.toString(id) });
            try {
                folderNames = new Vector<String>();
                while (cursor.moveToNext()) {
                    folderNames.add(Util.getString(cursor, "name"));
                }
            } finally {
                cursor.close();
            }
        }
        return folderNames;
    }
    
    public void setFolderNames(Vector<String> folderNames) {
        this.folderNames = folderNames;
        db.execSQL("delete from folders where account_id = ?",
                new Object[]{ id });
        for (String name : folderNames) {
            db.execSQL("insert into folders (account_id, name) values (?, ?)",
                    new Object[]{ id, name });
        }
    }
    
    private void setStatus(String format, Object... args) {
        String status = String.format(format, args);
        Log.i("MailChecker", String.format("%s: %s", email, status));
        db.execSQL("update accounts set last_result = ? where id = ?",
                new Object[]{ status, id });
    }
    
    public String getFullStatus() {
        if (lastResult == null) {
            return "Never checked.";
        }
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
        return String.format("%s\n%s.", timeStatus, lastResult);
    }
    
    private String getFromName(Message m) throws MessagingException {
        if (m.getFrom().length > 0) {
            Address from = m.getFrom()[0];
            if (from.getPersonal() != null &&
                    from.getPersonal().matches("^[\\x20-\\x7f]+$")) {
                return from.getPersonal();
            } else {
                String[] fields = from.getAddress().split("@");
                return fields.length > 0 ? fields[0] : "someone";
            }
        } else {
            return "someone";
        }
    }
    
}
