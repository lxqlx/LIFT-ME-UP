package nus.cs4222.service;

import nus.cs4222.liftmeup.MainActivity;
import nus.cs4222.liftmeup.R;
import android.app.AlertDialog;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

public class FallDetectingService extends Service implements
		SensorEventListener {

	/** Dialog pop up window */
	private AlertDialog dialog;
	/** Notification */
	private NotificationManager mNM;
	
	/** Sensor manager. */
	private SensorManager sensorManager;
	/** Gravity sensor. */
	private Sensor gravitySensor;
	private Sensor accelerationSensor;

	/** Vibrator */
	private Vibrator vibrator;

	/** Handler to the main thread. */
	private Handler handler;

	/** Acceleration of the phone. */
	private float AccelerationMag;
	/** Gravity values. */
	private float[] gravityValues;

	/** DDMS Log Tag. */
	private static final String TAG = "FallDetection",
			ANGLE_TAG = "AngleChanged", ACCE_TAG = "Acce", GRAV_TAG = "Grav";
	private static final int IDLE = 0, PRIMARY_FALL = 1, LONG_LY = 2,
			CONFIRM_FALL = 3, RECOVER = 4;
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

	private DtnHelper mDtnHelper;
	private GcmHelper mGcmHelper;
	private LocationServiceHelper mLocationHelper;
	
	@Override
	public void onCreate() {
		handler = new Handler();
		android.app.AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Fall Detected!!!");
		builder.setMessage("Are You OK?");
		builder.setNegativeButton("Cancle", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				initDetectWindow();
			}
		});
		dialog = builder.create();
		dialog.getWindow().setType(
				(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT));

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, MainActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
		
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
		notificationBuilder.setSmallIcon(R.drawable.ic_launcher)
			.setContentTitle(getText(R.string.detecting))
			.setContentText(getText(R.string.title_activity_detecting_fall))
			.setContentIntent(contentIntent);
		
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNM.notify(NOTIFICATION, notificationBuilder.build());
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
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
			AccelerationMag = (float) Math.sqrt(va[0] * va[0] + va[1] * va[1]
					+ va[2] * va[2]);
			addAcceData(AccelerationMag);
			//Log.d("Acce", AccelerationMag + " ");
		}

		// important to trigger fall flag
		checkFall();

		responseFall();
	}

	private void responseFall() {
		if (recoverFromPrimary && curState != IDLE) {
			dialog.dismiss();
			curState = IDLE;
			initDetectWindow();
			Toast.makeText(this, "Recoverd", Toast.LENGTH_LONG).show();
		}
		if (primaryFall && curState == IDLE) {
			curState = PRIMARY_FALL;
			Toast.makeText(this, "Primary Fall Detected", Toast.LENGTH_LONG)
					.show();
		}
		if (longLyFall && curState == PRIMARY_FALL) {
			curState = LONG_LY;
			Toast.makeText(this, "Long Lie State Detected", Toast.LENGTH_LONG)
					.show();
			// start.Timer()
			new Thread() {
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
			Toast.makeText(this, "Sending Message...", Toast.LENGTH_LONG)
					.show();
			sendMessage();// send message
			dialog.dismiss();
		}
		if (longLyFall && datacout % 50 == 0) {
			vibrator.vibrate(500);
		}

	}

	private void sendMessage() {
		mDtnHelper.sendMessage();
		mGcmHelper.sendMessage();
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flag, int startId) {
		initDetectWindow();
		initSensors();
		
		mDtnHelper = new DtnHelper(getApplicationContext());
		mDtnHelper.startMiddleWare();
		mGcmHelper = new GcmHelper(getApplicationContext());
		mLocationHelper = new LocationServiceHelper(getApplicationContext());
		vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
		
		return START_STICKY;
	}

	private void initSensors() {
		gravityValues = new float[] { 0.0F, 0.0F, 0.0F };

		// Get an instance of the SensorManager
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		// Get a reference to the gravity sensor
		gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
		accelerationSensor = sensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if (gravitySensor == null) {
			Toast.makeText(this, "Opps, No GravitySensor Detected!",
					Toast.LENGTH_LONG).show();
		}
		sensorManager.registerListener(this, // Listener
				gravitySensor, // Sensor to measure
				20000); // Measurement interval (microsec)

		sensorManager.registerListener(this, // Listener
				accelerationSensor, // Sensor to measure
				20000); // Measurement interval (microsec)
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mNM.cancel(NOTIFICATION);
		sensorManager.unregisterListener(this);
		mLocationHelper.removeLocationService();
		mDtnHelper.stopMiddleWare();
		Toast.makeText(this, "Fall Detecting Stoped", Toast.LENGTH_LONG).show();
	}

	/** Initialization */
	private void initDetectWindow() {
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
				//Log.d("BeforeImpact", "x: " + gravityValues[0] + " y: "
				//		+ gravityValues[1] + " z: " + gravityValues[0]);

			}
			if (isLowReached && AcceWindow[i] >= HIGH_TH) {
				isHighReached = true;
			}
			if (isLowReached && isHighReached) {
				isFall = true;
				//Log.d("FallPattern", "YES " + AcceWindow[i]);
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
			float _angle = computeOrientationChanged(i, i + 1);
			if (_angle >= ANGLE_TH && _angle <= 180.0 - ANGLE_TH) {
				changed = true;
				Log.d("ANGLE_BOOL", "YES " + _angle);
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
				//Log.d("Still", AcceWindow[i] + " " + AcceWindow[i + 1]);
				isStill = false;
				break;
			}
		}
		return isStill;
	}

	/** Check device if abs(Y) > 0.5 G position */
	private boolean checkVertical() {
		boolean isV = false;
		for (int i = 0; i < WINDOW_SIZE; i++) {
			if (GravWindow[i][1] > 0.5 * G || GravWindow[i][1] < -0.5 * G) {
				isV = true;
				//Log.d("V", "Y : " + GravWindow[i][1]);
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
		if (checkFallPattern() /* && checkOrientation() */) {
			primaryFall = true;
			//Log.d("primaryFall", "YES");
			mLocationHelper.initLocationService();
		}
		// check for long lie after fall
		if (primaryFall && datacout > LONGLY_TH) {
			boolean _still = checkStill();
			if (_still && datacout > RESET_COUNT_TH) {
				longLyFall = true;
				//Log.d("longLyFall", "YES");
			}
			if (!_still && checkVertical()) {
				initDetectWindow();
				recoverFromPrimary = true;
				curState = RECOVER;
				//Log.d("RecoverFall", "YES");
				mLocationHelper.removeLocationService();
			}
		}
		// if long lie and time out, confirm fall detection
		if (longLyFall && datacout > ALARM_TH) {
			confirmFall = true;
			//Log.d("confirmFall", "YES");
		}
	}

}
