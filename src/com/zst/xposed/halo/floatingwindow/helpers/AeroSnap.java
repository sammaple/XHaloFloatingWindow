package com.zst.xposed.halo.floatingwindow.helpers;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.zst.xposed.halo.floatingwindow.Common;
import com.zst.xposed.halo.floatingwindow.hooks.MovableWindow;

public class AeroSnap {
	
	final static int UNKNOWN = -10000;
	final static int SNAP_NONE = 0;
	final static int SNAP_LEFT = 1;
	final static int SNAP_TOP = 2;
	final static int SNAP_RIGHT = 3;
	final static int SNAP_BOTTOM = 4;
	
	final Window mWindow;
	final Handler mHandler;
	final Context mContext;
	final int mDelay;
	
	static Runnable mRunnable;
	static int mRange = 100;
	static int mSensitivity = 50;
	static int mSnap = SNAP_NONE;
	static int[] mSnapParam = new int[3]; // w,h,g
	static int[] mOldParam = new int[2]; // w,h
	static int mScreenHeight;
	static int mScreenWidth;
	static boolean mSnapped;
	static int[] mOldLayout;
	static boolean mTimeoutRunning;
	static boolean mTimeoutDone;
	
	/**
	 * An Aero Snap Class to check if the current pointer's coordinates
	 * are in range of the snap region.
	 */
	
	
	public AeroSnap(Window window, int delay) {
		mWindow = window;
		mContext = window.getContext();
		mHandler = new Handler();
		mDelay = delay;
		refreshScreenSize();
	}
	
