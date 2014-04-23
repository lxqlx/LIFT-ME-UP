package nus.cs4222.service;

import nus.cs4222.liftmeup.LiftMeUpConstants;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;

public class LocationServiceHelper extends Service {
	
	private LocationListener mGpsLocationListener;
	private LocationListener mNetworkLocationListener;
	private Location mCurrentBestLocation;
	private static final int TWO_MINUTES = 1000 * 60 * 2;
	
	private static boolean IsRunning = false;
	
	@Override
	public int onStartCommand(Intent intent , int flag, int startId) {
		
		if (!IsRunning) {
			initLocationService();
			IsRunning = true;
		}
		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		removeLocationService();
		IsRunning = false;
	}

	private void initLocationService() {
		mGpsLocationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				if (isBetterLocation(location, mCurrentBestLocation)) {
					SharedPreferences pref = getSharedPreferences(
							LiftMeUpConstants.PREF_NAME, Context.MODE_PRIVATE);
					Editor editor = pref.edit();
					editor.putFloat(LiftMeUpConstants.PREF_LAT_KEY,
							(float) location.getLatitude());
					editor.putFloat(LiftMeUpConstants.PREF_LNG_KEY,
							(float) location.getLongitude());
					editor.commit();
				}
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
			}
		};
		
		mNetworkLocationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				if (isBetterLocation(location, mCurrentBestLocation)) {
					SharedPreferences pref = getSharedPreferences(
							LiftMeUpConstants.PREF_NAME, Context.MODE_PRIVATE);
					Editor editor = pref.edit();
					editor.putFloat(LiftMeUpConstants.PREF_LAT_KEY,
							(float) location.getLatitude());
					editor.putFloat(LiftMeUpConstants.PREF_LNG_KEY,
							(float) location.getLongitude());
					editor.commit();
				}
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
			}
		};

		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		String networkProvider = LocationManager.NETWORK_PROVIDER;
		String gpsProvider = LocationManager.GPS_PROVIDER;
		Location lastNetworkLoc = locationManager
				.getLastKnownLocation(networkProvider);
		Location lastGpsLoc = locationManager.getLastKnownLocation(gpsProvider);

		mCurrentBestLocation = lastGpsLoc;
		if (lastNetworkLoc != null
				&& isBetterLocation(lastNetworkLoc, mCurrentBestLocation)) {
			mCurrentBestLocation = lastNetworkLoc;
		}

		if (mCurrentBestLocation != null) {
			SharedPreferences pref = getSharedPreferences(
					LiftMeUpConstants.PREF_NAME, Context.MODE_PRIVATE);
			Editor editor = pref.edit();
			editor.putFloat(LiftMeUpConstants.PREF_LAT_KEY,
					(float) mCurrentBestLocation.getLatitude());
			editor.putFloat(LiftMeUpConstants.PREF_LNG_KEY,
					(float) mCurrentBestLocation.getLongitude());
			editor.commit();
		}

		if (locationManager.isProviderEnabled(networkProvider)) {
			locationManager.requestLocationUpdates(networkProvider, 1000, 0,
					mNetworkLocationListener);
		}

		if (locationManager.isProviderEnabled(gpsProvider)) {
			locationManager.requestLocationUpdates(gpsProvider, 1000, 0,
					mGpsLocationListener);
		}
	}

	private void removeLocationService() {
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (mNetworkLocationListener != null) {
			locationManager.removeUpdates(mNetworkLocationListener);
		}
		if (mGpsLocationListener != null) {
			locationManager.removeUpdates(mGpsLocationListener);
		}
	}

	/**
	 * Determines whether one Location reading is better than the current
	 * Location fix
	 * 
	 * @param location
	 *            The new Location that you want to evaluate
	 * @param currentBestLocation
	 *            The current Location fix, to which you want to compare the new
	 *            one
	 */
	private boolean isBetterLocation(Location location,
			Location currentBestLocation) {
		if (currentBestLocation == null) {
			// A new location is always better than no location
			return true;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
		boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use
		// the new location
		// because the user has likely moved
		if (isSignificantlyNewer) {
			return true;
			// If the new location is more than two minutes older, it must be
			// worse
		} else if (isSignificantlyOlder) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation
				.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(),
				currentBestLocation.getProvider());

		// Determine location quality using a combination of timeliness and
		// accuracy
		if (isMoreAccurate) {
			return true;
		} else if (isNewer && !isLessAccurate) {
			return true;
		} else if (isNewer && !isSignificantlyLessAccurate
				&& isFromSameProvider) {
			return true;
		}
		return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
		if (provider1 == null) {
			return provider2 == null;
		}
		return provider1.equals(provider2);
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
}
