package nus.cs4222.liftmeup;

import nus.cs4222.service.FallDetectingService;
import nus.cs4222.liftmeup.LiftMeUpConstants;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	static boolean IsFallDetectingServiceRunning = false;

	private EditText editText_Name;
	private EditText editText_Contact;
	private TextView textView_Status;
	private Button button_FallDetecting;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		textView_Status = (TextView) findViewById(R.id.textView_Status);
		editText_Contact = (EditText) findViewById(R.id.editText_Contact);
		editText_Name = (EditText) findViewById(R.id.editText_Name);
		button_FallDetecting = (Button) findViewById(R.id.button_FallDetecting);

		SharedPreferences pref = getSharedPreferences(LiftMeUpConstants.PREF_NAME, MODE_PRIVATE);
		String name = pref.getString(LiftMeUpConstants.PREF_OWNER_NAME_KEY, "");
		String contact = pref.getString(LiftMeUpConstants.PREF_CAREGIVER_CONTACT_KEY, "");
		editText_Contact.setText(contact);
		editText_Name.setText(name);

		if (IsFallDetectingServiceRunning) {
			updateStartedUI();
		} else {
			updateStoppedUI();
		}
	}

	private void updateStartedUI() {
		editText_Contact.setFocusable(false);
		editText_Name.setFocusable(false);
		button_FallDetecting.setText("Stop");
		textView_Status.setText("started");
	}

	private void updateStoppedUI() {
		editText_Contact.setFocusableInTouchMode(true);
		editText_Name.setFocusableInTouchMode(true);
		button_FallDetecting.setText("Start");
		textView_Status.setText("stopped");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public void onFallDetectingClick(View view) {
		Intent intent = new Intent(getBaseContext(), FallDetectingService.class);
		if (IsFallDetectingServiceRunning) {
			// Stop the service
			stopService(intent);
			IsFallDetectingServiceRunning = false;
			updateStoppedUI();
		} else {
			// Sanitize inputs
			String name = editText_Name.getText().toString().trim();
			String contact = editText_Contact.getText().toString().trim();
			if (name.equals("") || contact.equals("")) {
				Toast.makeText(this,
						"Please enter owner's name and caregiver's contact",
						Toast.LENGTH_SHORT).show();
				return;
			} else {
				// Save caregiver's name and contact
				SharedPreferences pref = getSharedPreferences(LiftMeUpConstants.PREF_NAME,
						MODE_PRIVATE);
				Editor editor = pref.edit();
				editor.putString(LiftMeUpConstants.PREF_OWNER_NAME_KEY, name);
				editor.putString(LiftMeUpConstants.PREF_CAREGIVER_CONTACT_KEY, contact);
				editor.apply();

				// Start the service
				startService(intent);
				IsFallDetectingServiceRunning = true;
				updateStartedUI();
			}
		}
	}

}
