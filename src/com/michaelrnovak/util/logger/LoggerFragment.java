/*
 * Copyright (C) 2009, 2010  Michael Novak <mike@androidnerds.org>
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
package com.michaelrnovak.util.logger;

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
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
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

public class LoggerFragment extends Fragment {
    public static final String TAG = "Logger";
    public static final int DIALOG_FILTER_ID = 1;
    public static final int DIALOG_SAVE_ID = 2;
    public static final int DIALOG_SAVE_PROGRESS_ID = 3;
    public static final int DIALOG_EMAIL_ID = 4;
    public static final int DIALOG_BUFFER_ID = 5;
    public static final int DIALOG_TYPE_ID = 6;
    public static final int DIALOG_TAG_ID = 7;
    static final CharSequence[] LOG_LEVEL_NAMES = {"Verbose", "Debug", "Info", "Warn", "Error", "Fatal", "Silent"};
    static final char[] LOG_LEVEL_CHARS = {'V', 'D', 'I', 'W', 'E', 'F', 'S'};
    static final CharSequence[] BUFFERS = {"Main", "Radio", "Events"};
    static final CharSequence[] TYPES = {"Logcat", "Dmesg"};

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

    private FragmentActivity fragmentActivity;
    private ListView mListView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        fragmentActivity = (FragmentActivity)super.getActivity();
        setHasOptionsMenu(true);
        return inflater.inflate(R.layout.fragment_logger, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ListView listView = getListView();
        listView.setStackFromBottom(true);
        listView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        listView.setDividerHeight(0);

        mAdapter = new LoggerListAdapter(fragmentActivity);
        setListAdapter(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        fragmentActivity.bindService(new Intent(fragmentActivity, LogProcessor.class), mConnection, Context.BIND_AUTO_CREATE);

        //TODO: make sure this actually deletes and doesn't append.
        File f = new File("/sdcard/tmp.log");
        if (f.exists()) {
            f.deleteOnExit();
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        fragmentActivity.unbindService(mConnection);
    }
    
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_filter).setEnabled(mBuffer == 0);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_logger, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_filter) {
            onCreateDialog(DIALOG_FILTER_ID);
        } else if (id == R.id.action_email) {
            generateEmailMessage();
        } else if (id == R.id.action_save) {
            onCreateDialog(DIALOG_SAVE_ID);
        } else if (id == R.id.action_buffer) {
            onCreateDialog(DIALOG_BUFFER_ID);
        } else if (id == R.id.action_select) {
            onCreateDialog(DIALOG_TYPE_ID);
        } else if (id == R.id.action_tag) {
            onCreateDialog(DIALOG_TAG_ID);
        } else {
        }
        return false;
    }

    protected ListView getListView() {
        if (mListView == null) {
            mListView = (ListView)getView().findViewById(android.R.id.list);
        }
        return mListView;
    }
    protected void setListAdapter(ListAdapter adapter) {
        getListView().setAdapter(adapter);
    }
//    protected ListAdapter getListAdapter() {
//        ListAdapter adapter = getListView().getAdapter();
//        if (adapter instanceof HeaderViewListAdapter) {
//            return ((HeaderViewListAdapter)adapter).getWrappedAdapter();
//        } else {
//            return adapter;
//        }
//    }

    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragmentActivity);

        switch (id) {
        case DIALOG_FILTER_ID:
            builder.setTitle("Select a filter level");
            builder.setSingleChoiceItems(LOG_LEVEL_NAMES, mFilter, mClickListener);
            mDialog = builder.create();
            break;
        case DIALOG_SAVE_ID:
            builder.setTitle("Enter filename:");
            LayoutInflater inflater = (LayoutInflater)fragmentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View v = inflater.inflate(R.layout.file_save, (ViewGroup)getView().findViewById(R.id.layout_root));
            builder.setView(v);
            builder.setNegativeButton("Cancel", mButtonListener);
            builder.setPositiveButton("Save", mButtonListener);
            mDialog = builder.create();
            break;
        case DIALOG_SAVE_PROGRESS_ID:
            mProgressDialog = ProgressDialog.show(fragmentActivity, "", "Saving...", true);
            return mProgressDialog;
        case DIALOG_EMAIL_ID:
            mProgressDialog = ProgressDialog.show(fragmentActivity, "", "Generating attachment...", true);
            return mProgressDialog;
        case DIALOG_BUFFER_ID:
            builder.setTitle("Select a buffer");
            builder.setSingleChoiceItems(BUFFERS, mBuffer, mBufferListener);
            mDialog = builder.create();
            break;
        case DIALOG_TYPE_ID:
            builder.setTitle("Select a log");
            builder.setSingleChoiceItems(TYPES, mLogType, mTypeListener);
            mDialog = builder.create();
            break;
        case DIALOG_TAG_ID:
            builder.setTitle("Enter tag name");
            LayoutInflater inflate = (LayoutInflater)fragmentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View t = inflate.inflate(R.layout.file_save, (ViewGroup)getView().findViewById(R.id.layout_root));
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
            if (which >= LOG_LEVEL_CHARS.length) {
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
        fragmentActivity.unbindService(mConnection);
        mServiceRunning = false;

        if (mServiceRunning) {
            Log.d(TAG, "mServiceRunning is still TRUE");
        }
    }

    public void startLogging() {
        fragmentActivity.bindService(new Intent(fragmentActivity, LogProcessor.class), mConnection, Context.BIND_AUTO_CREATE);

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
            mService.reset(BUFFERS[mBuffer].toString());
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
            Toast.makeText(fragmentActivity, "Error while saving the log to file!", Toast.LENGTH_LONG).show();
        } else if (msg.equals("saved")) {
            Toast.makeText(fragmentActivity, "Log has been saved to file.", Toast.LENGTH_LONG).show();
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
            int filters = LOG_LEVEL_CHARS.length;
            int index = 0;
            for (; index < filters && LOG_LEVEL_CHARS[index] != c; index++) {
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
