/*
    Privacy Friendly Pedometer is licensed under the GPLv3.
    Copyright (C) 2017  Tobias Neidig

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package com.example.clonepedometer.receivers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.legacy.content.WakefulBroadcastReceiver;

import com.example.clonepedometer.Factory;
import com.example.clonepedometer.models.WalkingMode;
import com.example.clonepedometer.persistence.StepCountPersistenceHelper;
import com.example.clonepedometer.persistence.WalkingModePersistenceHelper;
import com.example.clonepedometer.utils.StepDetectionServiceHelper;


/**
 * Stores the current step count in database.
 *
 * @author Tobias Neidig
 * @version 20160720
 */

public class StepCountPersistenceReceiver extends WakefulBroadcastReceiver {
    private static final String LOG_CLASS = StepCountPersistenceReceiver.class.getName();
    private WalkingMode oldWalkingMode;
    /**
     * The application context
     */
    private Context context;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            StepCountPersistenceHelper.storeStepCounts(service, context, oldWalkingMode);
            context.getApplicationContext().unbindService(mServiceConnection);
            Log.d("service cycle", "UNbound service in onServiceConnected PERSISTENCEReceiver");

            StepDetectionServiceHelper.stopAllIfNotRequired(false, context);
            WidgetReceiver.forceWidgetUpdate(context);
            if(mSaveListener != null) {
                mSaveListener.onSaveDone();
            }
        }
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(LOG_CLASS, "Storing the steps");
        this.context = context.getApplicationContext();
        if (intent.hasExtra(WalkingModePersistenceHelper.BROADCAST_EXTRA_OLD_WALKING_MODE)) {
            oldWalkingMode = WalkingModePersistenceHelper.getItem(intent.getLongExtra(WalkingModePersistenceHelper.BROADCAST_EXTRA_OLD_WALKING_MODE, -1), context);
        }
        if(oldWalkingMode == null){
            oldWalkingMode = WalkingModePersistenceHelper.getActiveMode(context);
        }
        // bind to service
        Intent serviceIntent = new Intent(context, Factory.getStepDetectorServiceClass(context));
        context.getApplicationContext().bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        Log.d("service cycle", "bound service in onReceive PERSISTENCEReceiver");

    }

    public interface ISaveListener {
        void onSaveDone();
    }

    private static ISaveListener mSaveListener = null;

    public static void registerSaveListener(ISaveListener listener) {
        mSaveListener = listener;
    }

    public static void unregisterSaveListener() {
        mSaveListener = null;
    }


}
