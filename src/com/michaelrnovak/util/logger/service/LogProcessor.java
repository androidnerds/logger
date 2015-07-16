/*
 * Copyright (C) 2009  Michael Novak <mike@androidnerds.org>
 *               2015 Gavriel Fleischer <flocsy@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.michaelrnovak.util.logger.service;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.support.v7.appcompat.BuildConfig;
import android.util.EventLog;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import com.michaelrnovak.util.logger.LogLine;
import com.michaelrnovak.util.logger.LoggerFragment;

public class LogProcessor extends Service {
	public static final String HIDDEN_TAG = "LogProcessor:HIDDEN";
	public static final String TAG = HIDDEN_TAG.substring(0, 12);
	public static final int MAX_LINES = 250;
	public static final int MSG_READ_FAIL = 1;
	public static final int MSG_LOG_FAIL = 2;
	public static final int MSG_NEW_LINE = 3;
	public static final int MSG_RESET_LOG = 4;
	public static final int MSG_LOG_SAVE = 5;

	private static Handler mHandler;

	private String mFile;
	private String mBuffer = "main";
	private int mLogFormat = 2; // 0:brief | 1:time | 2:long
	private Vector<LogLine> mScrollback;
	private int mType;
	private String mFilterTag;
	private volatile boolean threadKill = false;
	private final AtomicBoolean mStatus;

	public LogProcessor() {
		mStatus = new AtomicBoolean(false);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mScrollback = new Vector<LogLine>(MAX_LINES);
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		Log.v(TAG, "Logger Service has hit the onStart method.");
	}

	private void runLog() {
		Log.d(TAG, "runLog thread started");
		Process process = null;
		
		try {
			if (mType == 0) {
				process = Runtime.getRuntime().exec("/system/bin/logcat -v " + LoggerFragment.LOG_FORMAT[mLogFormat] + " -b " + mBuffer);
			} else if (mType == 1) {
				process = Runtime.getRuntime().exec("dmesg -s 1000000");
			}
		} catch (IOException e) {
			Log.e(TAG, "runLog: can't create reader process", e);
			communicate(MSG_LOG_FAIL);
			return;
		}
		
		BufferedReader reader = null;
		
		try {
			reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			int l = 0;
			while (!killRequested()) {
				String line = reader.readLine();
				try {
					if (null != line) {
						LogLine logLine = LogLine.fromString(line, LoggerFragment.LOG_FORMAT[mLogFormat].toString());
						if (logLine instanceof LogLine.Long) {
							boolean emptyLine = false;
							boolean nextLine = false;
							do {
								reader.mark(1024);
								line = null;
								if (reader.ready()) {
									line = reader.readLine();
								} else {
									nextLine = true;
								}
								if (emptyLine && null != line && line.length() > 0 && line.charAt(0) == '[') {
									reader.reset();
									nextLine = true;
								}
								emptyLine = (null == line || line.length() == 0);
							} while (!nextLine && ((LogLine.Long) logLine).add(line));
						}
						if (!HIDDEN_TAG.equals(logLine.getTag())) {
							logLine(logLine);
							if (mScrollback.size() >= MAX_LINES) {
								mScrollback.removeElementAt(0);
							}
							mScrollback.add(logLine);
						}
						l++;
					} else {
						Log.e(TAG, "runLog: reader closed");
						requestKill();
					}
				} catch (Exception e) {
					Log.e(TAG, "runLog: " + line + ";", e);
				}
			}
		} catch (IOException e) {
			communicate(MSG_READ_FAIL);
		} finally {
			Log.d(TAG, "Prepping thread for termination");
			if (null != reader) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
			if (null != process) {
				process.destroy();
			}
			mScrollback.removeAllElements();
		}
		Log.d(TAG, "Exiting thread...");
	}

	Runnable worker = new Runnable() {
		public void run() {
			Log.d(TAG, "before syncronized worker");
			synchronized (mStatus) {
				mStatus.set(false);
			}
			long startedAt = System.currentTimeMillis();
			runLog();
			long finishedAt = System.currentTimeMillis();
			Log.d(TAG, "Worker worked for " + (finishedAt - startedAt) + "ms. status... " + mStatus + " => true");
			synchronized (mStatus) {
				mStatus.set(true);
				mStatus.notify();
			}
			Log.d(TAG, "after syncronized worker");
		}
	};

	private synchronized void requestKill() {
		threadKill = true;
	}
	
	private synchronized boolean killRequested() {
		return threadKill;
	}
	
	private void communicate(int msg) {
		Message.obtain(mHandler, msg, "error").sendToTarget();
	}
	
	private void logLine(LogLine line) {
		Message.obtain(mHandler, MSG_NEW_LINE, line).sendToTarget();
	}
	
	public static void setHandler(Handler handler) {
		mHandler = handler;
	}
	
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		requestKill();
		stopSelf();
		
		return false;
	}
	
	private final ILogProcessor.Stub mBinder = new ILogProcessor.Stub() {

		@TargetApi(Build.VERSION_CODES.FROYO)
		private void kill() {
			requestKill();

			Log.d(TAG, "before syncronized waiting... for " + mBuffer);
			synchronized (mStatus/*LogProcessor.this*/) {
				while (!mStatus.get()) {
					try {
						// we need to send a log, to wake up the reader (this is like reader.notify())
						if ("main".equals(mBuffer)) {
							Log.v(BuildConfig.DEBUG ? TAG : HIDDEN_TAG, "main waiting...");
						} else if ("events".equals(mBuffer) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
							EventLog.writeEvent(0, (BuildConfig.DEBUG ? TAG : HIDDEN_TAG) + ": event waiting...");
						}
//							Log.d(TAG, "syncronized waiting...");
						mStatus.wait(1000);
					} catch (InterruptedException e) {
						Log.d(TAG, "waiting interrupted by timeout");
					}
				}
			}
			Log.d(TAG, "after syncronized waiting...");
