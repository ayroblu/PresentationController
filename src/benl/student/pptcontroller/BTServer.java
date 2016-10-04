package benl.student.pptcontroller;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.getpebble.android.kit.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import org.json.JSONException;

public class BTServer extends Service{
	private static final String TAG = BTServer.class.getSimpleName();
	private static final UUID MY_UUID =	UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private static final String PEBBLE_FILTER = "com.getpebble.action.app.RECEIVE";
	private static final String PC_CMD_LEFT = "left";
	private static final String PC_CMD_RIGHT = "right";
	private static final String PC_CMD_PEBBLE_LEFT = "pleft";
	private static final String PC_CMD_PEBBLE_RIGHT = "pright";
	private static final String PC_CMD_QUIT = "quit";
	private static final String PC_CMD_POINT_CLOSE = "point close";
	private static final String PC_CMD_MONITOR = "monitor";
	
	private static final int IN_BUTTON = 0;
	private static final int IN_LOG = 1;
	private static final int OUT_NOTES = 2;
//	private static final int OUT_ERROR = 3;
	private static final int BUTTON_UP = 0;
	private static final int BUTTON_SELECT = 1;
	private static final int BUTTON_DOWN = 2;

	private BluetoothAdapter btAdapter = null;
	private BluetoothSocket btSocket = null;
	private OutputStream outStream = null;
	private InputStream inStream = null;
	
	private String pendingMessage = null;

	
    @Override
	public IBinder onBind( Intent intent ){
		return null;
	}

	@Override
	public int onStartCommand( Intent intent, int flags, int startId ){
		Log.d(TAG, "BTServer onStartCommand");
//		final int transactionId = intent.getIntExtra( Constants.TRANSACTION_ID, -1 );
//		final String jsonData = intent.getStringExtra( Constants.MSG_DATA );
		final String address = intent.getStringExtra(MainController.ADDRESS);

//		handlePebbleJSON(jsonData);
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if (address == null) {
			Log.w(TAG, "null Address");
			return START_NOT_STICKY;
		} else if (btAdapter == null) {
			// device does not support bluetooth TODO
			stopSelf();
			return START_NOT_STICKY;
		}
		startServerOnThread(address);
		return START_NOT_STICKY;
	}
	
	private void registerReceivers() {
		LocalBroadcastManager.getInstance(this).registerReceiver(
				activityReceiver, new IntentFilter(MainController.LOCAL));
		LocalBroadcastManager.getInstance(this).registerReceiver(
				touchReceiver, new IntentFilter(MainController.LOCAL_TOUCH));
		this.registerReceiver(pebbleReceiver, new IntentFilter(PEBBLE_FILTER));
		
        PebbleKit.registerReceivedAckHandler(this, ackReceiver);
        PebbleKit.registerReceivedNackHandler(this, nackReceiver);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy service");
//		unregisterReceivers();
	}
	
	private void unregisterReceivers(){
		tryUnregister(pebbleReceiver,false);
		tryUnregister(activityReceiver, true);
		tryUnregister(touchReceiver,true);
		tryUnregister(ackReceiver,false);
		tryUnregister(nackReceiver,false);
	}
	private void tryUnregister(BroadcastReceiver br, boolean isLocal) {
		try {
			if (isLocal) {
				LocalBroadcastManager.getInstance(this).unregisterReceiver(br);
			} else {
				unregisterReceiver(br);
			}
		} catch (Exception e) {
			Log.w(TAG, "receivers not registered: " + e.getMessage());
		}
	}
	
