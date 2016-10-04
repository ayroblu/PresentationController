package benl.student.pptcontroller;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

public class MainController extends Activity {
	private static final String TAG = "MainController";

    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    public static final String ADDRESS = "BTIPMacAddress";
    public static final String INST = "instructionFromActivity";
    public static final String INST_TO_ACTIVITY = "instructionToActivity";
    public static final String LOCAL = "local-event";
    public static final String LOCAL_FROM_SERVICE = "local-from-service-event";
    public static final String LOCAL_OUT_MESSAGE = "local-out-message";

	public static final String LOCAL_TOUCH = "local-touch-event";
	public static final String INST_TOUCH = "instructionFromTouch";
	public static final String INST_X = "x";
	public static final String INST_Y = "y";
	public static final String INST_P_COUNT = "pointerCount";
	public static final String TOUCH_START = "TouchStart";
	public static final String TOUCH_MOVE = "TouchMove";
	public static final String TOUCH_STOP = "TouchStop";
	public static final String SW_MONITOR = "Switch Monitor";
    
	private static final int REQUEST_ENABLE_BT = 3;
	private TextView out;
	private BluetoothAdapter btAdapter = null;
	private TouchImage ti;

	
	/** Called when the activity is first created. */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_controller);
		
		LinearLayout ll = (LinearLayout) findViewById(R.id.imagell);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT);
		ti = new TouchImage(this);
		ll.addView(ti, lp);

		out = (TextView) findViewById(R.id.out);
//		outlog("...In onCreate()...");
		
		setButtonListeners();

		btAdapter = BluetoothAdapter.getDefaultAdapter();
		CheckBTState();
	}

	public void onStart() {
		super.onStart();
//		outlog("...In onStart()...");
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this); 
		lbm.registerReceiver(serviceReceiver, new IntentFilter(LOCAL_FROM_SERVICE));
		lbm.registerReceiver(statusReceiver, new IntentFilter(LOCAL_OUT_MESSAGE));
	}

	private void setButtonListeners() {
		Button lbutton = (Button) findViewById(R.id.lbutton);
		Button rbutton = (Button) findViewById(R.id.rbutton);
		
		lbutton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				  sendToBTServer("left");
			}
		});

		rbutton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				  sendToBTServer("right");
			}
		});
	}
	
	
	private BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			byte[] inst = intent.getByteArrayExtra(MainController.INST_TO_ACTIVITY);

			Bitmap bitmap = BitmapFactory.decodeByteArray(inst, 0, inst.length);
			ti.setImageBitmap(bitmap);
			Log.d(TAG,"decoded bitmap? "+bitmap);
		}
	};

	private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String s = intent.getStringExtra(MainController.INST_TO_ACTIVITY);
			outlog(s);
		}
	};

	public void onStop() {
		super.onStop();
//		outlog("...In onStop()...");
		try {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver);
		} catch (Exception e) {
			Log.w(TAG, "receivers not registered: " + e.getMessage());
		}
	}
	
	private void CheckBTState() {
		// Check for Bluetooth support and then check to make sure it is turned on

		// Emulator doesn't support Bluetooth and will return null
		if(btAdapter==null) { 
			Log.w(TAG, "Fatal Error: Bluetooth Not supported. Aborting.");
		} else {
			if (btAdapter.isEnabled()) {
//				outlog("...Bluetooth is enabled...");
			} else {
				//Prompt user to turn on Bluetooth
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
		}
	}

	
	//---------------------------------------Select address
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        outlog("onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE_SECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data); //Secure??
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                //setupChat();
//            	testMessage();
            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    private void connectDevice(Intent data) {
        // Get the device MAC address
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

//        startServerOnThread(address);
        startBTServer(address);
    }

    private void sendToBTServer(String s) {
    	Intent intent = new Intent(LOCAL);
    	intent.putExtra(INST, s);
    	LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private void startBTServer(String address) {
		Intent serviceIntent = new Intent(this, BTServer.class);
		serviceIntent.putExtra(ADDRESS, address);
		startService(serviceIntent);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_controller, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
        case R.id.secure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
            return true;
        case R.id.switch_monitor:
        	sendToBTServer("Switch Monitor");
            return true;
        }
        return false;
    }
	
	private void outlog(final String s) {
		runOnUiThread(new Runnable(){
		    @Override
		    public void run(){
				out.setText(s);
				Log.w(TAG,s);
		    }
		});
	}
	

	private class TouchImage extends ImageView{
		public TouchImage(Context context) {
			super(context);
		}
		
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			Intent intent = new Intent(LOCAL_TOUCH);
	    	if (event.getActionMasked() == MotionEvent.ACTION_DOWN ||
	    			event.getActionMasked() == MotionEvent.ACTION_MOVE) {
	    		int pCount = event.getPointerCount();
	    		float[] xarr = new float[pCount];
	    		float[] yarr = new float[pCount];
	    		for (int i = 0; i < event.getPointerCount(); ++i) {
	    			xarr[i] = event.getX(i)/getWidth();
	    			yarr[i] = event.getY(i)/getHeight();
				}
	        	intent.putExtra(INST_TOUCH, TOUCH_START);
	        	intent.putExtra(INST_P_COUNT, pCount);
	        	intent.putExtra(INST_X, xarr);
	        	intent.putExtra(INST_Y, yarr);
	    	} else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
				Log.d(TAG,"Action Up");
	        	intent.putExtra(INST_TOUCH, TOUCH_STOP);
	    	} else {
	    		return true;
	    	}
	    	LocalBroadcastManager.getInstance(getApplicationContext())
	    	.sendBroadcast(intent);
			return true;
		}
	}
}