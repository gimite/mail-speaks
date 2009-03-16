package net.gimite.mailspeaks;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.android.email.mail.*;
import com.android.email.mail.internet.BinaryTempFileBody;
import com.android.email.mail.store.ImapStore;
import com.google.tts.TTS;

public class MailChecker {
	
	private TTS tts;
	private String speech = "";
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
		Cursor accountRow = null;
		try {
			boolean success = true;
			accountRow = db.rawQuery("select * from accounts", new String[]{ });
			while (accountRow.moveToNext()) {
				if (!checkMails(accountRow)) success = false;
			}
			return success;
		} finally {
	    	if (!speech.equals("")) {
				tts.setLanguage("en-us");
				tts.speak(speech, 1, null);
				speech = "";
	    	}
	    	if (accountRow != null) accountRow.close();
		}
	}
	
	public String getStatus() {
		Cursor accountRow = null;
		try {
			accountRow = db.rawQuery("select * from accounts", new String[]{ });
			String status = "";
			while (accountRow.moveToNext()) {
				Date lastCheckAt = getDate(accountRow, "last_check_at");
				Date lastSuccessAt = getDate(accountRow, "last_success_at");
				String timeStatus = "";
				if (lastSuccessAt.equals(lastCheckAt)) {
					timeStatus = String.format(
						"Last check succeeded at %s.",
						dateToString(lastCheckAt));
				} else {
					timeStatus = String.format(
						"Last check failed at %s. Last successful check was at %s.",
						dateToString(lastCheckAt),
						dateToString(lastSuccessAt));
				}
				status += String.format("%s:\n%s\n%s.\n\n",
						getString(accountRow, "email"),
						timeStatus,
						getString(accountRow, "last_result"));
			}
			return status;
		} finally {
	    	if (accountRow != null) accountRow.close();
		}
	}
	
    @SuppressWarnings("unchecked")
	public boolean checkMails(Cursor accountRow) {
		int accountId = getInt(accountRow, "id");
    	String storeUrl = getStoreUrl(accountRow, false);
		long lastUid = getLong(accountRow, "last_uid");
		String email = getString(accountRow, "email");
		Cursor folderRow = db.rawQuery("select * from folders where account_id = ?",
				new String[]{ Integer.toString(accountId) });
		boolean succeed = true;
		try {
			Log.i("MailChecker", String.format("check started: %s (%s)",
					email, getStoreUrl(accountRow, true)));
			ImapStore store = new ImapStore(storeUrl);
	//		for (Folder f : st.getPersonalNamespaces()){
	//		    Log.i("MailChecker", "Folder: " + f.getName());
	//	    }
			while (folderRow.moveToNext()) {
				String folderName = getString(folderRow, "name");
				setStatus(accountRow, String.format("Opening folder %s ...", folderName));
				Folder folder = store.getFolder(folderName);
				Log.i("MailChecker", "got folder");
				if (folder.exists()) {
					setStatus(accountRow, String.format(
							"Loading message list of folder %s ...", folderName));
					folder.open(Folder.OpenMode.READ_ONLY);
					int count = folder.getMessageCount();
					Log.i("MailChecker", String.format("%d mails", count));
					int start = Math.max(count - MAX_NOTIFICATIONS - 1, 0) + 1;
					Message[] messages = folder.getMessages(start, count, null);
					setStatus(accountRow, String.format(
							"Fetching messages in folder %s ...", folderName));
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
						if (lastUid == 0 || uid <= lastUid) continue;
						if (i > 0 || messages.length <= MAX_NOTIFICATIONS) {
							String fromName = getFromName(m);
							Log.i("MailChecker", String.format("%s: %s /%s\n",
									m.getUid(), fromName, m.getSubject()));
							speak("Mail from " + fromName + ".");
						}
						++numNew;
					}
					if (numNew > MAX_NOTIFICATIONS) {
						setStatus(accountRow, String.format(
								"Found more than %d mails in folder %s",
								MAX_NOTIFICATIONS, folderName));
						speak("And more mails.");
					} else if (numNew > 0){
						setStatus(accountRow, String.format(
								"Found %d mail(s) in folder %s", numNew, folderName));
					} else {
						setStatus(accountRow, String.format(
								"No new mails found in folder %s", folderName));
						//speak("No new mails");
					}
					
					folder.close(false);
					if (messages.length > 0){
						long uid = Long.parseLong(messages[messages.length - 1].getUid());
						if (uid > lastUid) lastUid = uid;
					}
				} else {
					setStatus(accountRow, String.format(
						"Error reading folder %s: Connection failed or " +
						"folder %s doesn't exist", folderName, folderName));
					//speak("Error reading mailbox");
					succeed = false;
					break;
				}
			}
		} catch (MessagingException ex) {
			ex.printStackTrace();
			setStatus(accountRow, String.format("Error: %s", ex.getMessage()));
			succeed = false;
		} catch (RuntimeException ex) {
			ex.printStackTrace();
			setStatus(accountRow, String.format("Unexpected error: %s (%s)",
					ex.getMessage(), ex.getClass().getName()));
			throw ex;
		} finally {
			folderRow.close();
		}
		Log.i("MailChecker", "check finished");
		Date now = new Date();
		db.execSQL(
				"update accounts set last_uid = ?, last_check_at = ? where id = ?",
				new Object[]{ lastUid, now.getTime(), accountId });
		if (succeed) {
			db.execSQL("update accounts set last_success_at = ? where id = ?",
				new Object[]{ now.getTime(), accountId });
		}
		return succeed;
	}
    
	private void setStatus(Cursor accountRow, String status) {
		Log.i("MailChecker", String.format("%s: %s",
				getString(accountRow, "email"), status));
		db.execSQL("update accounts set last_result = ? where id = ?",
				new Object[]{ status, getInt(accountRow, "id") });
	}
	
	private String getStoreUrl(Cursor accountRow, boolean forLog) {
		try {
			return String.format("%s://%s:%s@%s:%d",
					getString(accountRow, "protocol"),
					URLEncoder.encode(getString(accountRow, "user"), "UTF-8"),
					forLog ? "****" :
						URLEncoder.encode(getString(accountRow, "password"), "UTF-8"),
					getString(accountRow, "host"),
					getInt(accountRow, "port"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
    private void speak(String text) {
    	speech += text + "\n";
    }
    
    public void destroy() {
    	tts.shutdown();
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
    
	private int getInt(Cursor cursor, String columnName) {
		int idx = cursor.getColumnIndexOrThrow(columnName);
		return cursor.getInt(idx);
	}
	
	private long getLong(Cursor cursor, String columnName) {
		int idx = cursor.getColumnIndexOrThrow(columnName);
		return cursor.getLong(idx);
	}
	
	private String getString(Cursor cursor, String columnName) {
		int idx = cursor.getColumnIndexOrThrow(columnName);
		return cursor.getString(idx);
	}
	
	private Date getDate(Cursor cursor, String columnName) {
		int idx = cursor.getColumnIndexOrThrow(columnName);
		Date date = new Date();
		date.setTime(cursor.getLong(idx));
		return date;
	}
	
    private String dateToString(Date date) {
        DateFormat format = DateFormat.getDateTimeInstance();
        return format.format(date);
    }
    
}
