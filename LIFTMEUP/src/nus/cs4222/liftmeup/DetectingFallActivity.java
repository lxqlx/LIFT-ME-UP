package nus.cs4222.liftmeup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import nus.cs4222.liftmeup.R;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class DetectingFallActivity extends Activity implements
		SensorEventListener {
	/** Sensor manager. */
	private SensorManager sensorManager;
	/** Gravity sensor. */
	private Sensor gravitySensor;
	private Sensor accelerationSensor;

	/** Reference to text view displaying the angle. */
	private TextView textView_Angle;
	private TextView textView_Acceleration;
	private TextView textView_PrimaryFall;
	private TextView textView_LongLyFall;
	private TextView textView_ConfirmFall;
	private Button btn_CancelAlarm;
	
	/**Viberator*/
	private Vibrator vibrator;

	/** Handler to the main thread. */
	private Handler handler;

	/** Angle of the phone. */
	private float angleY;
	private float angleZ;
	private float angleX;
	private float vAccelerationMag;
	private float AccelerationMag;
	/** Gravity values. */
	private float[] gravityValues;

	/** Acceleration log file output stream. */
	public PrintWriter AccelerationLogFileOut;
	/** XYZ log file output stream. */
	public PrintWriter XYZLogFileOut;
	/** DDMS Log Tag. */
	private static final String TAG = "FallDetection", ANGLE_TAG = "AngleChanged",
			ACCE_TAG = "Acce", GRAV_TAG = "Grav";

	/** Window to record acceleration data */
	static int WINDOW_SIZE = 50, LONGLY_TH = 75, RESET_COUNT_TH = 350,
			ALARM_TH = 1000;
	static float LOW_TH = 2.0f, HIGH_TH = 25.0f, ANGLE_TH = 70.0f, G = 9.8f,
			TH = 0.3f;
	static float[] AcceWindow = new float[WINDOW_SIZE];
	static float[][] GravWindow = new float[WINDOW_SIZE][3];
	static float[] GravBeforeImpact = new float[3];
	private int datacout;

	/** if fall detected */
	private boolean primaryFall;
	private boolean recoverFromPrimary;
	private boolean longLyFall;
	private boolean confirmFall;

	/** Called when the activity is created. */
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		// Call the super's onCreate()
		super.onCreate(savedInstanceState);

		// initialzie window
		initializeWindow();

		// try {
		// openLogFiles();
		// } catch (IOException e) {
		// //See from DDMS
		// Log.e ( TAG , "Unable to create activity" , e );
		// e.printStackTrace();
		// }
		// Set the content view
		setContentView(R.layout.activity_detecting_fall);
		// Get references to the GUI stuff
		textView_Angle = (TextView) findViewById(R.id.AngleTextView);
		textView_Acceleration = (TextView) findViewById(R.id.AccelerationTextView);
		textView_PrimaryFall = (TextView) findViewById(R.id.PrimaryFallTextView);
		textView_LongLyFall = (TextView) findViewById(R.id.LongLyFallTextView);
		textView_ConfirmFall = (TextView) findViewById(R.id.ConfirmFallTextView);
		btn_CancelAlarm = (Button) findViewById(R.id.CancelAlarmButton);
		
		vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

		// Create a handler to this (main) thread
		handler = new Handler();

		// Initialize the angle and values to 0.0
		angleY = 0.0F;
		gravityValues = new float[] { 0.0F, 0.0F, 0.0F };

		// Get an instance of the SensorManager
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		// Get a reference to the gravity sensor
		gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
		accelerationSensor = sensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if (gravitySensor == null) {
			textView_Angle
					.setText("Oops, there is no gravity sensor on this device :(");
			return;
		}
	}

	/** Called when the activity is resumed. */
	@Override
	protected void onResume() {
		super.onResume();
		// reinitializeWindow
		initializeWindow();

		// Start measuring again
		sensorManager.registerListener(this, // Listener
				gravitySensor, // Sensor to measure
				20000); // Measurement interval (microsec)

		sensorManager.registerListener(this, // Listener
				accelerationSensor, // Sensor to measure
				20000); // Measurement interval (microsec)
	}

	/** Called when the activity is paused. */
	@Override
	protected void onPause() {
		super.onPause();

		// log acceleration data and x y z of gravity
		// closeLogFiles();
		// Stop measuring the sensor
		sensorManager.unregisterListener(this);
	}

	/** Called when the sensor value is measured. */
	public void onSensorChanged(SensorEvent event) {

		// Check if it is an gravity event
		if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {

			// Positive y-axis (0, 0, 1)
			float[] yaxis = new float[] { 0.0F, 0.0F, 1.0F };
			float[] zaxis = new float[] { 0.0F, 1.0F, 0.0F };
			float[] xaxis = new float[] { 1.0F, 0.0F, 0.0F };

			// Calculate the angle between y-axis and gravity sensor vector
			// NOTE: This calculation can be easily optimized since there
			// are 0s and 1s
			// Use Dot product formula
			float[] vy = yaxis, vz = zaxis, vx = xaxis, vg = event.values;
			// but for logging we use UTC time
			long timeStamp = System.currentTimeMillis();
			// logXYZReading(timeStamp,vg[0],vg[1],vg[2]);

			float dotProductY = vy[0] * vg[0] + vy[1] * vg[1] + vy[2] * vg[2];
			float dotProductZ = vz[0] * vg[0] + vz[1] * vg[1] + vz[2] * vg[2];
			float dotProductX = vx[0] * vg[0] + vx[1] * vg[1] + vx[2] * vg[2];
			float unitMag = 1.0f;
			float sMag = (float) Math.sqrt(vg[0] * vg[0] + vg[1] * vg[1]
					+ vg[2] * vg[2]);
			// Log.d("Grav","sqrt:"+mag2);
			float cosValueY = dotProductY / (unitMag * sMag);
			float cosValueZ = dotProductZ / (unitMag * sMag);
			float cosValueX = dotProductX / (unitMag * sMag);

			float radianAngleY = (float) Math.acos(cosValueY);
			float radianAngleZ = (float) Math.acos(cosValueZ);
			float radianAngleX = (float) Math.acos(cosValueX);
			// Convert to degrees
			angleY = radianAngleY * (180.0F / (float) Math.PI);
			angleZ = radianAngleZ * (180.0F / (float) Math.PI);
			angleX = radianAngleX * (180.0F / (float) Math.PI);

			// Save the gravity readings
			gravityValues = event.values;
			// load gravity value to gravity window
			addGravData(event.values);
		}
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			float[] va = event.values;
			long timeStamp = System.currentTimeMillis();
			float gMag;
			if (gravityValues != null) {
				gMag = (float) Math.sqrt(gravityValues[0] * gravityValues[0]
						+ gravityValues[1] * gravityValues[1]
						+ gravityValues[2] * gravityValues[2]);
				vAccelerationMag = (va[0] * gravityValues[0] + va[1]
						* gravityValues[1] + va[2] * gravityValues[2])
						/ gMag;
				vAccelerationMag = Math.abs(vAccelerationMag);
			} else {
				gMag = G;
				vAccelerationMag = G;
			}
			AccelerationMag = (float) Math.sqrt(va[0] * va[0] + va[1] * va[1]
					+ va[2] * va[2]);

			// logAcceReading(timeStamp,AccelerationMag,vAccelerationMag);
			// load acceleration data to acceleration window
			addAcceData(vAccelerationMag);
			//addAcceData(AccelerationMag);

			Log.d(ACCE_TAG, Float.toString(vAccelerationMag) + " " + Float.toString(AccelerationMag));
		}

		// important to trigger fall flag
		checkFall();

		handler.post(new Runnable() {
			public void run() {
				textView_Angle.setText("Gravity Sensor" + "\nX: "
						+ gravityValues[0] + "\nY: " + gravityValues[1]
						+ "\nZ: " + gravityValues[2]
						+ "\nAngle of phone with y:" + angleY
						+ "\nAngle of phone with z:" + angleZ
						+ "\nAngle of phone with x:" + angleX);
				textView_Acceleration.setText("\nVertical Acceleration:"
						+ vAccelerationMag + "\nAcceleration:"
						+ AccelerationMag);
				if (recoverFromPrimary) {
					textView_PrimaryFall.setText("Recovered");
				}
				if (primaryFall) {
					textView_PrimaryFall.setText("Primary Fall Detected");
				}
				else if(!recoverFromPrimary){
					textView_PrimaryFall.setText("Primary: Nothing Detected");
				}
				if (longLyFall) {
					textView_LongLyFall.setText("Long ly Fall Detected"
							+ "\nTime Left: " + (ALARM_TH - datacout)/50);
				} else {
					textView_LongLyFall
							.setText("Long ly Fall: -------- Nothing Detected");
				}
				if (confirmFall) {
					textView_ConfirmFall.setText("Confirm Fall Detected"
							+ "\n Sending help message...");
					vibrator.vibrate(500);
				} else {
					textView_ConfirmFall
							.setText("Confirm Fall:+++++++ Nothing Detected");
				}
			}
		});
	}

	/** Called when the accuracy changes. */
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Ignore
	}

	/** Helper method to make the log files ready for writing. */
	public void openLogFiles() throws IOException {

		// First, check if the sdcard is available for writing
		String externalStorageState = Environment.getExternalStorageState();
		if (!externalStorageState.equals(Environment.MEDIA_MOUNTED)
				&& !externalStorageState.equals(Environment.MEDIA_SHARED))
			throw new IOException("sdcard is not mounted on the filesystem");

		// Second, create the log directory
		File logDirectory = new File(Environment.getExternalStorageDirectory(),
				"LiftMeUp");
		logDirectory.mkdirs();
		if (!logDirectory.isDirectory())
			throw new IOException("Unable to create log directory");

		// Third, create output streams for the log files (APPEND MODE)
		// Barometer log
		File logFile = new File(logDirectory, "xyz.csv");
		FileOutputStream fout = new FileOutputStream(logFile, true);
		AccelerationLogFileOut = new PrintWriter(fout);
		// Location log
		logFile = new File(logDirectory, "acceleration.csv");
		fout = new FileOutputStream(logFile, true);
		XYZLogFileOut = new PrintWriter(fout);
	}

	/** Helper method that closes the log files. */
	public void closeLogFiles() {

		// Close the barometer log file
		try {
			AccelerationLogFileOut.close();
		} catch (Exception e) {
			Log.e(TAG, "Unable to close Acceleration log file", e);
		} finally {
			AccelerationLogFileOut = null;
		}

		// Close the location log file
		try {
			XYZLogFileOut.close();
		} catch (Exception e) {
			Log.e(TAG, "Unable to close gravity XYZ log file", e);
		} finally {
			XYZLogFileOut = null;
		}
	}

	/** Helper method that logs the barometer reading. */
	private void logXYZReading(long timestamp, float x, float y, float z) {

		// Barometer details
		final StringBuilder sb = new StringBuilder();
		sb.append(getHumanReadableTime(timestamp) + ",");
		sb.append(x + ",");
		sb.append(y + ",");
		sb.append(z);

		// Log to the file (and flush)
		AccelerationLogFileOut.println(sb.toString());
		AccelerationLogFileOut.flush();
	}

	/** Helper method that logs the location reading. */
	private void logAcceReading(long timestamp, float acce1, float acce2) {

		// Location details
		final StringBuilder sb = new StringBuilder();
		sb.append(getHumanReadableTime(timestamp) + ",");
		sb.append(acce1 + ",");
		sb.append(acce2 + ",");

		// Log to the file (and flush)
		XYZLogFileOut.println(sb.toString());
		XYZLogFileOut.flush();
	}

	/** Helper method to get the human readable time from unix time. */
	private static String getHumanReadableTime(long unixTime) {
		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd h:mm:ssa");
		return sdf.format(new Date(unixTime));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.detecting_fall, menu);
		return true;
	}

	/** Initialization */
	private void initializeWindow() {
		datacout = 0;
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
		if (checkFallPattern() && checkOrientation()) {
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
				Log.d("RecoverFall", "YES");
			}
		}
		//if long lie and time out, confirm fall detection
		if (longLyFall && datacout > ALARM_TH) {
			confirmFall = true;
			Log.d("confirmFall", "YES");
		}
	}

	/** cancel alarm */
	public void cancelAlarm(View view) {
		initializeWindow();
	}

}
