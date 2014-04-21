package com.example.falldetectorserver;

import java.io.IOException;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

public class GcmRegistrationActivity extends Activity {
	public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
	final static String TAG = "GcmRegistrationActivity";
	private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	String SENDER_ID = "385682690667";
	GoogleCloudMessaging mGcm;
	String mRegId;
	
	private EditText mEditTextPhoneNumber;
	private TextView mTextViewRegId;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_gcm);
		
		mEditTextPhoneNumber = (EditText) findViewById(R.id.editText_PhoneNumber);
		mTextViewRegId = (TextView) findViewById(R.id.textView_RegId);
		
		TelephonyManager tMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String phoneNumber = tMgr.getLine1Number();
		mEditTextPhoneNumber.setText(phoneNumber);
		
		if (checkPlayServices()) {
			mGcm = GoogleCloudMessaging.getInstance(this);
			mRegId = getRegistrationId(this);
			
			if (mRegId.isEmpty()) {
				registerInBackground();
			}
			
			mTextViewRegId.setText(mRegId);
		} else {
			Log.i(TAG, "No valid Google Play Services APK found.");
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		checkPlayServices();
	}
	
	/**
	 * Check the device to make sure it has the Google Play Services APK. If
	 * it doesn't, display a dialog that allows users to download the APK from
	 * the Google Play Store or enable it in the device's system settings.
	 */
	private boolean checkPlayServices() {
	    int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
	    if (resultCode != ConnectionResult.SUCCESS) {
	        if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
	            GooglePlayServicesUtil.getErrorDialog(resultCode, this,
	            		PLAY_SERVICES_RESOLUTION_REQUEST).show();
	        } else {
	            Log.i(TAG, "This device is not supported.");
	            finish();
	        }
	        return false;
	    }
	    return true;
	}
	
	/**
	 * Gets the current registration ID for application on GCM service.
	 * <p>
	 * If result is empty, the app needs to register.
	 *
	 * @return registration ID, or empty string if there is no existing
	 *         registration ID.
	 */
	private String getRegistrationId(Context context) {
	    final SharedPreferences prefs = getGCMPreferences(context);
	    String registrationId = prefs.getString(PROPERTY_REG_ID, "");
	    if (registrationId.isEmpty()) {
	        Log.i(TAG, "Registration not found.");
	        return "";
	    }
	    // Check if app was updated; if so, it must clear the registration ID
	    // since the existing regID is not guaranteed to work with the new
	    // app version.
	    int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
	    int currentVersion = getAppVersion(context);
	    if (registeredVersion != currentVersion) {
	        Log.i(TAG, "App version changed.");
	        return "";
	    }
	    return registrationId;
	}

	/**
	 * @return Application's {@code SharedPreferences}.
	 */
	private SharedPreferences getGCMPreferences(Context context) {
	    // This sample app persists the registration ID in shared preferences, but
	    // how you store the regID in your app is up to you.
	    return getSharedPreferences(MainActivity.class.getSimpleName(),
	            Context.MODE_PRIVATE);
	}
	
	/**
	 * @return Application's version code from the {@code PackageManager}.
	 */
	private static int getAppVersion(Context context) {
	    try {
	        PackageInfo packageInfo = context.getPackageManager()
	                .getPackageInfo(context.getPackageName(), 0);
	        return packageInfo.versionCode;
	    } catch (NameNotFoundException e) {
	        // should never happen
	        throw new RuntimeException("Could not get package name: " + e);
	    }
	}
	
	/**
	 * Registers the application with GCM servers asynchronously.
	 * <p>
	 * Stores the registration ID and app versionCode in the application's
	 * shared preferences.
	 */
	private void registerInBackground() {
		final Context context = this;
	    new AsyncTask<Void, Void, String>() {
	        @Override
	        protected String doInBackground(Void... params) {
	            String msg = "";
	            try {
	                if (mGcm == null) {
	                	mGcm = GoogleCloudMessaging.getInstance(context);
	                }
	                mRegId = mGcm.register(SENDER_ID);
	                msg = "Device registered, registration ID=" + mRegId;
	                
	                // Persist the regID - no need to register again.
	                storeRegistrationId(context, mRegId);
	            } catch (IOException ex) {
	                msg = "Error :" + ex.getMessage();
	                // If there is an error, don't just keep trying to register.
	                // Require the user to click a button again, or perform
	                // exponential back-off.
	            }
	            return msg;
	        }

	        @Override
	        protected void onPostExecute(String msg) {
	        	mTextViewRegId.setText(mRegId);
	            //mDisplay.append(msg + "\n");
	        	Log.i(TAG, msg);
	        }
	    }.execute(null, null, null);
	}
	
	/**
	 * Stores the registration ID and app versionCode in the application's
	 * {@code SharedPreferences}.
	 *
	 * @param context application's context.
	 * @param regId registration ID
	 */
	private void storeRegistrationId(Context context, String regId) {
	    final SharedPreferences prefs = getGCMPreferences(context);
	    int appVersion = getAppVersion(context);
	    Log.i(TAG, "Saving regId on app version " + appVersion);
	    SharedPreferences.Editor editor = prefs.edit();
	    editor.putString(PROPERTY_REG_ID, regId);
	    editor.putInt(PROPERTY_APP_VERSION, appVersion);
	    editor.commit();
	}
	
	/**
	 * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP
	 * or CCS to send messages to your app. 
	 */
	public void sendRegistrationToBackend(View view) {
		String num = mEditTextPhoneNumber.getText().toString();
		final String url = "http://cs4222-lift-me-up.appspot.com/register?reg_id=" + mRegId +
				"&phone_number=" + num;
		
	    new AsyncTask<Void, Void, String>() {
	        @Override
	        protected String doInBackground(Void... params) {
	            String msg = "";
	    	    try {
	    	    	DefaultHttpClient httpClient = new DefaultHttpClient();
	    	    	HttpGet httpGet = new HttpGet(url);
	    	    	
	    	    	httpClient.execute(httpGet);
	    	    } catch (Exception e) {
	    	    	e.printStackTrace();
	    	    }
	            return msg;
	        }

	        @Override
	        protected void onPostExecute(String msg) {

	        }
	    }.execute(null, null, null);
	}
	
	public void sendUnregistrationToBackend(View view) {
		String num = mEditTextPhoneNumber.getText().toString();
		final String url = "http://cs4222-lift-me-up.appspot.com/unregister?reg_id=" + mRegId +
				"&phone_number=" + num;
		
	    new AsyncTask<Void, Void, String>() {
	        @Override
	        protected String doInBackground(Void... params) {
	            String msg = "";
	    	    try {
	    	    	DefaultHttpClient httpClient = new DefaultHttpClient();
	    	    	HttpGet httpGet = new HttpGet(url);
	    	    	
	    	    	httpClient.execute(httpGet);
	    	    } catch (Exception e) {
	    	    	e.printStackTrace();
	    	    }
	            return msg;
	        }

	        @Override
	        protected void onPostExecute(String msg) {

	        }
	    }.execute(null, null, null);
	}
}
