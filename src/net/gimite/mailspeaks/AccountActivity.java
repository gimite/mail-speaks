package net.gimite.mailspeaks;

import java.util.Vector;

import com.android.email.mail.MessagingException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class AccountActivity extends Activity {

    static final int REQUEST_FOLDERS = 0;
    static final int MENU_ITEM_DELETE = Menu.FIRST;
    static final int DIALOG_CONNECTING = 0;
    static final int DIALOG_CONNECTION_FAILURE = 1;
    
    private Handler handler = new Handler();
    private MailChecker mailChecker;
    private EditText emailEdit;
    private EditText userEdit;
    private EditText passwordEdit;
    private EditText hostEdit;
    private EditText portEdit;
    private Account account;
    private Button foldersButton;
    private TextView foldersView;
    private Vector<String> folderNames;
    private Thread foldersThread;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account);
        mailChecker = new MailChecker(this);
        emailEdit = (EditText)findViewById(R.id.emailEdit);
        userEdit = (EditText)findViewById(R.id.userEdit);
        passwordEdit = (EditText)findViewById(R.id.passwordEdit);
        hostEdit = (EditText)findViewById(R.id.hostEdit);
        portEdit = (EditText)findViewById(R.id.portEdit);
        foldersButton = (Button)findViewById(R.id.foldersButton);
        foldersButton.setOnClickListener(onFoldersButtonClick );
        foldersView = (TextView)findViewById(R.id.foldersView);
        Bundle extras = getIntent().getExtras();
        int id = extras.getInt("id");
        account = id == 0 ? mailChecker.newAccount() : mailChecker.getAccount(id);
        emailEdit.setText(account.email);
        userEdit.setText(account.user);
        passwordEdit.setText(account.password);
        hostEdit.setText(account.host);
        portEdit.setText(Integer.toString(account.port));
        folderNames = account.folderNames();
        if (folderNames == null) {
            folderNames = new Vector<String>();
            folderNames.add("INBOX");
        }
        updateFoldersView(); 
    }
    
    private void updateFoldersView() {
        String str = null;
        for (String name : folderNames) {
            str = str == null ? name : str + ", " + name;
        }
        foldersView.setText(str);
    }

    private OnClickListener onFoldersButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            showDialog(DIALOG_CONNECTING);
            save();  // to use latest config to get folders list
            foldersThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        final String[] candidates =
                            account.allFolderNames().toArray(new String[0]);
                        final boolean[] checked = new boolean[candidates.length];
                        for (int i = 0; i < candidates.length; ++i) {
                            checked[i] = folderNames.contains(candidates[i]);
                        }
                        handler.post(new Runnable() {
                            public void run() {
                                dismissDialog(DIALOG_CONNECTING);
                                Intent intent = new Intent();
                                intent.setClass(AccountActivity.this, FoldersActivity.class);
                                intent.putExtra("folders", candidates);
                                intent.putExtra("checked", checked);
                                startActivityForResult(intent, REQUEST_FOLDERS);
                            }
                        });
                    } catch (MessagingException e) {
                        handler.post(new Runnable() {
                            public void run() {
                                dismissDialog(DIALOG_CONNECTING);
                                showDialog(DIALOG_CONNECTION_FAILURE);
                            }
                        });
                    }
                }
            });
            foldersThread.start();
        }
    };
    
    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FOLDERS && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            String[] folders = extras.getStringArray("folders");
            boolean[] checked = extras.getBooleanArray("checked");
            folderNames = new Vector<String>();
            for (int i = 0; i < folders.length; ++i) {
                if (checked[i]) folderNames.add(folders[i]);
            }
            updateFoldersView();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ITEM_DELETE, 0, "Delete account");
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_DELETE:
                if (account.id() != 0) account.delete();
                account = null;
                setResult(RESULT_CANCELED);
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void save() {
        account.email = emailEdit.getText().toString();
        account.user = userEdit.getText().toString();
        account.password = passwordEdit.getText().toString();
        account.host = hostEdit.getText().toString();
        try {
            account.port = Integer.parseInt(portEdit.getText().toString());
        } catch (NumberFormatException ex) {
            account.port = 993;
        }
        account.save();
    }
    
    @Override
    protected void onDestroy() {
        if (account != null) {
            save();
            if (!folderNames.equals(account.folderNames())) {
                account.setFolderNames(folderNames);
            }
        }
        super.onDestroy();
        mailChecker.destroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_CONNECTING:
                return new AlertDialog.Builder(this)
                    .setTitle("Opening the mailbox...")
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Log.i("GimiteTest", "canceled");
                            foldersThread.interrupt();
                            try {
                                foldersThread.join();
                            } catch (InterruptedException e) { }
                        }
                    })
                    .create();
            case DIALOG_CONNECTION_FAILURE:
                return new AlertDialog.Builder(this)
                    .setIcon(R.drawable.alert_dialog_icon)
                    .setTitle("Failed to open the mailbox.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                        }
                    })
                    .create();
            default:
                throw new RuntimeException("Unknown dialog");
        }
    }
    
}
