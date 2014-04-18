package nus.cs4222.service;

import nus.cs4222.liftmeup.MainActivity;
import nus.cs4222.liftmeup.R;
import nus.dtn.api.fwdlayer.ForwardingLayerInterface;
import nus.dtn.api.fwdlayer.ForwardingLayerProxy;
import nus.dtn.middleware.api.DtnMiddlewareInterface;
import nus.dtn.middleware.api.DtnMiddlewareProxy;
import nus.dtn.middleware.api.MiddlewareEvent;
import nus.dtn.middleware.api.MiddlewareListener;
import nus.dtn.util.Descriptor;
import nus.dtn.util.DtnMessage;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

public class FallDetectingService extends Service implements
		SensorEventListener {
	/** Location coordinates*/
	private double currentLocationLat=0;
	private double currentLocationLng=0;
	/** Dialog pop up window*/
	private AlertDialog dialog;
	/** Notification*/
	private NotificationManager mNM;
	/** Sensor manager. */
	private SensorManager sensorManager;
	/** Gravity sensor. */
	private Sensor gravitySensor;
	private Sensor accelerationSensor;
	
	/**Vibrator*/
	private Vibrator vibrator;

	/** Handler to the main thread. */
	private Handler handler;

	/** Acceleration of the phone. */
	private float AccelerationMag;
	/** Gravity values. */
	private float[] gravityValues;

	/** DDMS Log Tag. */
	private static final String TAG = "FallDetection", ANGLE_TAG = "AngleChanged",
			ACCE_TAG = "Acce", GRAV_TAG = "Grav";
	private static final int IDLE = 0, PRIMARY_FALL = 1, LONG_LY = 2, CONFIRM_FALL = 3, RECOVER = 4;
	private static final int NOTIFICATION = R.string.title_activity_detecting_fall;

	/** Window to record acceleration data */
	static int WINDOW_SIZE = 50, LONGLY_TH = 75, RESET_COUNT_TH = 350,
			ALARM_TH = 1000;
	static float LOW_TH = 2.0f, HIGH_TH = 25.0f, ANGLE_TH = 70.0f, G = 9.8f,
			TH = 0.3f;
	static float[] AcceWindow = new float[WINDOW_SIZE];
	static float[][] GravWindow = new float[WINDOW_SIZE][3];
	static float[] GravBeforeImpact = new float[3];
	private int datacout;
	private int msgcout;
	private int curState;

	/** if fall detected */
	private boolean primaryFall;
	private boolean recoverFromPrimary;
	private boolean longLyFall;
	private boolean confirmFall;
	
	
    /** DTN Middle ware API. */
    private DtnMiddlewareInterface middleware;
    /** Fwd layer API. */
    private ForwardingLayerInterface fwdLayer;
    /** Sender's descriptor. */
    private Descriptor descriptor;
    
      
    @SuppressWarnings("deprecation")
	@Override
    public void onCreate() {
    	handler = new Handler();
    	android.app.AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Fall Detected!!!");
        builder.setMessage("Are You OK?");
        builder.setNegativeButton("Cancle", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            	initializeWindow();
            }
        });
        dialog = builder.create();
        dialog.getWindow().setType((WindowManager.LayoutParams.TYPE_SYSTEM_ALERT));
    	
    	
    	
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        

     // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.detecting);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_launcher, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.app_name),
                       text, contentIntent);
        notification.flags = notification.FLAG_NO_CLEAR;

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }

	
	
	

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// Check if it is an gravity event
		if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
			// Save the gravity readings
			gravityValues = event.values;
			// load gravity value to gravity window
			addGravData(event.values);
		}
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			float[] va = event.values;
			AccelerationMag = (float) Math.sqrt(va[0] * va[0] + va[1] * va[1] + va[2] * va[2]);
			addAcceData(AccelerationMag);
			Log.d("Acce",AccelerationMag+" ");
		}

		// important to trigger fall flag
		checkFall();
		
		responseFall();
		
	}

	private void responseFall() {
		if (recoverFromPrimary && curState != IDLE) {
			dialog.dismiss();
			curState = IDLE;
			initializeWindow();
			Toast.makeText(this, "Recoverd", Toast.LENGTH_LONG).show();
		}
		if (primaryFall && curState == IDLE) {
			curState = PRIMARY_FALL;
			Toast.makeText(this, "Primary Fall Detected", Toast.LENGTH_LONG).show();
		}
		if (longLyFall && curState == PRIMARY_FALL) {
			curState = LONG_LY;
			Toast.makeText(this, "Long Lie State Detected", Toast.LENGTH_LONG).show();
			//start.Timer()
			new Thread(){
	            public void run() {
	               // SystemClock.sleep(4000);
	                handler.post(new Runnable() {
	                    @Override
	                    public void run() {
	                        dialog.show();
	                    }
	                });
	                };
	        }.start();
		} 
		if (confirmFall && curState == LONG_LY) {
			curState = CONFIRM_FALL;
			Toast.makeText(this, "Sending Message...", Toast.LENGTH_LONG).show();
			sendMessage();//send message
			dialog.dismiss();
		} 
		if(longLyFall && datacout%50 ==0){
			vibrator.vibrate(500);
		}
		
	}





	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flag, int startId){
		Toast.makeText(this, "Fall Detecting Started", Toast.LENGTH_LONG).show();
		
		//Initialize
		initializeWindow();
		//location
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		LocationListener locationListener = new LocationListener() {
		    public void onLocationChanged(Location location) {
		    	currentLocationLat = location.getLatitude();
				currentLocationLng = location.getLongitude();
		    }

		    public void onStatusChanged(String provider, int status, Bundle extras) {}

		    public void onProviderEnabled(String provider) {}

		    public void onProviderDisabled(String provider) {}
		};
		Criteria criteria = new Criteria();
		String provider = locationManager.getBestProvider(criteria, true);
		Location location = locationManager.getLastKnownLocation(provider);
		if(location!=null)
		{	currentLocationLat = location.getLatitude();
			currentLocationLng = location.getLongitude();
		}
		locationManager.requestLocationUpdates(provider, 1000, 0, locationListener);
		//vibration
		vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
		//start middle ware
		startMiddleWare();
		
		gravityValues = new float[] { 0.0F, 0.0F, 0.0F };

		// Get an instance of the SensorManager
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		// Get a reference to the gravity sensor
		gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
		accelerationSensor = sensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if (gravitySensor == null) {
			Toast.makeText(this, "Opps, No GravitySensor Detected!", Toast.LENGTH_LONG).show();
		}
		sensorManager.registerListener(this, // Listener
				gravitySensor, // Sensor to measure
				20000); // Measurement interval (microsec)

		sensorManager.registerListener(this, // Listener
				accelerationSensor, // Sensor to measure
				20000); // Measurement interval (microsec)


		return START_STICKY;
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
		mNM.cancel(NOTIFICATION);
		sensorManager.unregisterListener(this);
		Toast.makeText(this, "Fall Detecting Stoped", Toast.LENGTH_LONG).show();
	}
	
	/** Initialization */
	private void initializeWindow() {
		curState = IDLE;
		datacout = 0;
		msgcout = 0;
		primaryFall = false;
		longLyFall = false;
		confirmFall = false;
		recoverFromPrimary = false;
		for (int i = 0; i < WINDOW_SIZE; i++) {
			AcceWindow[i] = -1.0f;
			GravWindow[i][0] = -1.0f;
			GravWindow[i][1] = -1.0f;
			GravWindow[i][2] = -1.0f;
		}
	}

	/** check if fullfill the fall pattern */
	private boolean checkFallPattern() {
		boolean isFall = false;
		boolean isLowReached = false;
		boolean isHighReached = false;
		for (int i = 0; i < WINDOW_SIZE; i++) {
			// if no data yet
			if (AcceWindow[i] == -1.0f) {
				continue;
			}

			if (AcceWindow[i] <= LOW_TH) {
				isLowReached = true;
				GravBeforeImpact = gravityValues;
				Log.d("BeforeImpact", "x: "+ gravityValues[0] + " y: "+ gravityValues[1] + " z: "+ gravityValues[0]);

			}
			if (isLowReached && AcceWindow[i] >= HIGH_TH) {
				isHighReached = true;
			}
			if (isLowReached && isHighReached) {
				isFall = true;
				Log.d("FallPattern", "YES " + AcceWindow[i]);
				break;
			}
		}

		return isFall;
	}

	/** Check if orientation changed enough */
	private boolean checkOrientation() {
		boolean changed = false;
		for (int i = 0; i < WINDOW_SIZE - 1; i++) {
			if (GravWindow[i][0] == -1.0f && GravWindow[i][1] == -1.0f
					&& GravWindow[i][2] == -1.0f) {
				continue;
			}
			float _angle=computeOrientationChanged(i, i + 1) ;
			if (_angle>= ANGLE_TH && _angle<= 180.0 - ANGLE_TH) {
				changed = true;
				Log.d("ANGLE_BOOL", "YES "
						+ _angle);
				break;
			}
		}

		return changed;
	}

	/** compute orientation changed, if given tow vector */
	private float computeOrientationChanged(int index1, int index2) {
		float angleChanged = 0.0f;
		float[] v1 = GravWindow[index1];
		float[] v2 = GravWindow[index2];
		float dotProduct = v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
		float mag1 = (float) Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2]
				* v1[2]);
		float mag2 = (float) Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1] + v2[2]
				* v2[2]);
		float cosValue = dotProduct / (mag1 * mag2);
		float radianAngle = (float) Math.acos(cosValue);
		// Convert to degrees
		angleChanged = radianAngle * (180.0F / (float) Math.PI);
		angleChanged = Math.abs(angleChanged);
		// for DDMS check
		Log.d(ANGLE_TAG, Float.toString(angleChanged));
		return angleChanged;
	}
	/** compute orientation changed, if given tow vector */
	private float computeOrientationChanged(float[] v1, float[] v2) {
		float angleChanged = 0.0f;
		float dotProduct = v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
		float mag1 = (float) Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2]
				* v1[2]);
		float mag2 = (float) Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1] + v2[2]
				* v2[2]);
		float cosValue = dotProduct / (mag1 * mag2);
		float radianAngle = (float) Math.acos(cosValue);
		// Convert to degrees
		angleChanged = radianAngle * (180.0F / (float) Math.PI);
		angleChanged = Math.abs(angleChanged);
		// for DDMS check
		Log.d(ANGLE_TAG, Float.toString(angleChanged));
		return angleChanged;
	}

	/** Check if is device is still */
	private boolean checkStill() {
		boolean isStill = true;
		for (int i = 0; i < WINDOW_SIZE - 1; i++) {
			if (AcceWindow[i] < G - TH && AcceWindow[i + 1] > G + TH) {
				Log.d("Still", AcceWindow[i]+" "+AcceWindow[i+1]);
				isStill = false;
				break;
			}
		}
		return isStill;
	}
	/** Check device if abs(Y) > 0.5 G position*/
	private boolean checkVertical() {
		boolean isV = false;
		for (int i = 0; i < WINDOW_SIZE ; i++) {
			if (GravWindow[i][1] > 0.5*G || GravWindow[i][1] < -0.5 * G) {
				isV = true;
				Log.d("V", "Y : "+GravWindow[i][1]);
				break;
			}
		}
		return isV;
	}

	/** Add acceleration data to acceleration window */
	private void addAcceData(float acce) {

		if (primaryFall) {
			datacout++;
		}
		for (int i = 0; i < WINDOW_SIZE - 1; i++) {
			AcceWindow[i] = AcceWindow[i + 1];
		}
		AcceWindow[WINDOW_SIZE - 1] = acce;
	}

	/** Add gravity data to gravity window */
	private void addGravData(float[] grav) {
		for (int i = 0; i < WINDOW_SIZE - 1; i++) {
			GravWindow[i] = GravWindow[i + 1];
		}
		GravWindow[WINDOW_SIZE - 1] = grav;
	}

	/** check all states of fall */
	private void checkFall() {
		// check for primary fall pattern
		if (checkFallPattern() /*&& checkOrientation()*/) {
			primaryFall = true;
			Log.d("primaryFall", "YES");
		}
		//check for long lie after fall
		if (primaryFall && datacout > LONGLY_TH) {
			boolean _still = checkStill();
			if (_still && datacout > RESET_COUNT_TH) {
					longLyFall = true;
					Log.d("longLyFall", "YES");
			}
			if ( !_still && checkVertical()) {
				initializeWindow();
				recoverFromPrimary = true;
				curState = RECOVER;
				Log.d("RecoverFall", "YES");
			}
		}
		//if long lie and time out, confirm fall detection
		if (longLyFall && datacout > ALARM_TH) {
			confirmFall = true;
			Log.d("confirmFall", "YES");
		}
	}
	
	/** Helper method to create toasts. */
	private void createToast ( String toastMessage ) {
		// Use a 'final' local variable, otherwise the compiler will complain
        final String toastMessageFinal = toastMessage;

        // Post a runnable in the Main UI thread
        handler.post ( new Runnable() {
                @Override
                public void run() {
                    Toast.makeText ( getApplicationContext() , 
                                     toastMessageFinal , 
                                     Toast.LENGTH_SHORT ).show();
                }
        });
	}
	/**function to send help message*/
	private void sendMessage(){
		Thread clickThread = new Thread() {
            public void run() {

                try {
                	// Construct the DTN message
                    DtnMessage message = new DtnMessage();
                    // Data part
                    message.addData()                  // Create data chunk
                        .writeString("Yaguang")
                        .writeString("fall")
                        .writeDouble(currentLocationLat)
                        .writeDouble(currentLocationLng);

                    // Broadcast the message using the fwd layer interface
                    fwdLayer.sendMessage ( descriptor , message , "server" , null );

                    // Tell the user that the message has been sent
                    createToast ( "Chat message broadcast!" + currentLocationLat + currentLocationLng);
                    //SmsManager smsManager = SmsManager.getDefault();
    				//smsManager.sendTextMessage("96775203", null, "I FALL at "+currentLocationLat+" "+currentLocationLng, null, null);
                }
                catch ( Exception e ) {
                    // Log the exception
                    Log.e ( "BroadcastApp" , "Exception while sending message" , e );
                }
            }
        };
        clickThread.start();

        // Inform the user
        createToast ( "Broadcasting message..." );
	}
	
	/**helper function to start middleware*/
	private void startMiddleWare(){
		try 
		{

			// Start the middleware
			middleware = new DtnMiddlewareProxy ( getApplicationContext() );
			middleware.start ( new MiddlewareListener() {
            public void onMiddlewareEvent ( MiddlewareEvent event ) {
                try {

                    // Check if the middleware failed to start
                    if ( event.getEventType() != MiddlewareEvent.MIDDLEWARE_STARTED ) {
                        throw new Exception( "Middleware failed to start, is it installed?" );
                    }

                    // Get the fwd layer API
                    fwdLayer = new ForwardingLayerProxy ( middleware );

                    // Get a descriptor for this user
                    // Typically, the user enters the username, but here we simply use IMEI number
                    descriptor = fwdLayer.getDescriptor ( "cs4222.falldetector" , "client" );

                    // Set the broadcast address
                    fwdLayer.setBroadcastAddress ( "cs4222.falldetector" , "everyone" );
                }
                catch ( Exception e ) {
                    // Log the exception
                    Log.e ( "LIFT-ME-UP" , "Exception in middleware start listener" , e );
                }
            }
        } );
		}
		catch ( Exception e ) {
		    // Log the exception
		    Log.e ( "LIFT-ME-UP" , "Exception in onCreate()" , e );
		    // Inform the user
		    createToast ( "Exception in onCreate(), check log" );
		}
	}
	/**helper function close middleware*/
	private void stopMiddleWare(){
		try {
            // Stop the middleware
            // Note: This automatically stops the API proxies, and releases descriptors/listeners
            middleware.stop();
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( "BroadcastApp" , "Exception on stopping middleware" , e );
            // Inform the user
            createToast ( "Exception while stopping middleware, check log" );
        }
	}

}