	public void dispatchTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_UP:
			finishSnap(isValidSnap() && mTimeoutDone);
			discardTimeout();
			break;
		case MotionEvent.ACTION_DOWN:
			if (!saveOldPosition()) {
				restoreOldPosition();	
			}
			refreshScreenSize();
			break;
		case MotionEvent.ACTION_MOVE:
			showSnap((int) event.getRawX(), (int) event.getRawY());
			
		}
	}
	
	private void showSnap(int x, int y) {
		Log.d("test1", "showsnap");
		initSnappable(x, y);
		calculateSnap();
		Log.d("test1", "issnappable");
		
		if (isValidSnap()) {
			broadcastShowWithTimeout();
		} else {
			broadcastHide(mContext);
		}
		
	}
	
	private void finishSnap(boolean apply) {
		if (apply) {
			WindowManager.LayoutParams lpp = mWindow.getAttributes();
			lpp.width = mSnapParam[0];
			lpp.height = mSnapParam[1];
			lpp.gravity = mSnapParam[2];
			lpp.x = (lpp.gravity == Gravity.RIGHT) ? (mScreenWidth / 2) : 0;
			lpp.y = (lpp.gravity == Gravity.BOTTOM) ? (mScreenHeight / 2) : 0;
			mWindow.setAttributes(lpp);
		} else {
			mSnap = SNAP_NONE;
		}
		refreshLayout();
		broadcastHide(mContext);
	}
	
	/**
	 * Initializes the current screen size with respect to rotation.
	 */
	private void refreshScreenSize() {
		final WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
		final DisplayMetrics metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);
		
		mScreenHeight = metrics.heightPixels;
		mScreenWidth = metrics.widthPixels;
	}
	
	/**
	 * Checks the range of the touch coordinates and set the respective side.
	 */
	private boolean initSnappable(int x, int y) {
		if ((Math.abs(mOldParam[0] - x) > mSensitivity) ||
			(Math.abs(mOldParam[1] - y) > mSensitivity)) {
			mOldParam[0] = x;
			mOldParam[1] = y;
			discardTimeout();
			return false;
		}
		mOldParam[0] = x;
		mOldParam[1] = y;
		
		if (x < mRange) {
			mSnap = SNAP_LEFT;
		} else if (x > (mScreenWidth - mRange)) {
			mSnap = SNAP_RIGHT;
		} else if (y < mRange) {
			mSnap = SNAP_TOP;
		} else if (y > (mScreenHeight - mRange)) {
			mSnap = SNAP_BOTTOM;
		} else {
			mSnap = SNAP_NONE;
			return false;
		}
		return true;
	}
	
	private boolean isValidSnap() {
		return 	(mSnapParam[0] != UNKNOWN) &&
				(mSnapParam[1] != UNKNOWN) &&
				(mSnapParam[2] != UNKNOWN);
	}
	
	private boolean saveOldPosition() {
		if (mSnapped) {
			return (mSnap == SNAP_NONE) || (mTimeoutRunning);
		}
		mSnapped = true;
		final WindowManager.LayoutParams params = mWindow.getAttributes();
		int[] layout = { params.x, params.y, params.width, params.height };
		mOldLayout = layout;
		return true;
	}
	
	private boolean restoreOldPosition() {
		if (!mSnapped) return false;
		WindowManager.LayoutParams params = mWindow.getAttributes();
		params.x = mOldLayout[0];
		params.y = mOldLayout[1];
		params.width = mOldLayout[2];
		params.height = mOldLayout[3];
		params.gravity = Gravity.LEFT | Gravity.TOP;
		mWindow.setAttributes(params);
		mSnapped = false;
		refreshLayout();
		return true;
	}
	
	private void calculateSnap() {
		switch (mSnap) {
		case SNAP_LEFT:
			mSnapParam[0] = (int) ((mScreenWidth / 2) - 0.5f);
			mSnapParam[1] = ViewGroup.LayoutParams.MATCH_PARENT;
			mSnapParam[2] = Gravity.TOP | Gravity.LEFT;
			break;
		case SNAP_RIGHT:
			mSnapParam[0] = (int) ((mScreenWidth / 2) - 0.5f);
			mSnapParam[1] = ViewGroup.LayoutParams.MATCH_PARENT;
			mSnapParam[2] = Gravity.RIGHT;
			break;
		case SNAP_TOP:
			mSnapParam[0] = ViewGroup.LayoutParams.MATCH_PARENT;
			mSnapParam[1] = (int) ((mScreenHeight / 2) - 0.5f);
			mSnapParam[2] = Gravity.TOP;
			break;
		case SNAP_BOTTOM:
			mSnapParam[0] = ViewGroup.LayoutParams.MATCH_PARENT;
			mSnapParam[1] = (int) ((mScreenHeight / 2) - 0.5f);
			mSnapParam[2] = Gravity.BOTTOM;
			break;
		case SNAP_NONE:
			mSnapParam[0] = UNKNOWN;
			mSnapParam[1] = UNKNOWN;
			mSnapParam[2] = UNKNOWN;
		}
	}
	
	private void refreshLayout() {
		MovableWindow.initAndRefreshLayoutParams(mWindow, mContext, mContext.getPackageName());
	}
	
	private void discardTimeout() {
		mTimeoutDone = false;
		mTimeoutRunning = false;
		mHandler.removeCallbacks(mRunnable);
	}
	
	private void broadcastShowWithTimeout() {
		if (mTimeoutRunning) return;
		if (mRunnable == null) {
			mRunnable = new Runnable() {
				@Override
				public void run() {
					broadcastShow(mContext,mSnapParam[0],mSnapParam[1],mSnapParam[2]);
					mTimeoutRunning = false;
					mTimeoutDone = true;
				}
			};
		}
		mTimeoutRunning = true;
		mHandler.postDelayed(mRunnable, 2000);
	}
	
	private void broadcastShow(Context ctx, int w, int h, int g) {
		Intent i = new Intent(Common.SHOW_OUTLINE);
		int[] array = { w, h, g };
		i.putExtra(Common.INTENT_APP_SNAP, array);
		ctx.sendBroadcast(i);
	}
	
	private void broadcastHide(Context ctx) {
		ctx.sendBroadcast(new Intent(Common.SHOW_OUTLINE));
	}
}
