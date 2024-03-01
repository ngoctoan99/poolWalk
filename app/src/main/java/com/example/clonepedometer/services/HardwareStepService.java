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
package com.example.clonepedometer.services;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

import androidx.annotation.NonNull;

/**
 * Uses the hardware step counter sensor to detect steps.
 * Publishes the detected steps to any subscriber.
 *
 * @author Tobias Neidig
 * @version 20160522
 */

public class HardwareStepService extends AbstractStepDetectorService {
    /**
     * Number of steps which the user went today
     * This is used when step counter is used.
     */
    private float mStepOffset = -1;

    /**
     * Creates an HardwareStepDetectorService.
     *
     */
    public HardwareStepService() {
        super();
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {}

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_STEP_DETECTOR:
                this.onStepDetected(1);
                break;
            case Sensor.TYPE_STEP_COUNTER:
                if (this.mStepOffset < 0) {
                    this.mStepOffset = event.values[0];
                }
                if (this.mStepOffset > event.values[0]) {
                    // this should never happen?
                    return;
                }
                // publish difference between last known step count and the current ones.
                this.onStepDetected((int) (event.values[0] - mStepOffset));
                // Set offset to current value so we know it at next event
                mStepOffset = event.values[0];
                break;
        }
    }
}
