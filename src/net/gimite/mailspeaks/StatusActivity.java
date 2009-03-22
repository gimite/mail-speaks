package net.gimite.mailspeaks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class StatusActivity extends Activity {

    private Button startButton;
    private Button stopButton;
    private Handler handler = new Handler();
    private TextView globalStatusLabel;
    private MailChecker mailChecker;
    private Timer timer;
    private ListView accountsView;
    private ArrayList<HashMap<String, String>> accountsData =
        new ArrayList<HashMap<String, String>>();
    private SimpleAdapter accountsAdapter;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.status);
        mailChecker = new MailChecker(this);

        globalStatusLabel = (TextView)findViewById(R.id.globalStatusLabel);
        startButton = (Button)findViewById(R.id.startButton);
        startButton.setOnClickListener(onStartButtonClick);
        stopButton = (Button)findViewById(R.id.stopButton);
        stopButton.setOnClickListener(onStopButtonClick);
        accountsView = (ListView)findViewById(R.id.accountsView);
        accountsAdapter = new SimpleAdapter(
                this,
                accountsData,
                android.R.layout.simple_list_item_2,
                new String[] { "email", "status" },
                new int[] { android.R.id.text1, android.R.id.text2 }
                );
        accountsView.setAdapter(accountsAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        timer = new Timer();
        timer.schedule(new UpdateTask(), 0, 1000);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        timer.cancel();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mailChecker.destroy();
    }

    private void startService() {
        Intent i = new Intent();
        i.setClass(this, MailCheckerService.class);
        startService(i);
    }
    
    private void stopService() {
        Intent i = new Intent();
        i.setClass(this, MailCheckerService.class);
        stopService(i);
    }
    
    private OnClickListener onStartButtonClick = new OnClickListener() {
        @Override
        public void onClick(View arg0) {
            startService();
        }
    };
    
    private OnClickListener onStopButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            stopService();
        }
    };
    
    private class UpdateTask extends TimerTask {
        public void run() {
            handler.post(new Runnable() {
                public void run() {
                    globalStatusLabel.setText(mailChecker.getGlobalStatus());
                    mailChecker.getAccountsStatus(accountsData);
                    accountsAdapter.notifyDataSetChanged();
                }
            });
        }
    };

}