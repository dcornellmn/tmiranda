/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package tmiranda.mus;

import java.util.*;
import sagex.api.*;

/**
 *
 * @author Tom Miranda
 */
public class MultiObject {

    boolean             isValid         = true;
    Object              record          = null;
    private String      store           = null;
    boolean             isInitialized   = false;
    String              userID          = null;

    static final String INITIALIZED     = "IsInitialized";

    static final String REALWATCHEDSTARTTIME_PREFIX = "RealWatchedStartTime_";
    static final String REALWATCHEDENDTIME_PREFIX   = "RealWatchedEndTime_";
    static final String WATCHEDSTARTTIME_PREFIX     = "WatchedStartTime_";
    static final String WATCHEDENDTIME_PREFIX       = "WatchedEndTime_";

    public MultiObject(String UserID, String Store, Integer keyInt) {

        if (UserID==null || UserID.isEmpty() || Store==null || Store.isEmpty() || keyInt==null) {
            isValid = false;
            return;
        }

        userID = UserID;
        store = Store;

        String key = keyInt.toString();
        Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "MultiObject: Key is " + key);

        record = UserRecordAPI.GetUserRecord(store, key);

        if (record == null) {

            Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "MultiObject: Creating new userRecord.");

            record = UserRecordAPI.AddUserRecord(store, key);

