package com.example.clonepedometer

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.clonepedometer.databinding.ActivityMainBinding
import com.example.clonepedometer.models.ActivityChartDataSet
import com.example.clonepedometer.models.ActivityDayChart
import com.example.clonepedometer.models.ActivitySummary
import com.example.clonepedometer.models.StepCount
import com.example.clonepedometer.models.WalkingMode
import com.example.clonepedometer.persistence.StepCountPersistenceHelper
import com.example.clonepedometer.persistence.WalkingModePersistenceHelper
import com.example.clonepedometer.services.AbstractStepDetectorService
import com.example.clonepedometer.services.MovementSpeedService
import com.example.clonepedometer.utils.StepDetectionServiceHelper
import com.example.clonepedometer.utils.UnitHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private val broadcastReceiver: BroadcastReceiver = BroadcastReceiver()
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            myBinder = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            myBinder = service as AbstractStepDetectorService.StepDetectorBinder
            updateData()
            updateView()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding =  ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filterRefreshUpdate = IntentFilter()
        filterRefreshUpdate.addAction(StepCountPersistenceHelper.BROADCAST_ACTION_STEPS_SAVED)
        filterRefreshUpdate.addAction(AbstractStepDetectorService.BROADCAST_ACTION_STEPS_DETECTED)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, filterRefreshUpdate)

        // Bind to stepDetector
        // Bind to stepDetector
        val serviceIntent = Intent(this, Factory.getStepDetectorServiceClass(this))
        applicationContext.bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE)

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        start_timestamp =
            sharedPref.getLong(getString(R.string.pref_distance_measurement_start_timestamp), -1)
        if (start_timestamp!! < 0) {
            start_timestamp = null
        }
        binding.startButton.setOnClickListener{
            start_after_storing_steps = true
            // Start persistence service and wait for it before start counting steps
            // Start persistence service and wait for it before start counting steps
            StepDetectionServiceHelper.startPersistenceService(this)
        }
        binding.stopButton.setOnClickListener {
            stopDistanceMeasurement()
        }

        getStepCounts()
        updateData()
        updateView()
    }

    companion object {
        private lateinit var binding : ActivityMainBinding
        val LOG_CLASS: String = MainActivity::class.java.getName()
        private val menuWalkingModes: Map<Int, WalkingMode>? = null
        private var stepCounts: List<StepCount>? = null
        private var start_after_storing_steps = false
        var start_timestamp: Long? = null
        private var distance = 0.0
        private var myBinder: AbstractStepDetectorService.StepDetectorBinder? = null
        protected fun getStepCounts() {
            if (start_timestamp == null) {
                return
            }
            stepCounts = StepCountPersistenceHelper.getStepCountsForInterval(
                start_timestamp!!,
                Calendar.getInstance().getTimeInMillis(),
                binding.root.context
            )
        }
        /**
         * Updates the data, gets the current step counts from step detector service.
         * It summaries the step data in stepCount, distance and calories.
         */
        protected fun updateData() {
            if (start_timestamp == null) {
                return
            }
            val stepCounts: MutableList<StepCount?> = ArrayList<StepCount?>(
                stepCounts
            )
            // Add the steps which are not in database.
            if (myBinder != null) {
                val s = StepCount()
                if (stepCounts.size > 0) {
                    s.startTime = stepCounts[stepCounts.size - 1]!!.endTime
                } else {
                    s.startTime = start_timestamp!!
                }
                s.endTime = Calendar.getInstance().getTimeInMillis() // now
                s.stepCount = myBinder!!.stepsSinceLastSave()
                s.walkingMode =
                    WalkingModePersistenceHelper.getActiveMode(binding.root.context) // add current walking mode
                stepCounts.add(s)
            }
            var distance = 0.0
            for (s in stepCounts) {
                distance += s!!.distance
            }
            this.distance = distance
        }

        /**
         * Updates the users view to current distance measurement.
         */
        protected fun updateView() {
            if (start_after_storing_steps || start_timestamp != null) {
                binding.startButton.visibility = View.GONE
                binding.stopButton.visibility = View.VISIBLE
            } else {
                binding.startButton.visibility = View.VISIBLE
                binding.stopButton.visibility = View.GONE
            }
            val distances: UnitHelper.FormattedUnitPair = UnitHelper.formatKilometers(
                UnitHelper.metersToKilometers(
                    distance
                ), binding.root.context
            )
            binding.distance.setText(distance.toString())
            binding.distanceTitle.setText(distances.getUnit())
        }

        /**
         * Stops the distance measurement.
         */
        protected fun stopDistanceMeasurement() {
            updateData()
            start_timestamp = null
            start_after_storing_steps = false
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(binding.root.context)
            val editor = sharedPref.edit()
            editor.putLong(binding.root.context.getString(R.string.pref_distance_measurement_start_timestamp), -1)
            editor.apply()
            StepDetectionServiceHelper.stopAllIfNotRequired(binding.root.context)
            updateView()
        }
    }

    override fun onResume() {
        super.onResume()
        val filterRefreshUpdate = IntentFilter()
        filterRefreshUpdate.addAction(StepCountPersistenceHelper.BROADCAST_ACTION_STEPS_SAVED)
        filterRefreshUpdate.addAction(AbstractStepDetectorService.BROADCAST_ACTION_STEPS_DETECTED)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, filterRefreshUpdate)
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        start_timestamp =
            sharedPref.getLong(getString(R.string.pref_distance_measurement_start_timestamp), -1)
        if (start_timestamp!! < 0) {
            start_timestamp = null
        }
        if (start_timestamp != null && start_timestamp!! > 0) {
            val serviceIntent = Intent(this, Factory.getStepDetectorServiceClass(this))
            applicationContext.bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE)
        }
        // Force refresh of view.
        getStepCounts()
        updateView()
    }

    override fun onPause() {
        if (mServiceConnection != null && myBinder != null && myBinder!!.isBinderAlive) {
            applicationContext.unbindService(mServiceConnection)
            myBinder = null
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        if (mServiceConnection != null && myBinder != null && myBinder!!.isBinderAlive) {
            applicationContext.unbindService(mServiceConnection)
            myBinder = null
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    class BroadcastReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent == null) {
                Log.w(LOG_CLASS,
                    "Received intent which is null."
                )
                return
            }
            when (intent.action) {
                AbstractStepDetectorService.BROADCAST_ACTION_STEPS_DETECTED -> {
                    updateData()
                    updateView()
                }

                StepCountPersistenceHelper.BROADCAST_ACTION_STEPS_SAVED -> {
                    if (start_timestamp == null && start_after_storing_steps) {
                        start_timestamp = Calendar.getInstance().time.time
                        start_after_storing_steps = false
                        distance = 0.0
                        val sharedPref =
                            PreferenceManager.getDefaultSharedPreferences(binding.root.context.applicationContext)
                        val editor = sharedPref.edit()
                        editor.putLong(
                            binding.root.context.getString(R.string.pref_distance_measurement_start_timestamp),
                            start_timestamp!!
                        )
                        editor.apply()
                        StepDetectionServiceHelper.startAllIfEnabled(binding.root.context.applicationContext)
                    }
                    getStepCounts()
                    updateData()
                    updateView()
                }

                WalkingModePersistenceHelper.BROADCAST_ACTION_WALKING_MODE_CHANGED -> {
                    getStepCounts()
                    updateData()
                    updateView()
                }

                else -> {}
            }
        }
    }

}

