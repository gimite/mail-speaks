package net.gimite.mailspeaks;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.OnItemClickListener;

public class FoldersActivity extends Activity {

    private ListView foldersView;
    private String[] folders;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        folders = extras.getStringArray("folders");
        boolean[] checked = extras.getBooleanArray("checked");

        setContentView(R.layout.folders);
        foldersView = (ListView)findViewById(R.id.foldersView);
        foldersView.setOnItemClickListener(onFoldersViewClick);
        foldersView.setItemsCanFocus(false);
        foldersView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        foldersView.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_multiple_choice, folders));
        for (int i = 0; i < checked.length; ++i) {
            foldersView.setItemChecked(i, checked[i]);
        }
    }

    private OnItemClickListener onFoldersViewClick = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            Intent intent = new Intent();
            boolean[] checked = new boolean[folders.length];
            for (int i = 0; i < folders.length; ++i) {
                checked[i] = foldersView.isItemChecked(i);
            }
            intent.putExtra("folders", folders);
            intent.putExtra("checked", checked);
            setResult(RESULT_OK, intent);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
