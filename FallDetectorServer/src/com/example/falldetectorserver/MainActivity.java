package com.example.falldetectorserver;

import nus.dtn.api.fwdlayer.ForwardingLayerInterface;
import nus.dtn.api.fwdlayer.ForwardingLayerProxy;
import nus.dtn.api.fwdlayer.MessageListener;
import nus.dtn.middleware.api.DtnMiddlewareInterface;
import nus.dtn.middleware.api.DtnMiddlewareProxy;
import nus.dtn.middleware.api.MiddlewareEvent;
import nus.dtn.middleware.api.MiddlewareListener;
import nus.dtn.util.Descriptor;
import nus.dtn.util.DtnMessage;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends Activity {
	private TextView tv;
	private GoogleMap map;
	
    /** DTN Middleware API. */
    private DtnMiddlewareInterface middleware;
    /** Fwd layer API. */
    private ForwardingLayerInterface fwdLayer;

    /** Sender's descriptor. */
    private Descriptor descriptor;
    
    /** Handler to the main thread to do UI stuff. */
    private Handler handler;
    private TabHost tabs;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try
		{	setContentView(R.layout.activity_main);
			tabs=(TabHost)findViewById(android.R.id.tabhost); 
	        tabs.setup(); 
	        TabHost.TabSpec spec=tabs.newTabSpec("tag1"); 
	        spec.setContent(R.id.tab1); 
	        spec.setIndicator("Message"); 
	        tabs.addTab(spec); 
	
	        spec=tabs.newTabSpec("tag2"); 
	        spec.setContent(R.id.tab2); 
	        spec.setIndicator("Map"); 
	        tabs.addTab(spec); 
	        tabs.setCurrentTab(0);
	        
			map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
			map.setMyLocationEnabled(true);
			tv = (TextView) findViewById(R.id.output);
            // Set a handler to the current UI thread
            handler = new Handler();

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
                            descriptor = fwdLayer.getDescriptor ( "cs4222.falldetector" , "server" );

                            // Set the broadcast address
                            fwdLayer.setBroadcastAddress ( "cs4222.falldetector" , "everyone" );

                            // Register a listener for received chat messages
                            ChatMessageListener messageListener = new ChatMessageListener();
                            fwdLayer.addMessageListener ( descriptor , messageListener );
                        }
                        catch ( Exception e ) {
                            // Log the exception
                            Log.e ( "BroadcastApp" , "Exception in middleware start listener" , e );
                            // Inform the user
                            createToast ( "Exception in middleware start listener, check log" );
                        }
                    }
            });
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( "BroadcastApp" , "Exception in onCreate()" , e );
            // Inform the user
            createToast ( "Exception in onCreate(), check log" );
        }
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if(extras != null)
        {	LatLng location = new LatLng(extras.getDouble("lat"),extras.getDouble("lng"));
            map.clear();
            map.addMarker(new MarkerOptions().position(location));
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
            tabs.setCurrentTab(1);
        }
	}

    /** Called when the activity is destroyed. */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            // Stop the middleware
            // Note: This automatically stops the API proxies, and releases descriptors/listeners
            middleware.stop();
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( "falldetector" , "Exception on stopping middleware" , e );
            // Inform the user
            createToast ( "Exception while stopping middleware, check log" );
        }
    }

    /** Listener for received chat messages. */
    private class ChatMessageListener 
        implements MessageListener {

        /** {@inheritDoc} */
        public void onMessageReceived ( String source , 
                                        String destination , 
                                        DtnMessage message ) {

            try { 

                // Read the DTN message
                // Data part
                message.switchToData();
                final String name = message.readString();
                final String action = message.readString();
                final double lat = message.readDouble();
                final double lng = message.readDouble();
                
                final String receivedText = name + " " + action + "@" + lat + " " + lng;
                handler.post ( new Runnable() {
                    public void run() 
                    {
                    	tv.setText(receivedText);
                    	
                    	Intent intent = new Intent(getApplicationContext(),MainActivity.class);
                		intent.putExtra("lat", lat);
                		intent.putExtra("lng", lng);
                		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                		PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0,intent, PendingIntent.FLAG_UPDATE_CURRENT);
                		
						Notification n  = new NotificationCompat.Builder(getApplicationContext())
								.setContentIntent(contentIntent)
                    	        .setContentTitle("ALERT")
                    	        .setContentText("Fall detected from " + name)
                    	        .setSmallIcon(R.drawable.ic_launcher)
                    	        .setAutoCancel(true).build();
                    	    
                    	n.defaults = Notification.DEFAULT_ALL;
                    	NotificationManager notificationManager = 
                    	  (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

                    	notificationManager.notify(0, n);
                    }
                } );
                
                
                //send notification
            }
            catch ( Exception e ) {
                // Log the exception
                Log.e ( "BroadcastApp" , "Exception on message event" , e );
                // Tell the user
                createToast ( "Exception on message event, check log" );
            }
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
            } );
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		    case R.id.action_register:
		    startActivity(new Intent(this, GcmRegistrationActivity.class));
		    return true;
		    default:
		    return super.onOptionsItemSelected(item);
		}
	}
}
