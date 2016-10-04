package benl.student.pptcontroller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class TouchScreenPC extends Activity {
	private static final String TAG = "TouchScreenPC";
	public static final String LOCAL_TOUCH = "local-touch-event";
	public static final String INST_TOUCH = "instructionFromTouch";
	public static final String INST_X = "x";
	public static final String INST_Y = "y";
	public static final String TOUCH_START = "TouchStart";
	public static final String TOUCH_MOVE = "TouchMove";
	public static final String TOUCH_STOP = "TouchStop";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(createLayout());
	}

	private View createLayout() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT);
		LinearLayout ll = new LinearLayout(this);
		ll.setLayoutParams(lp);
		TouchImage ti = new TouchImage(this);
		ll.addView(ti, lp);
		ti.setBackgroundColor(Color.BLUE);
		return ll;
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
				Log.d(TAG,"Action Down");
	        	intent.putExtra(INST_TOUCH, TOUCH_START);
	        	intent.putExtra(INST_X, event.getX()/getWidth());
	        	intent.putExtra(INST_Y, event.getY()/getHeight());
	    	} else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
				Log.d(TAG,"Action Up");
	        	intent.putExtra(INST_TOUCH, TOUCH_STOP);
	    	} else {
	    		return true;
	    	}
	    	LocalBroadcastManager.getInstance(TouchScreenPC.this).sendBroadcast(intent);
			return true;
		}
	}

}
