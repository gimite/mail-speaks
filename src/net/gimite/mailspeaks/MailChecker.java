package net.gimite.mailspeaks;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.email.mail.*;
import com.android.email.mail.internet.BinaryTempFileBody;
import com.android.email.mail.store.ImapStore;
import com.google.tts.TTS;

public class MailChecker {
	
	private TTS tts;
	private SharedPreferences preferences;
	private String speech = "";
	
	final int MAX_NOTIFICATIONS = 3;
	
	public MailChecker(Context context) {
		BinaryTempFileBody.setTempDirectory(new File("/sdcard/net/gimite/tmp"));
        tts = new TTS(context, onTtsInit, true);
        preferences = context.getSharedPreferences("pref", 0);
	}
	
    private TTS.InitListener onTtsInit = new TTS.InitListener() {
		public void onInit(int version) {
		}
    };
    
	public boolean checkMails() throws MessagingException {
		try {
			boolean success = true;
			for (String id : folderIds()) {
				if (!checkMails(id)) success = false;
			}
			return success;
		} finally {
	    	if (!speech.equals("")) {
				tts.setLanguage("en-us");
				tts.speak(speech, 1, null);
				speech = "";
	    	}
		}
	}
	
    @SuppressWarnings("unchecked")
	public boolean checkMails(String id) throws MessagingException {
		String storeUrl = preferences.getString("folder." + id + ".storeUrl", null);
    	String targetFolder = preferences.getString("folder." + id + ".folderName", null);
		Log.i("MailChecker", String.format("check started: %s, %s",
				id, targetFolder));
		ImapStore st = new ImapStore(storeUrl);
//		for(Folder f : st.getPersonalNamespaces()){
//			Log.i("MailChecker", "Folder: " + f.getName());
//		}
		Folder fol = st.getFolder(targetFolder);
		Log.i("MailChecker", "got folder");
		if(fol.exists()){
			fol.open(Folder.OpenMode.READ_ONLY);
			int count = fol.getMessageCount();
			Log.i("MailChecker", String.format("%d mails", count));
			int start = Math.max(count - MAX_NOTIFICATIONS - 1, 0) + 1;
			Message[] messages = fol.getMessages(start, count, null);
			Log.i("MailChecker", "got messages");
            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.FLAGS);
            fp.add(FetchProfile.Item.ENVELOPE);
			fol.fetch(messages, fp, null);
			Log.i("MailChecker", "fetched messages");
			
			long lastUid = preferences.getLong("folder." + id + ".lastUid", 0);
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
				speak("And more mails.");
			}
			if (numNew == 0) {
				//speak("No new mails");
			}
			
			fol.close(false);
        	SharedPreferences.Editor editor = preferences.edit();
			if (messages.length > 0){
	        	editor.putLong("folder." + id + ".lastUid",
	        			Long.parseLong(messages[messages.length - 1].getUid()));
			}
			editor.putString("lastCheckTime", new Date().toString());
        	editor.commit();
			Log.i("MailChecker", "check finished");
			return true;
		}else{
			Log.i("MailChecker", String.format("error reading mailbox %s", targetFolder));
			//speak("Error reading mailbox");
			return false;
		}
	}
    
    private void speak(String text) {
    	speech += text + "\n";
    }
    
    // registerFolder("personal", "imap+ssl://gimite%40gmail.com:password@imap.gmail.com:993", "INBOX");
    public void registerFolder(String id, String storeUrl, String folder) {
    	SharedPreferences.Editor editor = preferences.edit();
    	Set<String> ids = folderIds();
    	ids.add(id);
    	String idsStr = "";
    	for (String s : ids) {
    		idsStr = idsStr == "" ? s : idsStr + "," + s;
    	}
    	editor.putString("folderIds", idsStr);
    	editor.putString("folder." + id + ".storeUrl", storeUrl);
    	editor.putString("folder." + id + ".folderName", folder);
    	editor.commit();
    }
    
    public String lastCheckTime() {
    	return preferences.getString("lastCheckTime", "");
    }
    
    private Set<String> folderIds() {
    	HashSet<String> ids = new HashSet<String>();
    	for (String s : preferences.getString("folderIds", "").split(",")) {
    		ids.add(s);
    	}
    	return ids;
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
    
}
