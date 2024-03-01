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
package com.example.clonepedometer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import com.example.clonepedometer.services.AbstractStepDetectorService;
import com.example.clonepedometer.services.AccelerometerStepDetectorService;
import com.example.clonepedometer.services.HardwareStepService;
import com.example.clonepedometer.utils.AndroidVersionHelper;


/**
 * Factory class
 *
 * @author Tobias Neidig
 * @version 20160610
 */

public class Factory {

    /**
     * Returns the class of the step detector service which should be used
     *
     * The used step detector service depends on different soft- and hardware preferences.
     * @param context An instance of the calling Context
     * @return The class of step detector
     */
    public static Class<? extends AbstractStepDetectorService> getStepDetectorServiceClass(Context context){
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        PackageManager pm = context.getPackageManager();
        if(pm != null && AndroidVersionHelper.supportsStepDetector(pm) && sharedPref.getBoolean(context.getString(R.string.pref_use_step_hardware), false)) {
            return HardwareStepService.class;
        }else{
            return AccelerometerStepDetectorService.class;
        }
    }
}
