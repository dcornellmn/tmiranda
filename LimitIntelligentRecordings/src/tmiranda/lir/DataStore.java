/*
 * Database access methods.
 */

package tmiranda.lir;

import java.util.*;
import sagex.api.*;

/**
 *
 * @author Tom Miranda.
 */
public class DataStore {

    // The UserRecord datastore.  The Key will be the Airing title.
    public static final String STORE = "tmiranda.lir";

    //
    // User Record data fields.
    //

    // The maximum number of shows to keep.
    public static final String MAX_ALLOWED = "MaxAllowed";

    // The record key, since there is no way to get it from the UserRecordAPI.
    public static final String KEY = "Key";

    private Object record = null;

    /**
     * Constructor.
     *
     * By default the constructor will NOT create an empty UserRecord. This is done to
     * avoid filling up the database with a UserRecord for every Airing title that
     * is queried.  Use the static method addRecord() to explicitly add a UserRecord.
     *
     * @param MediaFile
     */
    public DataStore(Object MediaFile) {
        if (MediaFile==null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "DataStore: null MediaFile.");
            record = null;
            return;
        }

        String key = AiringAPI.GetAiringTitle(MediaFile);
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "DataStore: key = " + key);
        record = UserRecordAPI.GetUserRecord(STORE, key);
    }

    public DataStore(String Title) {
        if (Title==null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "DataStore: null Title.");
            record = null;
            return;
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "DataStore: key = " + Title);
        record = UserRecordAPI.GetUserRecord(STORE, Title);
    }

    public Object getRecord() {
        return record;
    }

    public boolean hasRecord() {
        return record != null;
    }

    public boolean addRecord(String key) {
        if (key == null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "DataStore.addRecord: null key.");
            return false;
        }

        Object newRecord;

        newRecord = UserRecordAPI.GetUserRecord(STORE, key);
        if (newRecord != null) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "DataStore.addRecord: Record already existed.");
            return true;
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "DataStore.addRecord: key = " + key);
        newRecord = UserRecordAPI.AddUserRecord(STORE, key);

        if (newRecord==null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "DataStore.addRecord: Could not add recird for " + key);
            return false;
        }

        // Set the key.
        UserRecordAPI.SetUserRecordData(newRecord, KEY, key);
        record = newRecord;
        return true;
    }
    
    /**
     * Return all of the keys (Airing titles) in the datastore.
     * @return
     */
    public static List<String> getAllKeys() {
        
        List<String> allKeys = new ArrayList<String>();
        
        Object records[] = UserRecordAPI.GetAllUserRecords(STORE);
        
        if (records==null || records.length==0)
            return allKeys;
        
        for (Object record : records) {
            String thisKey = UserRecordAPI.GetUserRecordData(record, KEY);
            if (thisKey!=null && thisKey.length()>0) {
                allKeys.add(thisKey);
            }
        }
        
        return allKeys;
    }

    public String getKey() {
        return UserRecordAPI.GetUserRecordData(record, KEY);
    }

    public boolean deleteRecord() {
        if (UserRecordAPI.DeleteUserRecord(record)) {
            record = null;
            return true;
        } else {
            return false;
        }
    }

    public boolean isMonitored() {
        return record != null;
    }

    /**
     * Get the maximum number of recordings to keep.
     * @return The maximum number to keep, or 0 for unlimited.
     */
    public int getMax() {
        if (record==null) {
            Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "DataStore.getMax: null record.");
            return Plugin.UNLIMITED;
        }

        String maxString = UserRecordAPI.GetUserRecordData(record, MAX_ALLOWED);

        if (maxString==null || maxString.length()==0) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "DataStore.getMax: null maxString.");
            return Plugin.UNLIMITED;
        }

        try {
            return Integer.parseInt(maxString);
        } catch (NumberFormatException e) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "DataStore.getMax: Format error " + maxString);
            return Plugin.UNLIMITED;
        }
    }

    public boolean setMax(String max) {
        if (record==null) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "DataStore.setMax: null record.");
            return false;
        }

        UserRecordAPI.SetUserRecordData(record, MAX_ALLOWED, max);
        return true;
    }

    public String getMaxString() {
        if (record==null) {
            Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "DataStore.getMax: null record.");
            return Plugin.DEFAULT_MAX_STRING;
        }

        String maxString = UserRecordAPI.GetUserRecordData(record, MAX_ALLOWED);

        if (maxString==null || maxString.length()==0) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "DataStore.getMax: null maxString.");
            return Plugin.DEFAULT_MAX_STRING;
        } else {
            return maxString;
        }
    }
}