            if (record==null) {
                Log.getInstance().write(Log.LOGLEVEL_ERROR, "MultiObject: Error creating userRecord for Store:Key " + store + ":" + key);
                isValid = false;
                return;
            }
        }

        String init = getRecordData(INITIALIZED);
        isInitialized = (init != null && init.equalsIgnoreCase("true"));
        return;
    }

    // Returns the specified user record data.
    String getRecordData(String Flag) {
        return UserRecordAPI.GetUserRecordData(record, Flag);
    }
    
    // Sets the specified user record data.
    void setRecordData(String Flag, String Data) {       
        UserRecordAPI.SetUserRecordData(record, Flag, Data);
        return;
    }

    // Adds Data to the Flag.
    DelimitedString addFlag(String Flag, String Data) {
        Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "addFlag: Adding " + Data + " to " + Flag);
        DelimitedString DS = new DelimitedString(getRecordData(Flag), Plugin.LIST_SEPARATOR);
        DS.addUniqueElement(Data);
        setRecordData(Flag, DS.toString());
        return DS;
    }

    // Removes Data from the Flag.
    DelimitedString removeFlag(String Flag, String Data) {
        Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "removeFlag: Removing " + Data + " from " + Flag);
        DelimitedString DS = new DelimitedString(getRecordData(Flag), Plugin.LIST_SEPARATOR);
        DS.removeElement(Data);
        setRecordData(Flag, DS.toString());
        return DS;
    }

    // True if the Flag contains Data.
    boolean containsFlag(String Flag, String User) {
        DelimitedString DS = new DelimitedString(getRecordData(Flag), Plugin.LIST_SEPARATOR);
        return (DS.contains(User));
    }

    boolean containsFlagAnyData(String Flag) {
        DelimitedString DS = new DelimitedString(getRecordData(Flag), Plugin.LIST_SEPARATOR);
        return (DS!=null && !DS.isEmpty());
    }

    boolean containsFlagAllUsers(String Flag) {
        List<String> allUsers = User.getAllUsers();
        DelimitedString DS = new DelimitedString(getRecordData(Flag), Plugin.LIST_SEPARATOR);
        allUsers.remove(Plugin.SUPER_USER);
        return (DS.containsAll(allUsers));
    }

    // Removes all Records from the DataStore.
    void wipeDatabase() {

        Object[] AllUserRecords = UserRecordAPI.GetAllUserRecords(store);
        Log.getInstance().write(Log.LOGLEVEL_WARN, "wipeDatabase: Begin wipe of Store " + AllUserRecords.length);
        for (Object Record : AllUserRecords)
            UserRecordAPI.DeleteUserRecord(Record);
        Log.getInstance().write(Log.LOGLEVEL_WARN, "wipeDatabase: DataStore wiped.");
    }

    // Removes the specified User from the Flags.
    void clearUser(String User, String[] Flags) {

        for (String Flag : Flags) {
            removeFlag(Flag, User);
        }

        addFlag(INITIALIZED, "false");
        return;
    }


    long getRealWatchedStartTime() {
        String D = getRecordData(REALWATCHEDSTARTTIME_PREFIX + userID);

        try {
            long duration = Long.parseLong(D);
            return duration;
        } catch (NumberFormatException e) {
            Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getRealWatchedStartTime: Bad number " + D);
            return -1;
        }
    }

    void setRealWatchedStartTime(String Time) {
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "setRealWatchedStartTime: Setting to " + Time);
        setRecordData(REALWATCHEDSTARTTIME_PREFIX + userID, Time);
    }

    void setRealWatchedStartTime(long Time) {
        Long T = Time;
        setRealWatchedStartTime(T.toString());
    }

    void clearRealWatchedStartTime() {
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "clearRealWatchedStartTime: Setting to null.");
        setRecordData(REALWATCHEDSTARTTIME_PREFIX + userID, null);
    }


    long getRealWatchedEndTime() {
        String D = getRecordData(REALWATCHEDENDTIME_PREFIX + userID);

        try {
            long duration = Long.parseLong(D);
            return duration;
        } catch (NumberFormatException e) {
            Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getRealWatchedEndTime: Bad number " + D);
            return -1;
        }
    }

    void setRealWatchedEndTime(String Time) {
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "setRealWatchedEndTime: Setting to " + Time);
        setRecordData(REALWATCHEDENDTIME_PREFIX + userID, Time);
    }

    void setRealWatchedEndTime(long Time) {
        Long T = Time;
        setRealWatchedEndTime(T.toString());
    }

    void clearRealWatchedEndTime() {
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "clearRealWatchedStartTime: Setting to null.");
        setRecordData(REALWATCHEDENDTIME_PREFIX + userID, null);
    }


    long getWatchedStartTime() {
        String D = getRecordData(WATCHEDSTARTTIME_PREFIX + userID);

        try {
            long duration = Long.parseLong(D);
            return duration;
        } catch (NumberFormatException e) {
            Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getWatchedStartTime: Bad number " + D);
            return -1;
        }
    }

    void setWatchedStartTime(String Time) {
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "setWatchedStartTime: Setting to " + Time);
        setRecordData(WATCHEDSTARTTIME_PREFIX + userID, Time);
    }

    void setWatchedStartTime(long Time) {
        Long T = Time;
        setWatchedStartTime(T.toString());
    }

    void clearWatchedStartTime() {
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "clearWatchedStartTime: Setting to null.");
        setRecordData(WATCHEDSTARTTIME_PREFIX + userID, null);
    }


    long getWatchedEndTime() {
        String D = getRecordData(WATCHEDENDTIME_PREFIX + userID);
System.out.println("GET WATCHED END TIME:: " + D);
        try {
            long duration = Long.parseLong(D);
            return duration;
        } catch (NumberFormatException e) {
System.out.println("GET WATCHED END TIME:: UNINITIALIZED");
            Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getWatchedEndTime: Bad number " + D);
            return -1;
        }
    }

    void setWatchedEndTime(String Time) {
System.out.println("SET WATCHED END TIME:: " + Time);
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "setWatchedEndTime: Setting to " + Time);
        setRecordData(WATCHEDENDTIME_PREFIX + userID, Time);
    }

    void setWatchedEndTime(long Time) {
        Long T = Time;
        setWatchedEndTime(T.toString());
    }

    void clearWatchedEndTime() {
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "clearWatchedEndTime: Setting to null.");
        setRecordData(WATCHEDENDTIME_PREFIX + userID, null);
    }


    // Used for debugging.
    String getFlagString(String Flag) {
        return getRecordData(Flag);
    }
}