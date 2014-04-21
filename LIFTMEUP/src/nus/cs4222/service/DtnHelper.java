package nus.cs4222.service;

import nus.cs4222.liftmeup.LiftMeUpConstants;
import nus.dtn.api.fwdlayer.ForwardingLayerInterface;
import nus.dtn.api.fwdlayer.ForwardingLayerProxy;
import nus.dtn.middleware.api.DtnMiddlewareInterface;
import nus.dtn.middleware.api.DtnMiddlewareProxy;
import nus.dtn.middleware.api.MiddlewareEvent;
import nus.dtn.middleware.api.MiddlewareListener;
import nus.dtn.util.Descriptor;
import nus.dtn.util.DtnMessage;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class DtnHelper {

	/** DTN Middle ware API. */
	private DtnMiddlewareInterface middleware;
	/** Fwd layer API. */
	private ForwardingLayerInterface fwdLayer;
	/** Sender's descriptor. */
	private Descriptor descriptor;

	private Context mContext;

	public DtnHelper(Context context) {
		mContext = context;
	}

	/** function to send help message */
	public void sendMessage() {
		Thread clickThread = new Thread() {
			public void run() {
				SharedPreferences pref = mContext.getSharedPreferences(LiftMeUpConstants.PREF_NAME, Context.MODE_PRIVATE);
				String name = pref.getString(LiftMeUpConstants.PREF_OWNER_NAME_KEY, "");
				Float lat = pref.getFloat(LiftMeUpConstants.PREF_LAT_KEY, 0);
				Float lng = pref.getFloat(LiftMeUpConstants.PREF_LAT_KEY, 0);
				
				try {
					// Construct the DTN message
					DtnMessage message = new DtnMessage();
					// Data part
					message.addData()
							// Create data chunk
							.writeString(name).writeString("fall")
							.writeDouble((double)lat)
							.writeDouble((double)lng);

					// Broadcast the message using the fwd layer interface
					fwdLayer.sendMessage(descriptor, message, "everyone", null);
				} catch (Exception e) {
					// Log the exception
					Log.e("BroadcastApp", "Exception while sending message", e);
				}
			}
		};
		clickThread.start();
	}

	/** helper function to start middleware */
	public void startMiddleWare() {
		try {
			// Start the middleware
			middleware = new DtnMiddlewareProxy(mContext);
			middleware.start(new MiddlewareListener() {
				public void onMiddlewareEvent(MiddlewareEvent event) {
					try {

						// Check if the middleware failed to start
						if (event.getEventType() != MiddlewareEvent.MIDDLEWARE_STARTED) {
							throw new Exception(
									"Middleware failed to start, is it installed?");
						}

						// Get the fwd layer API
						fwdLayer = new ForwardingLayerProxy(middleware);

						// Get a descriptor for this user
						// Typically, the user enters the username, but here we
						// simply use IMEI number
						descriptor = fwdLayer.getDescriptor(
								"cs4222.falldetector", "client");

						// Set the broadcast address
						fwdLayer.setBroadcastAddress("cs4222.falldetector",
								"everyone");
					} catch (Exception e) {
						// Log the exception
						Log.e("LIFT-ME-UP",
								"Exception in middleware start listener", e);
					}
				}
			});
		} catch (Exception e) {
			// Log the exception
			Log.e("LIFT-ME-UP", "Exception in onCreate()", e);
		}
	}

	/** helper function close middleware */
	public void stopMiddleWare() {
		try {
			// Stop the middleware
			// Note: This automatically stops the API proxies, and releases
			// descriptors/listeners
			middleware.stop();
		} catch (Exception e) {
			// Log the exception
			Log.e("BroadcastApp", "Exception on stopping middleware", e);
		}
	}
}