//class MainActivity : AppCompatActivity(), OnSharedPreferenceChangeListener {
//    private lateinit var binding : ActivityMainBinding
//    var LOG_TAG: String = MainActivity::class.java.getName()
//    private val broadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            if (intent == null) {
//                Log.w(
//                    LOG_TAG,
//                    "Received intent which is null."
//                )
//                return
//            }
//            when (intent.action) {
//                AbstractStepDetectorService.BROADCAST_ACTION_STEPS_DETECTED, StepCountPersistenceHelper.BROADCAST_ACTION_STEPS_SAVED, WalkingModePersistenceHelper.BROADCAST_ACTION_WALKING_MODE_CHANGED, MovementSpeedService.BROADCAST_ACTION_SPEED_CHANGED ->                     // Steps were saved, reload step count from database
//                    generateReports(true)
//
//                StepCountPersistenceHelper.BROADCAST_ACTION_STEPS_INSERTED, StepCountPersistenceHelper.BROADCAST_ACTION_STEPS_UPDATED -> generateReports(
//                    false
//                )
//
//                else -> {}
//            }
//        }
//
//    }
//    //        private val mAdapter: ReportAdapter? = null
////        private val mRecyclerView: RecyclerView? = null
////        private val mListener: com.example.clonepedometer.fragments.DailyReportFragment.OnFragmentInteractionListener? =
////            null
//    private var activitySummary: ActivitySummary? = null
//    private var activityChart: ActivityDayChart? = null
//    private var reports: ArrayList<Any> = ArrayList()
//    private var day: Calendar? = Calendar.getInstance()
//    private var generatingReports = false
//    private val menuWalkingModes: Map<Int, WalkingMode>? = null
//    private val menuCorrectStepId = 0
//    private var myBinder: AbstractStepDetectorService.StepDetectorBinder? = null
//    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
//        override fun onServiceDisconnected(name: ComponentName) {
//            myBinder = null
//        }
//
//        override fun onServiceConnected(name: ComponentName, service: IBinder) {
//            myBinder = service as AbstractStepDetectorService.StepDetectorBinder
//            generateReports(true)
//        }
//    }
//    private var movementSpeedBinder: MovementSpeedService.MovementSpeedBinder? = null
//    private val mMovementSpeedServiceConnection: ServiceConnection =
//        object : ServiceConnection {
//            override fun onServiceDisconnected(name: ComponentName) {
//                movementSpeedBinder = null
//            }
//
//            override fun onServiceConnected(name: ComponentName, service: IBinder) {
//                movementSpeedBinder = service as MovementSpeedService.MovementSpeedBinder
//                generateReports(true)
//            }
//        }
//    private fun isTodayShown(): Boolean {
////            return Calendar.getInstance()[Calendar.YEAR] == day!![Calendar.YEAR] && Calendar.getInstance()[Calendar.MONTH] == day!![Calendar.MONTH] && Calendar.getInstance()[Calendar.DAY_OF_MONTH] == day!![Calendar.DAY_OF_MONTH]
//        return true
//    }
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding =  ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//        /*if (getArguments() != null) {
//           mParam1 = getArguments().getString(ARG_PARAM1);
//        }*/
//        // register for steps-saved-event
//        day = Calendar.getInstance()
//        registerReceivers()
//        // Bind to stepDetector if today is shown
//        // Bind to stepDetector if today is shown
//        if (isTodayShown() && StepDetectionServiceHelper.isStepDetectionEnabled(baseContext)) {
//            bindService()
//            Log.d("service cycle", "bound service in onCreate DailyFragment")
//        }
//    }
//
//
//    override fun onResume() {
//        super.onResume()
//        if (day == null) {
//            day = Calendar.getInstance()
//        }
//        if (day!!.getTimeZone() != TimeZone.getDefault()) {
//            day = Calendar.getInstance()
//            generateReports(true)
//        }
//        if (isTodayShown() && StepDetectionServiceHelper.isStepDetectionEnabled(baseContext)) {
//            bindService()
//            Log.d("service cycle", "bound service in onResume DailyFragment")
//        }
//        registerReceivers()
//        bindMovementSpeedService()
//    }
//
//    public override fun onPause() {
//        unbindService()
//        Log.d("service cycle", "UNbound service in onPause DailyFragment")
//        unbindMovementSpeedService()
//        unregisterReceivers()
//        super.onPause()
//    }
//
//    public override fun onDestroy() {
//        unbindService()
//        Log.d("service cycle", "UNbound service in onDestroy DailyFragment")
//        unbindMovementSpeedService()
//        unregisterReceivers()
//        super.onDestroy()
//    }
//
//    private fun unregisterReceivers() {
//        LocalBroadcastManager.getInstance(baseContext).unregisterReceiver(broadcastReceiver)
//        val sharedPref = PreferenceManager.getDefaultSharedPreferences(baseContext)
//        sharedPref.unregisterOnSharedPreferenceChangeListener(this)
//    }
//
//    private fun bindMovementSpeedService() {
//        val sharedPref = PreferenceManager.getDefaultSharedPreferences(baseContext)
//        val isVelocityEnabled = sharedPref.getBoolean(getString(R.string.pref_show_velocity), false)
//        if (movementSpeedBinder == null && isVelocityEnabled) {
//            val serviceIntent = Intent(
//                baseContext,
//                MovementSpeedService::class.java
//            )
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                baseContext.startForegroundService(serviceIntent)
//            } else {
//                baseContext.startService(serviceIntent)
//            }
//            baseContext.applicationContext
//                .bindService(serviceIntent, mMovementSpeedServiceConnection, BIND_AUTO_CREATE)
//        }
//    }
//    private fun unbindMovementSpeedService() {
//        if (movementSpeedBinder != null && mMovementSpeedServiceConnection != null && movementSpeedBinder!!.service != null) {
//            baseContext.getApplicationContext().unbindService(mMovementSpeedServiceConnection)
//            movementSpeedBinder = null
//        }
//        val serviceIntent = Intent(
//            baseContext,
//            MovementSpeedService::class.java
//        )
//        baseContext.applicationContext.stopService(serviceIntent)
//    }
//    private fun unbindService() {
//        if (isTodayShown() && mServiceConnection != null && myBinder != null && myBinder!!.service != null) {
//            baseContext.applicationContext.unbindService(mServiceConnection)
//            myBinder = null
//        }
//    }
//    private fun bindService() {
//        if (myBinder == null) {
//            val serviceIntent =
//                Intent(baseContext, Factory.getStepDetectorServiceClass(baseContext))
//            baseContext.applicationContext
//                .bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE)
//        }
//        StepDetectionServiceHelper.startAllIfEnabled(true, baseContext)
//    }
//    private fun registerReceivers() {
//        // subscribe to onStepsSaved and onStepsDetected broadcasts and onSpeedChanged
//        val filterRefreshUpdate = IntentFilter()
//        filterRefreshUpdate.addAction(StepCountPersistenceHelper.BROADCAST_ACTION_STEPS_SAVED)
//        filterRefreshUpdate.addAction(AbstractStepDetectorService.BROADCAST_ACTION_STEPS_DETECTED)
//        filterRefreshUpdate.addAction(MovementSpeedService.BROADCAST_ACTION_SPEED_CHANGED)
//        filterRefreshUpdate.addAction(StepCountPersistenceHelper.BROADCAST_ACTION_STEPS_INSERTED)
//        filterRefreshUpdate.addAction(StepCountPersistenceHelper.BROADCAST_ACTION_STEPS_UPDATED)
//        LocalBroadcastManager.getInstance(baseContext).registerReceiver(broadcastReceiver, filterRefreshUpdate)
//        val sharedPref = PreferenceManager.getDefaultSharedPreferences(baseContext)
//        sharedPref.registerOnSharedPreferenceChangeListener(this)
//    }
//
//
//    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
//        if (key == getString(R.string.pref_step_counter_enabled) || key == getString(R.string.pref_use_step_hardware)) {
//            if (!StepDetectionServiceHelper.isStepDetectionEnabled(baseContext)) {
//                unbindService()
//                Log.d("servicecycle", "UNbound service in PREFERENCEchanged DailyFragment")
//            } else if (isTodayShown()) {
//                bindService()
//                Log.d("servicecycle", "bound service in PREFERENCEchanged DailyFragment")
//            }
//        }
//    }
//
//
//    fun generateReports(updated: Boolean) {
//            Log.i(
//                LOG_TAG,
//                "Generating reports"
//            )
//            if (!isTodayShown() && updated || baseContext == null || generatingReports) {
//                // the day shown is not today or is detached
//                return
//            }
//            generatingReports = true
//            // Get all step counts for this day.
//            val context: Context = baseContext.applicationContext
//            val locale = context.resources.configuration.locale
//            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
//            val stepCounts: MutableList<StepCount> = java.util.ArrayList<StepCount>()
//            val formatHourMinute = SimpleDateFormat("HH:mm", locale)
//            val m = day!!
//            m[Calendar.HOUR_OF_DAY] = 0
//            m[Calendar.MINUTE] = 0
//            m[Calendar.SECOND] = 0
//            var s = StepCount()
//            s.setStartTime(m.getTimeInMillis())
//            s.setEndTime(m.getTimeInMillis()) // one hour more
//            s.setStepCount(0)
//            s.setWalkingMode(null)
//            stepCounts.add(s)
//            var previousStepCount: StepCount = s
//            for (h in 0..23) {
//                m[Calendar.HOUR_OF_DAY] = h
//                s = StepCount()
//                s.setStartTime(m.getTimeInMillis() + 1000)
//                if (h != 23) {
//                    s.setEndTime(m.getTimeInMillis() + 3600000) // one hour more
//                } else {
//                    s.setEndTime(m.getTimeInMillis() + 3599000) // one hour more - 1sec
//                }
//                s.setWalkingMode(previousStepCount.getWalkingMode())
//                previousStepCount = s
//                // load step counts in interval [s.getStartTime(), s.getEndTime()] from database
//                val stepCountsFromStorage: List<StepCount> =
//                    StepCountPersistenceHelper.getStepCountsForInterval(
//                        s.getStartTime(),
//                        s.getEndTime(),
//                        context
//                    )
//                // add non-saved steps if today
//                if (isTodayShown() && s.getStartTime() < Calendar.getInstance()
//                        .getTimeInMillis() && s.getEndTime() >= Calendar.getInstance()
//                        .getTimeInMillis() && myBinder != null
//                ) {
//                    // Today is shown. Add the steps which are not in database.
//                    val s1 = StepCount()
//                    if (stepCountsFromStorage.size > 0) {
//                        s1.setStartTime(stepCountsFromStorage[stepCountsFromStorage.size - 1].getEndTime())
//                    } else {
//                        s1.setStartTime(s.getStartTime())
//                    }
//                    s1.setEndTime(Calendar.getInstance().getTimeInMillis()) // now
//                    s1.setStepCount(myBinder!!.stepsSinceLastSave())
//                    s1.setWalkingMode(WalkingModePersistenceHelper.getActiveMode(context)) // add current walking mode
//                    stepCounts.add(s1)
//                }
//                // iterate over stepcounts in interval to sum up steps and detect changes in walkingmode
//                for (stepCountFromStorage in stepCountsFromStorage) {
//                    if (previousStepCount.getWalkingMode() == null || !previousStepCount.getWalkingMode()
//                            .equals(stepCountFromStorage.getWalkingMode())
//                    ) {
//                        // we have to create a new stepcount entry.
//                        val oldEndTime: Long = s.getEndTime()
//                        s.setEndTime(stepCountFromStorage.getStartTime() - 1000)
//                        stepCounts.add(s)
//                        previousStepCount = s
//                        if (previousStepCount.getWalkingMode() == null) {
//                            for (s1 in stepCounts) {
//                                s1.setWalkingMode(stepCountFromStorage.getWalkingMode())
//                            }
//                            previousStepCount.setWalkingMode(stepCountFromStorage.getWalkingMode())
//                        }
//                        // create new stepcount.
//                        s = StepCount()
//                        s.setStartTime(stepCountFromStorage.getStartTime())
//                        s.setEndTime(oldEndTime)
//                        s.setStepCount(stepCountFromStorage.getStepCount())
//                        s.setWalkingMode(stepCountFromStorage.getWalkingMode())
//                    } else {
//                        s.setStepCount(s.getStepCount() + stepCountFromStorage.getStepCount())
//                    }
//                }
//                stepCounts.add(s)
//            }
//            // fill chart data arrays
//            var stepCount = 0.0
//            var distance = 0.0
//            var calories = 0.0
//            val stepData: MutableMap<String, ActivityChartDataSet?> =
//                LinkedHashMap<String, ActivityChartDataSet?>()
//            val distanceData: MutableMap<String, ActivityChartDataSet?> =
//                LinkedHashMap<String, ActivityChartDataSet?>()
//            val caloriesData: MutableMap<String, ActivityChartDataSet?> =
//                LinkedHashMap<String, ActivityChartDataSet?>()
//            for (s1 in stepCounts) {
//                stepCount += s1.getStepCount()
//                distance += s1.getDistance()
//                calories += s1.getCalories(context)
//                if (!stepData.containsKey(formatHourMinute.format(s1.getEndTime())) ||
//                    stepData[formatHourMinute.format(s1.getEndTime())]?.getStepCount()
//                        ?.getStepCount()!! < stepCount
//                ) {
//                    if (s1.getEndTime() > Calendar.getInstance().time.time) {
//                        stepData[formatHourMinute.format(s1.getEndTime())] = null
//                        distanceData[formatHourMinute.format(s1.getEndTime())] = null
//                        caloriesData[formatHourMinute.format(s1.getEndTime())] = null
//                    } else {
//                        stepData[formatHourMinute.format(s1.getEndTime())] =
//                            ActivityChartDataSet(stepCount, s1)
//                        distanceData[formatHourMinute.format(s1.getEndTime())] =
//                            ActivityChartDataSet(distance, s1)
//                        caloriesData[formatHourMinute.format(s1.getEndTime())] =
//                            ActivityChartDataSet(calories, s1)
//                    }
//                } else {
//                    Log.i(LOG_TAG,
//                        "Skipping put operation"
//                    )
//                }
//            }
//            val simpleDateFormat = SimpleDateFormat("dd. MMMM", locale)
//
//            // create view models
//            if (activitySummary == null) {
//                activitySummary =
//                    ActivitySummary(stepCount.toInt(), distance, calories.toInt(), simpleDateFormat.format(day!!.time))
//                reports.add(activitySummary!!)
//            } else {
//                activitySummary!!.setSteps(stepCount.toInt())
//                activitySummary!!.setDistance(distance)
//                activitySummary!!.setCalories(calories.toInt())
//                activitySummary!!.setTitle(simpleDateFormat.format(day!!.time))
//                activitySummary!!.setHasSuccessor(!isTodayShown())
//                activitySummary!!.setHasPredecessor(
//                    StepCountPersistenceHelper.getDateOfFirstEntry(baseContext).before(
//                        day!!.time
//                    )
//                )
//                val isVelocityEnabled =
//                    sharedPref.getBoolean(baseContext.getString(R.string.pref_show_velocity), false)
//                if (movementSpeedBinder != null && isVelocityEnabled) {
//                    activitySummary!!.setCurrentSpeed(movementSpeedBinder!!.speed)
//                } else {
//                    activitySummary!!.setCurrentSpeed(null)
//                }
//            }
//            if (activityChart == null) {
//                activityChart = ActivityDayChart(
//                    stepData, distanceData, caloriesData, simpleDateFormat.format(
//                        day!!.time
//                    )
//                )
//                activityChart!!.setDisplayedDataType(ActivityDayChart.DataType.STEPS)
//                reports.add(activityChart!!)
//            } else {
//                activityChart!!.setSteps(stepData)
//                activityChart!!.setDistance(distanceData)
//                activityChart!!.setCalories(caloriesData)
//                activityChart!!.setTitle(simpleDateFormat.format(day!!.time))
//            }
//            val d = sharedPref.getString(context.getString(R.string.pref_daily_step_goal), "10000")
//            activityChart!!.setGoal(d!!.toInt())
//        Toast.makeText(context,"Step : $stepData",Toast.LENGTH_SHORT).show()
//            // notify ui
////        if (mAdapter != null && mRecyclerView != null && getActivity() != null) {
////            getActivity().runOnUiThread(Runnable {
////                if (!mRecyclerView.isComputingLayout()) {
////                    mAdapter.notifyDataSetChanged()
////                } else {
////                    Log.w(LOG_TAG,
////                        "Cannot inform adapter for changes - RecyclerView is computing layout."
////                    )
////                }
////            })
////        } else {
////            Log.w(LOG_TAG,
////                "Cannot inform adapter for changes."
////            )
////        }
//            generatingReports = false
//        }
//
//
//
//
//
//
//}