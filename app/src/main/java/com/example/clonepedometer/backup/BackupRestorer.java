package com.example.clonepedometer.backup;



import static com.example.clonepedometer.backup.PrefManager.PREF_NAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;


import com.example.clonepedometer.persistence.StepCountDbHelper;
import com.example.clonepedometer.persistence.TrainingDbHelper;
import com.example.clonepedometer.persistence.WalkingModeDbHelper;

import org.secuso.privacyfriendlybackup.api.backup.DatabaseUtil;
import org.secuso.privacyfriendlybackup.api.backup.FileUtil;
import org.secuso.privacyfriendlybackup.api.pfa.IBackupRestorer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BackupRestorer implements IBackupRestorer {

    private void readDatabase(@NonNull JsonReader reader, @NonNull Context context, String dbName) throws IOException {
        reader.beginObject();

        String n1 = reader.nextName();
        if (!n1.equals("version")) {
            throw new RuntimeException("Unknown value " + n1);
        }
        int version = reader.nextInt();

        String n2 = reader.nextName();
        if (!n2.equals("content")) {
            throw new RuntimeException("Unknown value " + n2);
        }
        DatabaseUtil.deleteRoomDatabase(context, "restoreDatabase");
        SupportSQLiteDatabase db = DatabaseUtil.getSupportSQLiteOpenHelper(context, "restoreDatabase", version).getWritableDatabase();
        db.beginTransaction();
        db.setVersion(version);

        DatabaseUtil.readDatabaseContent(reader, db);

        db.setTransactionSuccessful();
        db.endTransaction();
        db.close();

        reader.endObject();

        // copy file to correct location
        File databaseFile = context.getDatabasePath("restoreDatabase");
        File oldDBFile = context.getDatabasePath(dbName);
        oldDBFile.delete();
        FileUtil.copyFile(databaseFile, context.getDatabasePath(dbName));
        databaseFile.delete();
    }

    private void readDefaultPreferences(@NonNull JsonReader reader, @NonNull Context context) throws IOException {
        reader.beginObject();

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);

        while (reader.hasNext()) {
            String name = reader.nextName();

            switch (name) {
                case "com.example.clonepedometer.pref.step_counter_enabled":
                case "com.example.clonepedometer.pref.use_wake_lock":
                case "com.example.clonepedometer.pref.use_wake_lock_during_training":
                case "com.example.clonepedometer.pref.show_velocity":
                case "com.example.clonepedometer.pref.permanent_notification_show_steps":
                case "com.example.clonepedometer.pref.permanent_notification_show_distance":
                case "com.example.clonepedometer.pref.permanent_notification_show_calories":
                case "com.example.clonepedometer.pref.motivation_alert_enabled":

                    pref.edit().putBoolean(name, reader.nextBoolean()).apply();
                    break;

                case "com.example.clonepedometer.pref.unit_of_length":
                case "com.example.clonepedometer.pref.unit_of_energy":
                case "com.example.clonepedometer.pref.accelerometer_threshold":
                case "com.example.clonepedometer.pref.accelerometer_step_threshold":
                case "com.example.clonepedometer.pref.daily_step_goal":
                case "com.example.clonepedometer.pref.weight":
                case "com.example.clonepedometer.pref.gender":
                case "com.example.clonepedometer.pref.motivation_alert_criterion":

                    pref.edit().putString(name,reader.nextString() ).apply();
                    break;

                case "com.example.clonepedometer.pref.motivation_alert_time":
                case "com.example.clonepedometer.pref.distance_measurement_start_timestamp":

                    pref.edit().putLong(name, reader.nextLong()).apply();
                    break;

                case "com.example.clonepedometer.pref.motivation_alert_texts":

                    reader.beginArray();
                    List<String> alertTexts = new ArrayList<>();
                    while(reader.hasNext()) {
                        alertTexts.add(reader.nextString());
                    }
                    reader.endArray();
                    pref.edit().putStringSet("com.example.clonepedometer.pref.motivation_alert_texts", new HashSet<>(alertTexts)).apply();
                    break;

                default:
                    throw new RuntimeException("Unknown preference " + name);
            }
        }

        reader.endObject();
    }

    @Override
    public boolean restoreBackup(@NonNull Context context, @NonNull InputStream restoreData) {
        try {
            InputStreamReader isReader = new InputStreamReader(restoreData);
            JsonReader reader = new JsonReader(isReader);

            // START
            reader.beginObject();

            while (reader.hasNext()) {
                String type = reader.nextName();

                switch (type) {
                    case "database_stepCount":
                        readDatabase(reader, context, StepCountDbHelper.DATABASE_NAME);
                        break;
                    case "database_trainings":
                        readDatabase(reader, context, TrainingDbHelper.DATABASE_NAME);
                        break;
                    case "database_walkingMode":
                        readDatabase(reader, context, WalkingModeDbHelper.DATABASE_NAME);
                        break;
                    case "preferences":
                        readDefaultPreferences(reader, context);
                        break;
                    case "tutorial_preferences":
                        readTutorialPreferences(reader, context);
                        break;
                    default:
                        throw new RuntimeException("Can not parse type " + type);
                }

            }

            reader.endObject();

            StepCountDbHelper.invalidateReference();
            TrainingDbHelper.invalidateReference();
            WalkingModeDbHelper.invalidateReference();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void readTutorialPreferences(@NonNull JsonReader reader, @NonNull Context context) throws IOException {
        reader.beginObject();

        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, 0);

        while (reader.hasNext()) {
            String name = reader.nextName();

            if (name.equals("IsFirstTimeLaunch")) {
                pref.edit().putBoolean(name, reader.nextBoolean()).apply();
            }else{
                throw new RuntimeException("Unknown preference " + name);
            }
        }

        reader.endObject();
    }
}