/*
 * Copyright (C) 2009, 2010  Michael Novak <mike@androidnerds.org>
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
package com.michaelrnovak.util.logger;

import android.app.ListActivity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.michaelrnovak.util.logger.service.ILogProcessor;
import com.michaelrnovak.util.logger.service.LogProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class Logger extends ListActivity {
    public static final String TAG = "Logger";

    private ILogProcessor mService;
    private AlertDialog mDialog;
    private ProgressDialog mProgressDialog;
    private LoggerListAdapter mAdapter;
    private LayoutInflater mInflater;
    private int mFilter = -1;
    private int mBuffer = 0;
    private int mLogType = 0;
    private String mFilterTag = "";
    private String mFilterTagLowerCase = "";
    private boolean mServiceRunning = false;
    public static final int DIALOG_FILTER_ID = 1;
    public static final int DIALOG_SAVE_ID = 2;
    public static final int DIALOG_SAVE_PROGRESS_ID = 3;
    public static final int DIALOG_EMAIL_ID = 4;
    public static final int DIALOG_BUFFER_ID = 5;
    public static final int DIALOG_TYPE_ID = 6;
    public static final int DIALOG_TAG_ID = 7;
    public static final int FILTER_OPTION = Menu.FIRST;
    public static final int EMAIL_OPTION = Menu.FIRST + 1;
    public static final int SAVE_OPTION = Menu.FIRST + 2;
    public static final int BUFFER_OPTION = Menu.FIRST + 3;
    public static final int TYPE_OPTION = Menu.FIRST + 4;
    public static final int TAG_OPTION = Menu.FIRST + 5;
    final CharSequence[] items = {"Verbose", "Debug", "Info", "Warn", "Error", "Fatal", "Silent"};
    final char[] mFilters = {'V', 'D', 'I', 'W', 'E', 'F', 'S'};
    final CharSequence[] buffers = {"Main", "Radio", "Events"};
    final CharSequence[] types = {"Logcat", "Dmesg"};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getListView().setStackFromBottom(true);
        getListView().setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        getListView().setDividerHeight(0);

        mAdapter = new LoggerListAdapter(this);
        setListAdapter(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        bindService(new Intent(this, LogProcessor.class), mConnection, Context.BIND_AUTO_CREATE);

        //TODO: make sure this actually deletes and doesn't append.
        File f = new File("/sdcard/tmp.log");
        if (f.exists()) {
            f.deleteOnExit();
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        unbindService(mConnection);
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.getItem(0);

        if (mBuffer != 0) {
            item.setEnabled(false);
        } else {
            item.setEnabled(true);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, FILTER_OPTION, 1, R.string.menu_filter).setIcon(R.drawable.ic_menu_filter)
                .setEnabled(mBuffer == 0);
        menu.add(Menu.NONE, TAG_OPTION, 2, R.string.menu_tag).setIcon(R.drawable.ic_menu_tag);
        menu.add(Menu.NONE, BUFFER_OPTION, 3, R.string.menu_buffer).setIcon(android.R.drawable.ic_menu_manage);
        menu.add(Menu.NONE, EMAIL_OPTION, 4, R.string.menu_email).setIcon(android.R.drawable.ic_menu_send);
        menu.add(Menu.NONE, SAVE_OPTION, 5, R.string.menu_save).setIcon(android.R.drawable.ic_menu_save);
        menu.add(Menu.NONE, TYPE_OPTION, 6, R.string.menu_select).setIcon(R.drawable.ic_menu_monitor);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case FILTER_OPTION:
            onCreateDialog(DIALOG_FILTER_ID);
            break;
        case EMAIL_OPTION:
            generateEmailMessage();
            break;
        case SAVE_OPTION:
            onCreateDialog(DIALOG_SAVE_ID);
            break;
        case BUFFER_OPTION:
            onCreateDialog(DIALOG_BUFFER_ID);
            break;
        case TYPE_OPTION:
            onCreateDialog(DIALOG_TYPE_ID);
            break;
        case TAG_OPTION:
            onCreateDialog(DIALOG_TAG_ID);
            break;
        default:
            break;
        }

        return false;
    }
    
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        switch (id) {
        case DIALOG_FILTER_ID:
            builder.setTitle("Select a filter level");
            builder.setSingleChoiceItems(items, mFilter, mClickListener);
            mDialog = builder.create();
            break;
        case DIALOG_SAVE_ID:
            builder.setTitle("Enter filename:");
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            View v = inflater.inflate(R.layout.file_save, (ViewGroup) findViewById(R.id.layout_root));
            builder.setView(v);
            builder.setNegativeButton("Cancel", mButtonListener);
            builder.setPositiveButton("Save", mButtonListener);
            mDialog = builder.create();
            break;
        case DIALOG_SAVE_PROGRESS_ID:
            mProgressDialog = ProgressDialog.show(this, "", "Saving...", true);
            return mProgressDialog;
        case DIALOG_EMAIL_ID:
            mProgressDialog = ProgressDialog.show(this, "", "Generating attachment...", true);
            return mProgressDialog;
        case DIALOG_BUFFER_ID:
            builder.setTitle("Select a buffer");
            builder.setSingleChoiceItems(buffers, mBuffer, mBufferListener);
            mDialog = builder.create();
            break;
        case DIALOG_TYPE_ID:
            builder.setTitle("Select a log");
            builder.setSingleChoiceItems(types, mLogType, mTypeListener);
            mDialog = builder.create();
            break;
        case DIALOG_TAG_ID:
            builder.setTitle("Enter tag name");
            LayoutInflater inflate = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            View t = inflate.inflate(R.layout.file_save, (ViewGroup) findViewById(R.id.layout_root));
            EditText et = (EditText) t.findViewById(R.id.filename);
            et.setText(mFilterTag);
            builder.setView(t);
            builder.setNegativeButton("Clear Filter", mTagListener);
            builder.setPositiveButton("Filter", mTagListener);
            mDialog = builder.create();
            break;
        default:
            break;
        }

        mDialog.show();
        return mDialog;
    }
    
    DialogInterface.OnClickListener mClickListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which >= mFilters.length) {
                mFilter = -1;
            } else {
                mFilter = which;
            }

            updateFilter();
        }
    };

    DialogInterface.OnClickListener mBufferListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            mBuffer = which;
            updateBuffer();
        }
    };

    DialogInterface.OnClickListener mTypeListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            mLogType = which;
            updateLog();
        }
    };

    DialogInterface.OnClickListener mButtonListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which == -1) {
                EditText et = (EditText) mDialog.findViewById(R.id.filename);
                onCreateDialog(DIALOG_SAVE_PROGRESS_ID);
                Log.d(TAG, "Filename: " + et.getText().toString());

                try {
                    mService.write(et.getText().toString(), mFilterTag);
                } catch (RemoteException e) {
                    Log.e(TAG, "Trouble writing the log to a file");
                }
            }
        }
    };

    DialogInterface.OnClickListener mTagListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which == -1) {
                EditText et = (EditText) mDialog.findViewById(R.id.filename);
                mFilterTag = et.getText().toString().trim();
                mFilterTagLowerCase = mFilterTag.toLowerCase();
                updateFilterTag();
            } else {
                EditText et = (EditText) mDialog.findViewById(R.id.filename);
                et.setText("");
                mFilterTag = "";
                mFilterTagLowerCase = "";
                updateFilterTag();
            }
        }
    };

    public void stopLogging() {
        unbindService(mConnection);
        mServiceRunning = false;

        if (mServiceRunning) {
            Log.d(TAG, "mServiceRunning is still TRUE");
        }
    }

    public void startLogging() {
        bindService(new Intent(this, LogProcessor.class), mConnection, Context.BIND_AUTO_CREATE);

        try {
            mService.run(mLogType);
            mServiceRunning = true;
        } catch (RemoteException e) {
            Log.e(TAG, "Could not start logging");
        }
    }

    private void reset() {
        mAdapter.resetLines();

        try {
            mService.reset(buffers[mBuffer].toString());
        } catch (RemoteException e) {
            Log.e(TAG, "Service is gone...");
        }

        mDialog.dismiss();

    }

    private void updateFilter() {
        reset();
    }
    
    private void updateBuffer() {
        reset();
    }

    private void updateLog() {
        mAdapter.resetLines();

        try {
            mService.restart(mLogType);
        } catch (RemoteException e) {
            Log.e(TAG, "Service is gone...");
        }

        mDialog.dismiss();
    }
    
    private void updateFilterTag() {
        reset();
    }

    private void saveResult(String msg) {
        mProgressDialog.dismiss();

        if (msg.equals("error")) {
            Toast.makeText(this, "Error while saving the log to file!", Toast.LENGTH_LONG).show();
        } else if (msg.equals("saved")) {
            Toast.makeText(this, "Log has been saved to file.", Toast.LENGTH_LONG).show();
        } else if (msg.equals("attachment")) {
            Intent mail = new Intent(Intent.ACTION_SEND);
            mail.setType("text/plain");
            mail.putExtra(Intent.EXTRA_SUBJECT, "Logger Debug Output");
            mail.putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///sdcard/tmp.log"));
            mail.putExtra(Intent.EXTRA_TEXT, "Here's the output from my log file. Thanks!");
            startActivity(Intent.createChooser(mail, "Email:"));
        }
    }
    
    private void generateEmailMessage() {
        onCreateDialog(DIALOG_EMAIL_ID);

        try {
            mService.write("tmp.log", mFilterTag);
        } catch (RemoteException e) {
            Log.e(TAG, "Error generating email attachment.");
        }
    }

    public Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case LogProcessor.MSG_READ_FAIL:
                Log.d(TAG, "MSG_READ_FAIL");
                break;
            case LogProcessor.MSG_LOG_FAIL:
                Log.d(TAG, "MSG_LOG_FAIL");
                break;
            case LogProcessor.MSG_NEW_LINE:
                mAdapter.addLine((LogLine) msg.obj);
                break;
            case LogProcessor.MSG_LOG_SAVE:
                saveResult((String) msg.obj);
                break;
            default:
                super.handleMessage(msg);
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = ILogProcessor.Stub.asInterface((IBinder)service);
            LogProcessor.setHandler(mHandler);

            try {
                mService.run(mLogType);
                mServiceRunning = true;
            } catch (RemoteException e) {
                Log.e(TAG, "Could not start logging");
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "onServiceDisconnected has been called");
            mService = null;
        }
    };

    /*
     * This is the list adapter for the Logger, it holds an array of strings and adds them
     * to the list view recycling views for obvious performance reasons.
     */
    public class LoggerListAdapter extends BaseAdapter {
        private Context mContext;
        private ArrayList<LogLine> mLines;

        public LoggerListAdapter(Context c) {
            mContext = c;
            mLines = new ArrayList<LogLine>();
            mInflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public int getCount() {
            return mLines.size();
        }

        public long getItemId(int pos) {
            return pos;
        }

        public Object getItem(int pos) {
            return mLines.get(pos);
        }

        public View getView(int pos, View convertView, ViewGroup parent) {
            TextView holder;
            LogLine line = mLines.get(pos);

            if (convertView == null) {
                //inflate the view here because there's no existing view object.
                convertView = mInflater.inflate(R.layout.log_item, parent, false);

                holder = (TextView) convertView.findViewById(R.id.log_line);
                holder.setTypeface(Typeface.MONOSPACE);

                convertView.setTag(holder);
            } else {
                holder = (TextView) convertView.getTag();
            }

            if (mLogType == 0) {
                holder.setText(new LogFormattedString(line));
            } else {
                holder.setText(line.toString());
            }

            final boolean autoscroll =
                    (getListView().getScrollY() + getListView().getHeight() >= getListView().getBottom());

            if (autoscroll) {
                getListView().setSelection(mLines.size() - 1);
            }

            return convertView;
        }

        private int indexOfLogLevel(final char c) {
            int filters = mFilters.length;
            int index = 0;
            for (; index < filters && mFilters[index] != c; index++) {
            }
            return index < filters ? index : -1;
        }

        public void addLine(LogLine line) {
            if (null == line || indexOfLogLevel(line.getLevel()) < mFilter) {
                return;
            }

            if (!mFilterTagLowerCase.equals("")) {
                if (!mFilterTagLowerCase.equals(line.getTag().toLowerCase().trim())) {
                    return;
                }
            }

            mLines.add(line);
            notifyDataSetChanged();
        }
 
        public void resetLines() {
            mLines.clear();
            notifyDataSetChanged();
        }

        public void updateView() {
            notifyDataSetChanged();
        }
    }

    private static class LogFormattedString extends SpannableString {
        public static final HashMap<Character, Integer> LABEL_COLOR_MAP;

        public LogFormattedString(LogLine line) {
            super(line.toString() + '\n');

            try {
                Integer labelColor = LABEL_COLOR_MAP.get(line.getLevel());

                if (labelColor == null) {
                    labelColor = LABEL_COLOR_MAP.get('F');
                }

                setSpan(new ForegroundColorSpan(labelColor), 0, 1, 0);
                setSpan(new StyleSpan(Typeface.BOLD), 0, 1, 0);

                String header = line.getHeader();
                int headerLen = header.length();
                setSpan(new ForegroundColorSpan(labelColor), 0, headerLen, 0);
                setSpan(new StyleSpan(Typeface.ITALIC), 0, headerLen, 0);
            } catch (Exception e) {
                setSpan(new ForegroundColorSpan(0xffddaacc), 0, length(), 0);
            }
        }

        static {
            LABEL_COLOR_MAP = new HashMap<Character, Integer>();
            LABEL_COLOR_MAP.put('V', 0xffcccccc);
            LABEL_COLOR_MAP.put('D', 0xff9999ff);
            LABEL_COLOR_MAP.put('I', 0xffeeeeee);
            LABEL_COLOR_MAP.put('W', 0xffffff99);
            LABEL_COLOR_MAP.put('E', 0xffff9999);
            LABEL_COLOR_MAP.put('F', 0xffff0000);
        }
    }
}
