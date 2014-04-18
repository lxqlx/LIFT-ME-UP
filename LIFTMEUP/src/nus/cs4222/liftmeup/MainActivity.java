package nus.cs4222.liftmeup;

import java.security.Provider.Service;

import nus.cs4222.service.FallDetectingService;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
	private TextView textView_Status;
	private EditText editText_Contact;
	private EditText editText_Name;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		textView_Status = (TextView) findViewById(R.id.textView_Status);
		editText_Contact = (EditText) findViewById(R.id.editText_Contact);
		editText_Name = (EditText) findViewById(R.id.editText_Name);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	public void startSensing(View view){
		if(!editText_Contact.getText().toString().equals("") && !editText_Name.getText().toString().equals("")){
			textView_Status.setText("Sensing");
			Intent intent = new Intent(getBaseContext(), FallDetectingService.class);
			intent.putExtra("Name", editText_Name.getText().toString());
			intent.putExtra("ContactNum", editText_Contact.getText().toString());
			editText_Contact.setFocusable(false);
			editText_Name.setFocusable(false);
			
			InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		    mgr.hideSoftInputFromWindow(editText_Contact.getWindowToken(), 0);
		    mgr.hideSoftInputFromWindow(editText_Name.getWindowToken(), 0);
			
			startService(intent);
		}else{
			textView_Status.setText("Must Enter Name and Contact Number!");
		}
	}
	public void stopSensing(View view){
		textView_Status.setText("Stoped");
		editText_Contact.setFocusableInTouchMode(true);
		editText_Name.setFocusableInTouchMode(true);
		Intent intent = new Intent(getBaseContext(), FallDetectingService.class);
        stopService(intent);
	}

}
