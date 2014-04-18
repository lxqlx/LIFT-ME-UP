package nus.cs4222.liftmeup;

import java.security.Provider.Service;

import nus.cs4222.service.FallDetectingService;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {
	private TextView textView_Status;
	private EditText editText_Contact;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		textView_Status = (TextView) findViewById(R.id.textView_Status);
		editText_Contact = (EditText) findViewById(R.id.editText_Contact);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	public void startSensing(View view){
		textView_Status.setText("Sensing");
		Intent intent = new Intent(getBaseContext(), FallDetectingService.class);
        startService(intent);
	}
	public void stopSensing(View view){
		textView_Status.setText("Stoped");
		Intent intent = new Intent(getBaseContext(), FallDetectingService.class);
        stopService(intent);
	}

}