	private void sendUp() throws IOException {
		Log.i(TAG, "Sending up message");
		PebbleDictionary data = new PebbleDictionary();
		data.addString(OUT_NOTES, "Back");
        PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_APP_UUID, data);
        pendingMessage = PC_CMD_PEBBLE_LEFT;
	}

	private void sendDown() throws IOException {
		Log.i(TAG, "Sending down message");
		PebbleDictionary data = new PebbleDictionary();
		data.addString(OUT_NOTES, "Forward");
        PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_APP_UUID, data);
        pendingMessage = PC_CMD_PEBBLE_RIGHT;
	}
	
	// ------------------------------------------------Handle Activity instructions
	private BroadcastReceiver activityReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String inst = intent.getStringExtra(MainController.INST);
			if (inst.equals(PC_CMD_QUIT)) {
				closeStream();
			} else if (inst.equals(PC_CMD_LEFT) || inst.equals(PC_CMD_RIGHT)) {
				outToPC(inst);
			} else if (inst.equals(MainController.SW_MONITOR)) {
				outToPC(PC_CMD_MONITOR);
			}
		}
	};
	private BroadcastReceiver touchReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String inst = intent.getStringExtra(MainController.INST_TOUCH);
			if (inst.equals(MainController.TOUCH_START)) {
				int pCount = intent.getIntExtra(MainController.INST_P_COUNT,0);
				float[] xarr = intent.getFloatArrayExtra(MainController.INST_X);
				float[] yarr = intent.getFloatArrayExtra(MainController.INST_Y);
				outToPC("Points: "+pCount);
				for (int j = 0; j < pCount; j++) {
					outToPC("Point: "+j+": "+xarr[j]+","+yarr[j]);
				}
			} else if (inst.equals(MainController.TOUCH_STOP)) {
				outToPC(PC_CMD_POINT_CLOSE);
			}
		}
	};
	
	private void outToPC(String s) {
		try {
			outStream.write((s+"\n").getBytes());
			outStream.flush();
			Log.d(TAG, "outToPC: "+s);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void closeStream() {
		if (outStream != null) {
			try {
				outStream.write("quit\n".getBytes());
				outStream.flush();
			} catch (IOException e) {
				Log.w(TAG,"Fatal Error: In closeStream() and failed to flush output stream: " + e.getMessage() + ".");
			}
		}

		try{
			btSocket.close();
		} catch (IOException e2) {
			Log.w(TAG, "Fatal Error: In closeStream() and failed to close socket." + e2.getMessage() + ".");
		}
		outlog("Stream Closed");
		
		unregisterReceivers();
	}
	
	// ------------------------------------------Handle Receiving pebble instructions
	public final static UUID PEBBLE_APP_UUID = UUID.fromString( "4F1A970C-E3F7-44F3-AE28-FFE594EB848A" );
	private BroadcastReceiver pebbleReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if( intent.getAction().equals( Constants.INTENT_APP_RECEIVE ) ){
				Log.i( TAG, "Received messaged from Pebble App." );

				final UUID receivedUuid = (UUID) intent.getSerializableExtra( Constants.APP_UUID );
				// Pebble-enabled apps are expected to be good citizens and only inspect broadcasts containing their UUID
				if( !PEBBLE_APP_UUID.equals( receivedUuid ) ){
					Log.w( TAG, "not my UUID" );
					return;
				}

				final int transactionId = intent.getIntExtra( Constants.TRANSACTION_ID, -1 );
				final String jsonData = intent.getStringExtra( Constants.MSG_DATA );
				if( jsonData == null || jsonData.isEmpty() ){
					Log.w( TAG, "jsonData null" );
					PebbleKit.sendNackToPebble( context, transactionId );
					return;
				}

				Log.i( TAG, "Sending ACK to Pebble. " + transactionId );
				PebbleKit.sendAckToPebble( context, transactionId );

				Log.i( TAG, "Processing Pebble data..." );
				handlePebbleJSON(jsonData);
			}
		}
	};
	
	private void handlePebbleJSON(String jsonData) {
		try{
			final PebbleDictionary data = PebbleDictionary.fromJson( jsonData );
			if (data.contains(IN_BUTTON)){
				long cmd = data.getInteger( IN_BUTTON );
				switch ((int)cmd) {
				case BUTTON_UP:
					sendUp();
					break;
				case BUTTON_SELECT:
					
					break;
				case BUTTON_DOWN:
					sendDown();
					break;
				default:
					Log.w( TAG, "Bad command received from pebble app: " + cmd );
					break;
				}
			}
			if (data.contains(IN_LOG)) {
				String s = data.getString(IN_LOG);
				Log.d(TAG, s); 
			}
		}catch( JSONException e ){
			Log.w( TAG, "failed reived -> dict" + e );
		} catch (IOException e) {
			e.printStackTrace();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
		
	PebbleKit.PebbleAckReceiver ackReceiver = new PebbleKit.PebbleAckReceiver(PEBBLE_APP_UUID) {
        @Override
        public void receiveAck(final Context context, final int transactionId) {
        	Log.d(TAG, "Acked");
        	if (pendingMessage != null) {
        		outToPC(pendingMessage);
        		pendingMessage = null;
        	}
        }
    };
    PebbleKit.PebbleNackReceiver nackReceiver = new PebbleKit.PebbleNackReceiver(PEBBLE_APP_UUID) {
        @Override
        public void receiveNack(final Context context, final int transactionId) {
        	Log.w(TAG, "Nacked message");
        }
    };
	// -----------------------------------------------------BT Server boilerplate code
	private void startServerOnThread(final String address) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				connectToServer(address);
			}
		}).start();
	}
	
	private void connectToServer(String address) {
		BluetoothDevice device = btAdapter.getRemoteDevice(address);
		outlog("Device Found!");

		try {
			btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
			outlog("...socket created");
		} catch (IOException e) {
			outlog("Fatal Error: In onResume() and socket create failed: " + e.getMessage() + ".");
		}

		btAdapter.cancelDiscovery();

		outlog("...discovery cancelled, attempting to connect");
		try {
			messageDisplay("Connecting...");
			btSocket.connect();
			outlog("...Connection established and data link opened...");

			try {
				outStream = btSocket.getOutputStream();
				inStream = btSocket.getInputStream();
				inputThread();
				registerReceivers();
				messageDisplay("Connected");
			} catch (IOException e) {
				Log.w(TAG,"Fatal Error: In onResume() and output stream creation failed:" + e.getMessage() + ".");
				stopSelf();
				messageDisplay("Connection Failed");
			}
		} catch (IOException e) {
			outlog("...connection failure");
			messageDisplay("Connection Failed");
			e.printStackTrace();
			try {
				btSocket.close();
			} catch (IOException e2) {
				Log.w(TAG,"Fatal Error: In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
			}
			stopSelf();
		}
	}
	
	private void inputThread() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				runInput();
			}
		}).start();
	}

	private void runInput() {
		BufferedReader bReader=new BufferedReader(new InputStreamReader(inStream));
		try {
			DataInputStream dis = new DataInputStream(inStream);
			while (true) {
				String lineRead = bReader.readLine();
				if (!lineRead.equals("image")) {
					Log.w(TAG, "lineRead failed");
					continue;
				}
				lineRead = bReader.readLine();
				int length;
				try {
					length = Integer.parseInt(lineRead);
				} catch (NumberFormatException e) {
					continue;
				}
				Log.d(TAG, "decoding image");
				
				byte[] buffer = new byte[length];
				dis.readFully(buffer);
				
				Intent intent = new Intent(MainController.LOCAL_FROM_SERVICE);
				intent.putExtra(MainController.INST_TO_ACTIVITY, buffer);
				LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
				  
//				Bitmap bitmap = BitmapFactory.decodeByteArray(buffer , 0, buffer.length);
//				Log.d(TAG,"decoded bitmap? "+bitmap);
			}
		} catch (Exception e) {
			e.printStackTrace();
			unregisterReceivers();
			stopSelf();
		}
	}

	private void outlog(final String s) {
		Log.d(TAG,s);
	}
	
	private void messageDisplay(String s) {
		Intent intent = new Intent(MainController.LOCAL_OUT_MESSAGE);
		intent.putExtra(MainController.INST_TO_ACTIVITY, s);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
}
