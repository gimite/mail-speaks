package net.gimite.mailspeaks;

import java.util.Vector;

import com.android.email.mail.MessagingException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class AccountActivity extends Activity {

    static final int REQUEST_FOLDERS = 0;
    
    private Handler handler = new Handler();
    private MailChecker mailChecker;
    private EditText emailEdit;
    private EditText userEdit;
    private EditText passwordEdit;
    private EditText hostEdit;
    private EditText portEdit;
    private Account account;
    private Button foldersButton;
    private Vector<String> folderNames;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account);
        mailChecker = new MailChecker(this);
        emailEdit = (EditText)findViewById(R.id.emailEdit);
        foldersButton = (Button)findViewById(R.id.foldersButton);
        foldersButton.setOnClickListener(onFoldersButtonClick );
        userEdit = (EditText)findViewById(R.id.userEdit);
        passwordEdit = (EditText)findViewById(R.id.passwordEdit);
        hostEdit = (EditText)findViewById(R.id.hostEdit);
        portEdit = (EditText)findViewById(R.id.portEdit);
        Bundle extras = getIntent().getExtras();
        int id = extras.getInt("id");
        account = id == 0 ? mailChecker.newAccount() : mailChecker.getAccount(id);
        emailEdit.setText(account.email);
        userEdit.setText(account.user);
        passwordEdit.setText(account.password);
        hostEdit.setText(account.host);
        portEdit.setText(Integer.toString(account.port));
        folderNames = account.folderNames();
    }

    private OnClickListener onFoldersButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        final String[] candidates =
                            account.allFolders().toArray(new String[0]);
                        final boolean[] checked = new boolean[candidates.length];
                        for (int i = 0; i < candidates.length; ++i) {
                            checked[i] = folderNames.contains(candidates[i]);
                        }
                        handler.post(new Runnable() {
                            public void run() {
                                Intent intent = new Intent();
                                intent.setClass(AccountActivity.this, FoldersActivity.class);
                                intent.putExtra("folders", candidates);
                                intent.putExtra("checked", checked);
                                startActivityForResult(intent, REQUEST_FOLDERS);
                            }
                        });
                    } catch (MessagingException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    };
    
    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        Log.i("MailChecker", "account onresult");
        if (requestCode == REQUEST_FOLDERS && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            String[] folders = extras.getStringArray("folders");
            boolean[] checked = extras.getBooleanArray("checked");
            folderNames = new Vector<String>();
            for (int i = 0; i < folders.length; ++i) {
                if (checked[i]) folderNames.add(folders[i]);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
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
        if (!folderNames.equals(account.folderNames())) {
            account.setFolderNames(folderNames);
        }
        super.onDestroy();
        mailChecker.destroy();
    }

}