//			while (!mStatus) {
//				try {
//					Log.v(TAG, "waiting...");
//				} catch (Exception e) {
//					Log.d(TAG, "Woot! obj has been interrupted!");
//				}
//			}

			threadKill = false;
		}

		public void reset(String buffer) {
			kill();

			mBuffer = buffer.toLowerCase();
			run(mType);
		}
		
		public void run(int type) {
			mType = type;
			mScrollback.removeAllElements();
			Thread thr = new Thread(worker);
			thr.start();
		}
		
		public void restart(int type) {
			kill();

			run(type);
		}
		
		public void stop() {
			Log.i(TAG, "stop() method called in service.");
			requestKill();
			stopSelf();
		}
		
		public void write(String file, String tag) {
			mFilterTag = tag;
			mFile = file;
			Thread thr = new Thread(writer);
			thr.start();
		}
	};
	
	Runnable writer = new Runnable() {
		public void run() {
			writeLog();
		}
	};
	
	private void writeLog() {
		
		try {			
			File f = new File("/sdcard/" + mFile);
			FileWriter w = new FileWriter(f);
			final String lowerCaseFilterTag = mFilterTag.toLowerCase();

			for (int i = 0; i < mScrollback.size(); i++) {
				final LogLine line = mScrollback.elementAt(i);

//				if (mFilterApp == 1 && line.getPid() != android.os.Process.myPid()) {
//					return;
//				}

				if (!lowerCaseFilterTag.equals("")) {
					if (lowerCaseFilterTag.equals(line.getTag().toLowerCase().trim())) {
		    			w.write(line + "\n");
		    		}
		    	} else {
		    		w.write(mScrollback.elementAt(i) + "\n");
		    	}

				i++;
			}
			
			if (!mFile.equals("tmp.log")) {
				Message.obtain(mHandler, MSG_LOG_SAVE, "saved").sendToTarget();
			} else {
				Message.obtain(mHandler, MSG_LOG_SAVE, "attachment").sendToTarget();
			}
			
			w.close();
			f = null;
		} catch (Exception e) {
			Log.e(TAG, "Error writing the log to a file.", e);
			Message.obtain(mHandler, MSG_LOG_SAVE, "error").sendToTarget();
		}
	}

}
