package net.gimite.mailspeaks;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class StatusActivity extends Activity {

	private Button startButton;
	private Button stopButton;
	private Handler handler = new Handler();
	private TextView outputLabel;
	private MailChecker mailChecker;
	private Timer timer;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.status);
        outputLabel = (TextView)findViewById(R.id.outputLabel);
        startButton = (Button)findViewById(R.id.startButton);
        startButton.setOnClickListener(onStartButtonClick);
        stopButton = (Button)findViewById(R.id.stopButton);
        stopButton.setOnClickListener(onStopButtonClick);
        mailChecker = new MailChecker(this);
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
					outputLabel.setText(mailChecker.lastCheckTime());
				}
			});
		}
	};

}