package nus.cs4222.service;

import nus.cs4222.liftmeup.LiftMeUpConstants;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

public class GcmHelper {
	private Context mContext;
	
	public GcmHelper(Context context) {
		mContext = context;
	}
	
	public void sendMessage() {
		SharedPreferences pref = mContext.getSharedPreferences(LiftMeUpConstants.PREF_NAME, Context.MODE_PRIVATE);
		String name = pref.getString(LiftMeUpConstants.PREF_OWNER_NAME_KEY, "");
		String num = pref.getString(LiftMeUpConstants.PREF_CAREGIVER_CONTACT_KEY, "");
		Float lat = pref.getFloat(LiftMeUpConstants.PREF_LAT_KEY, 0);
		Float lng = pref.getFloat(LiftMeUpConstants.PREF_LAT_KEY, 0);
		
		final String url = "http://cs4222-lift-me-up.appspot.com/send?phone_number=" + num + 
				"&latitude=" + lat + "&longitude=" + lng + "&name=" + name;
		
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
