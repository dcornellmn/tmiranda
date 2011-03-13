
package tmiranda.mus;

import java.util.*;
import java.io.*;
import sagex.UIContext;
import sagex.api.*;
import sagex.UIContext.*;

/**
 * This class presents methods that can be invoked from within the Sage STV to perform all
 * of the tasks necessary to implement the Multi-User Plugin.  Unless noted otherwise the
 * methods behave in the same way as the Sage core APIs.
 * @author Tom Miranda
 */
public class API {

    /**
     * All methods in this class are static.
     */
    private API() {}

    /*
     * User login and logout.
     *
     * General behavour:
     * - Admin user accesses underlying Sage core.
     * - null user access underlying Sage core, but can't do admin functions.
     */


    /**
     * Logs on the specified user.
     * @param UserID
     */
    @Deprecated
    public static void loginUser(String UserID) {
        
        if (UserID == null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "loginUser: null UserID.");
            return;
        }

        User user = new User(UserID);
        user.logOn();
    }

    /**
     * Logs out the current user.
     */
    @Deprecated
    public static void logoutCurrentUser() {

        if (getLoggedinUser()==null)
            return;

        User user = new User(getLoggedinUser());
        user.logOff();
    }

    /**
     * Returns the currently logged on user.
     * @return The currently logged on user.
     */
    @Deprecated
    public static String getLoggedinUser() {
        return SageUtil.getUIProperty(Plugin.PROPERTY_LAST_LOGGEDIN_USER, null);
    }

    /**
     * Returns the user that should be logged on after Sage is rebooted.  It may be null
     * indicating that no user should be logged on.
     * @return The user that should be logged in after the UI is reloaded.
     */
    @Deprecated
    public static String getUserAfterReboot() {
        if (SageUtil.GetLocalBoolProperty(Plugin.PROPERTY_LOGIN_LAST_USER, "false")) {
            String userID = getLoggedinUser();

            if (userID==null || userID.isEmpty())
                return null;

            User user = new User(userID);

            if (user.exists()) {
                return userID;
            } else {
                logoutCurrentUser();
                return null;
            }
        }  else
            return null;
    }


    /*
     * Admin methods.
     */

    /**
     * Checks to see if the multiusersupport General Plugin in installed.  The method name is
     * a bit misleading because it does NOT check to see if the Plugin is actually enabled or not.
     * @return true if the General Plugin is installed on the server, false otherwise.
     */
    public static boolean isPluginEnabled() {
        Object thisPlugin = PluginAPI.GetAvailablePluginForID(Plugin.PLUGIN_ID);

        if (thisPlugin==null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "isPluginEnabled: Error. null Plugin for ID " + Plugin.PLUGIN_ID);
            return false;
        }

        Object[] installedPlugins = PluginAPI.GetInstalledPlugins();

        if (installedPlugins==null || installedPlugins.length==0) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "isPluginEnabled: No Plugins are installed on the server.");
            return false;
        }

        for (Object plugin : installedPlugins)
            if (PluginAPI.GetPluginIdentifier(plugin).equalsIgnoreCase(Plugin.PLUGIN_ID))
                return true;

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "isPluginEnabled: Plugin is not installed on the server.");
        return false;
    }

    /**
     * Returns a String containing the Plugin version number.
     * @return The Plugin version.
     */
    public static String getPluginVersion() {
        return Plugin.VERSION;
    }

    /**
     * Checks to see if the Airing will be recorded for the current user.  It may be a
     * Favorite, Manual Record or an Intelligent Recording choice.
     * @param Airing
     * @return true if the currently logged on user has requested the Airing be recorded,
     * false otherwise.
     */
    public static boolean isUpcomingRecordingForMe(Object Airing) {

        // Null Airing or an Airing that won't be recorded is certainly not for the current user.
        if (Airing==null || !willBeRecordedByCore(ensureIsAiring(Airing)))
            return false;

        String User = getLoggedinUser();

        // It's for current user if it's a Favorite or a Manual.
        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER) || isFavorite(ensureIsAiring(Airing)) || isManualRecord(ensureIsAiring(Airing))) {
            //Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "isUpcomingRecordingForMe: null user or Admin " + User);
            return true;
        }

        // If IR is disabled it can't be for the current user.
        if (isIntelligentRecordingDisabled())
            return false;

        // If it's an IR it must be for this user.
        return isIntelligentRecord(ensureIsAiring(Airing));
    }

    /**
     * Checks if the specified Airing will be recorded by the SageTV core.
     * @param Airing
     * @return true if the Airing will be recorded by the Sage core, false otherwise.
     */
    public static boolean willBeRecordedByCore(Object Airing) {

        if (Airing==null)
            return false;

        int AiringID = sagex.api.AiringAPI.GetAiringID(ensureIsAiring(Airing));
        Object[] scheduledAirings = Global.GetScheduledRecordings();

        for (Object airing : scheduledAirings)
            if (sagex.api.MediaFileAPI.IsFileCurrentlyRecording(airing) || (AiringID == sagex.api.AiringAPI.GetAiringID(airing)))
                return true;

        return false;
    }

    /**
     * Checks to see if the Airing will be recorded for the User because it's a Favorite or
     * a Manual Record.  Does NOT return true for Intelligent Recording choices.
     * @param User
     * @param Airing
     * @return
     */
    static boolean isUpcomingRecordingForUser(String User, Object Airing) {
        if (!willBeRecordedByCore(ensureIsAiring(Airing))) {
            Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "isUpcomingRecordingForUser: Core will not record the Airing.");
            return false;
        }

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "isUpcomingRecordingForUser: null user or Admin " + User);
            return true;
        }

        // Check for a Favorite or a Manual.
        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        return (MA.isFavorite() || MA.isManualRecord());
    }

    /**
     * Returns a List of users that have the Airing defined as a Favorite or a Manual Record.
     * Does NOT include the Super-User (Admin).
     * @param Airing
     * @return A List of users that have requested the Airing be recorded.
     */
    public static List<String> getUsersThatWillRecord(Object Airing) {
        List<String> theList = new ArrayList<String>();

        if (Airing == null)
            return theList;

        List<String> allUsers = User.getAllUsers();

        for (String user : allUsers)
            if (!user.equalsIgnoreCase(Plugin.SUPER_USER) && isUpcomingRecordingForUser(user, ensureIsAiring(Airing)))
                theList.add(user);

        return theList;
    }

    /**
     * Checks to see if the Airing will be recorded because it's an Intelligent Recording choice.
     * @param Airing
     * @return
     */
    static boolean isIntelligentRecord(Object Airing) {

        // See if it will be recorded at all.
        if (!willBeRecordedByCore(ensureIsAiring(Airing)))
            return false;

        // If no users have this defined as a Favorite or Manual it's an IR.
        return getUsersThatWillRecord(ensureIsAiring(Airing)).isEmpty();
    }

    /*
     * MediaPlayer API.
     *
     * WatchLive() is only used in the setup menus so there is no need to implement that.
     */

    /**
     * Invoke this method just before invoking Watch() in the STV.  We can't invoke Watch
     * from this method directly because the STV relies on having the pre-defined variable
     * "this" set appropriately.
     * @param Content
     */
    @Deprecated
    public static Object watch(String ContextName, Object Content) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "preWatch: null user or Admin " + User);
            return sagex.api.MediaPlayerAPI.Watch(new UIContext(ContextName), Content);
        }

        // Set flag showing that this user is watching this content. We will need this later
        // when the RecordingStopped Event is received so we can set RealWatchedEndTime and
        // WatchedEndTime for the appropriate user.
        User user = new User(User);
        user.setWatching(Content);

        long WatchedEndTime = 0;
        long RealStartTime = 0;

        if (sagex.api.AiringAPI.IsAiringObject(Content)) {
            MultiAiring MA = new MultiAiring(User, Content);
            MA.setRealWatchedStartTime(Utility.Time());
            WatchedEndTime = MA.getWatchedEndTime();
            RealStartTime = MA.getRealWatchedStartTime();
        } else if (sagex.api.MediaFileAPI.IsMediaFileObject(Content)) {
            MultiMediaFile MMF = new MultiMediaFile(User, Content);
            MMF.setRealWatchedStartTime(Utility.Time());
            WatchedEndTime = MMF.getWatchedEndTime();
            RealStartTime = MMF.getRealWatchedStartTime();
        } else {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "preWatch: Not an Airing or MediaFile.");
            return sagex.api.MediaPlayerAPI.Watch(new UIContext(ContextName), Content);
        }

        WatchedEndTime = (WatchedEndTime==-1 ? sagex.api.AiringAPI.GetWatchedEndTime(Content):WatchedEndTime);
        RealStartTime = (RealStartTime==-1 ? sagex.api.AiringAPI.GetRealWatchedStartTime(Content):RealStartTime);
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "watch: Setting WatchedEndTime and RealStartTime " + Plugin.PrintDateAndTime(WatchedEndTime) + ":" + Plugin.PrintDateAndTime(RealStartTime));

        // Reset the values.
        sagex.api.AiringAPI.ClearWatched(Content);
        sagex.api.AiringAPI.SetWatchedTimes(Content, WatchedEndTime, RealStartTime);

        // Let the core do its thing.
        return sagex.api.MediaPlayerAPI.Watch(new UIContext(ContextName), Content);
    }

    @Deprecated
    public static void preWatch(Object Content) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "preWatch: null user or Admin " + User);
            return;
        }

        // Set flag showing that this user is watching this content. We will need this later
        // when the RecordingStopped Event is received so we can set RealWatchedEndTime and
        // WatchedEndTime for the appropriate user.
        User user = new User(User);
        user.setWatching(Content);

        long WatchedEndTime = 0;
        long RealStartTime = 0;

        if (sagex.api.AiringAPI.IsAiringObject(Content)) {
            MultiAiring MA = new MultiAiring(User, Content);
            MA.setRealWatchedStartTime(Utility.Time());
            WatchedEndTime = MA.getWatchedEndTime();
            RealStartTime = MA.getRealWatchedStartTime();
        } else if (sagex.api.MediaFileAPI.IsMediaFileObject(Content)) {
            MultiMediaFile MMF = new MultiMediaFile(User, Content);
            MMF.setRealWatchedStartTime(Utility.Time());
            WatchedEndTime = MMF.getWatchedEndTime();
            RealStartTime = MMF.getRealWatchedStartTime();
        } else {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "preWatch: Not an Airing or MediaFile.");
            return;
        }

        WatchedEndTime = (WatchedEndTime==-1 ? sagex.api.AiringAPI.GetWatchedEndTime(Content):WatchedEndTime);
        RealStartTime = (RealStartTime==-1 ? sagex.api.AiringAPI.GetRealWatchedStartTime(Content):RealStartTime);
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "watch: Setting WatchedEndTime and RealStartTime " + Plugin.PrintDateAndTime(WatchedEndTime) + ":" + Plugin.PrintDateAndTime(RealStartTime));

        // Reset the values.
        sagex.api.AiringAPI.ClearWatched(Content);
        sagex.api.AiringAPI.SetWatchedTimes(Content, WatchedEndTime, RealStartTime);

        // Let the core do its thing.
        return;
    }

    /*
     * MediaFile API.
     */

    @Deprecated
    public static Object getMediaFilesWithImportPrefix(Object Mask, String Prefix, boolean b1, boolean b2, boolean b3) {
        String user = getLoggedinUser();

        if (user==null || user.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return Database.GetMediaFilesWithImportPrefix(Mask, Prefix, b1, b2, b3);
        }

        List<Object> userMediaFiles = new ArrayList<Object>();

        Object mediaFiles = Database.GetMediaFilesWithImportPrefix(Mask, Prefix, b1, b2, b3);

        if (mediaFiles==null)
            return null;

        for (Object mediaFile : (Object[]) mediaFiles) {
            if (isMediaFileForLoggedOnUser(mediaFile))
                userMediaFiles.add(mediaFile);
        }

        return userMediaFiles.toArray();
    }

    @Deprecated
    public static Object[] getMediaFiles(String Mask) {
        String user = getLoggedinUser();

        if (user==null || user.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.MediaFileAPI.GetMediaFiles(Mask);
        }

        List<Object> userMediaFiles = new ArrayList<Object>();

        Object[] mediaFiles = sagex.api.MediaFileAPI.GetMediaFiles(Mask);

        if (mediaFiles==null)
            return null;

        for (Object mediaFile : mediaFiles) {
            if (isMediaFileForLoggedOnUser(mediaFile))
                userMediaFiles.add(mediaFile);
        }

        return userMediaFiles.toArray();
    }

    @Deprecated
    public static Object[] getMediaFiles() {
        String user = getLoggedinUser();

        if (user==null || user.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.MediaFileAPI.GetMediaFiles();
        }

        List<Object> userMediaFiles = new ArrayList<Object>();

        Object[] mediaFiles = sagex.api.MediaFileAPI.GetMediaFiles();

        if (mediaFiles==null)
            return null;

        for (Object mediaFile : mediaFiles) {
            if (isMediaFileForLoggedOnUser(mediaFile))
                userMediaFiles.add(mediaFile);
        }

        return userMediaFiles.toArray();
    }

    /**
     * Checks to see if the specified MediaFile or Airing should be displayed for the currently logged
     * on user.  This method should be used in conjunction with the FilterByBoolMethod()
     * method to filter out those MediaFiles and Airings that should not be displayed.
     * @param MediaFile
     * @return true if the MediaFile or Airing should be displayed for the currently logged on
     * user, false otherwise.
     */
    @Deprecated
    public static boolean isMediaFileForLoggedOnUser(Object MediaFile) {
        String UserID = getLoggedinUser();

        if (UserID == null || UserID.equalsIgnoreCase(Plugin.SUPER_USER) || MediaFile==null)
            return true;

        MultiMediaFile MMF = new MultiMediaFile(UserID, ensureIsMediaFile(MediaFile));
        return !MMF.isDeleted();
    }

    @Deprecated
    public static Object[] filterMediaFilesNotForLoggedOnUser(Object[] MediaFiles) {
        List<Object> theList = new ArrayList<Object>();

        if (MediaFiles == null)
            return null;

        for (Object MediaFile : MediaFiles)
            if (isMediaFileForLoggedOnUser(MediaFile))
                theList.add(MediaFile);

        return theList.toArray();
    }

    @Deprecated
    public static Object[] filterMediaFilesNotForLoggedOnUser(List<Object> MediaFiles) {
        return filterMediaFilesNotForLoggedOnUser(MediaFiles.toArray());
    }

    /**
     * Replaces the core API.
     * @param file
     * @param Prefix
     * @return The added MediaFile.
     */
    @Deprecated
    public static Object addMediaFile(File file, String Prefix) {

        String User = getLoggedinUser();

        Object MediaFile = sagex.api.MediaFileAPI.AddMediaFile(file, Prefix);

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER) || file==null || Prefix==null || MediaFile==null)
            return MediaFile;

        User user = new User(User);
        user.addToMediaFile(MediaFile);
        return MediaFile;
    }

    /**
     * Replaces the core API IsLibraryFile().  (isArchived is a more intuitive name.)
     * @param MediaFile
     * @return true if archived, false otherwise.
     */
    @Deprecated
    public static boolean isArchived(Object MediaFile) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.MediaFileAPI.IsLibraryFile(MediaFile);
        }

        MultiMediaFile MMF = new MultiMediaFile(User, ensureIsMediaFile(MediaFile));
        return MMF.isArchived();
    }

    /**
     * Replaces the core API MoveTVFileOutOfLibrary().  (clearArchived is a more intuitive name.)
     * @param MediaFile
     */
    @Deprecated
    public static void clearArchived(Object MediaFile) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            sagex.api.MediaFileAPI.MoveTVFileOutOfLibrary(MediaFile);
            return;
        }

        MultiMediaFile MMF = new MultiMediaFile(User, ensureIsMediaFile(MediaFile));
        MMF.clearArchived();
        return;
    }

    /**
     * Replaces the core API MoveFileToLibrary().  (setArchived is a more intuitive name.)
     * @param MediaFile
     */
    @Deprecated
    public static void setArchived(Object MediaFile) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            sagex.api.MediaFileAPI.MoveFileToLibrary(MediaFile);
            return;
        }

        MultiMediaFile MMF = new MultiMediaFile(User, ensureIsMediaFile(MediaFile));
        MMF.setArchived();
        return;
    }

    /**
     * Replaces the core API.
     * @param MediaFile
     * @return true if the MediaFile was deleted, false otherwise.
     */
    @Deprecated
    public static boolean deleteMediaFile(Object MediaFile) {

        String User = getLoggedinUser();

        // If the MediaFile is removed make sure we remove the corresponding record.
        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            MultiMediaFile MMF = new MultiMediaFile(Plugin.SUPER_USER, MediaFile);
            MMF.removeRecord();
            return sagex.api.MediaFileAPI.DeleteFile(MediaFile);
        }

        MultiMediaFile MMF = new MultiMediaFile(getLoggedinUser(), ensureIsMediaFile(MediaFile));
        return MMF.delete(false);
    }

    /**
     * Undeletes the MediaFile for the specified user.
     * @param MediaFile
     */
    @Deprecated
    public static void undeleteMediaFile(String User, Object MediaFile) {

        MultiMediaFile MMF = null;

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            MMF = new MultiMediaFile(Plugin.SUPER_USER, MediaFile);
        } else {
            MMF = new MultiMediaFile(User, MediaFile);
        }

        MMF.unhide();
        return;

    }

    /**
     * Replaces the core API.
     * @param MediaFile
     * @return true if the MediaFile was deleted, false otherwise.
     */
    @Deprecated
    public static boolean deleteMediaFileWithoutPrejudice(Object MediaFile) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.MediaFileAPI.DeleteFileWithoutPrejudice(MediaFile);
        }

        MultiMediaFile MMF = new MultiMediaFile(User, ensureIsMediaFile(MediaFile));
        return MMF.delete(true);
    }

    /**
     * Replaces the core API. (NOT IMPLEMENTED.)
     * @param ID
     * @return
     */
    @Deprecated
    private static Object getMediaFileForID(int ID) {
        return null;
    }


    /*
     * Configuration API.
     *
     * Behavour:
     * - Does not alter core.
     */

    /**
     * Replaces the core API.
     * @return
     */
    @Deprecated
    public static boolean isIntelligentRecordingDisabled() {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return Configuration.IsIntelligentRecordingDisabled();
        }

        User user = new User(User);
        return user.isIntelligentRecordingDisabled();
    }

    /**
     * Convenience method to avoid the confusion of double negatives.
     * @return
     */
    @Deprecated
    public static boolean isIntelligentRecordingEnabled() {
        return !isIntelligentRecordingDisabled();
    }

    /**
     * Invoke IN PLACE OF core API.
     *
     * If enabling IR, it will be enabled in the Core as well as for the user requesting it
     * to be enabled.
     *
     * If disabling IR, it will be disabled in the Core if no users have IR enabled.
     *
     * If the null user or Admin requests the action the behavior is different.  If IR is
     * being disabled we disable it for all users.  If it's being enabled we do not do anything
     * for individual users.  This implies that if Admin turns IR off all of the users must
     * individually turn it back on.
     *
     * @param disabling
     */
    @Deprecated
    public static void setIntelligentRecordingDisabled(boolean disabling) {

        String user = getLoggedinUser();

        if (user==null || user.equalsIgnoreCase(Plugin.SUPER_USER)) {

            // Set the Core to the appropriate state.
            Configuration.SetIntelligentRecordingDisabled(disabling);

            // If we just turned IR off, turn it off for all users.
            if (disabling)
                User.disableIntelligentRecordingForAllUsers();

            return;
        }

        // Change the state for the user.
        User u = new User(user);
        u.setIntelligentRecordingDisabled(disabling);

        // If we just enabled IR for the user, make sure it's enabled in the Core.
        // If we just disabled IR for the user and no users have IR enabled, disable it in the Core.
        if (!disabling) {
            Configuration.SetIntelligentRecordingDisabled(disabling);
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "setIntelligentRecordingDisabled: Enabling IR in the core.");
        } else if (!User.isIntelligentRecordingEnabledForAnyUsers()) {
            Configuration.SetIntelligentRecordingDisabled(disabling);
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "setIntelligentRecordingDisabled: No users are using IR, disabling it in the core.");
        }

        return;
    }

    /*
     * Global API.
     */

    /**
     * Invoke IN PLACE OF core API.
     * @return
     */
    @Deprecated
    public static long getUsedVideoDiskspace() {
        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return Global.GetUsedVideoDiskspace();
        }

        Object[] allMediaFiles = sagex.api.MediaFileAPI.GetMediaFiles("T");
        if (allMediaFiles==null || allMediaFiles.length==0)
            return 0;

        long bytes = 0;

        for (Object mediaFile : allMediaFiles) {
            MultiMediaFile MMF = new MultiMediaFile(User, ensureIsMediaFile(mediaFile));
            if (!MMF.isDeleted())
                bytes += sagex.api.MediaFileAPI.GetSize(mediaFile);
        }

        return bytes;
    }

    /**
     * Returns the number of bytes used by TV recordings belonging to User.
     * @param User
     * @return The number of bytes of space used by the User.
     */
    @Deprecated
    public static long getUsedVideoDiskspace(String User) {

        if (User==null)
            return 0;

        if (User.equalsIgnoreCase(Plugin.SUPER_USER))
            return Global.GetUsedVideoDiskspace();

        Object[] allMediaFiles = sagex.api.MediaFileAPI.GetMediaFiles("T");
        if (allMediaFiles==null || allMediaFiles.length==0)
            return 0;

        long bytes = 0;

        for (Object mediaFile : allMediaFiles) {
            MultiMediaFile MMF = new MultiMediaFile(User, ensureIsMediaFile(mediaFile));
            if (!MMF.isDeleted())
                bytes += sagex.api.MediaFileAPI.GetSize(mediaFile);
        }

        return bytes;
    }

    @Deprecated
    public static Object[] getScheduledRecordings() {
        String user = getLoggedinUser();

        if (user==null || user.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return Global.GetScheduledRecordings();
        }

        List<Object> userScheduledRecordings = new ArrayList<Object>();

        Object[] scheduledRecordings = Global.GetScheduledRecordings();

        if (scheduledRecordings==null)
            return null;

        for (Object recording : scheduledRecordings) {
            if (isMediaFileForLoggedOnUser(recording))
                userScheduledRecordings.add(recording);
        }

        return userScheduledRecordings.toArray();
    }

    /*
     * Airing API.
     */

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static Object record(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.AiringAPI.Record(Airing);
        }

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        MA.setManualRecord();
        return sagex.api.AiringAPI.Record(Airing);
    }

    /**
     * Lets the current logged in user mark an upcoming recording as a manual for another user.
     * @param User
     * @param Airing
     */
    @Deprecated
    public static void markAsManualRecord(String User, Object Airing) {

        // Nothing to do for null user or Admin.
        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return;
        }

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        MA.setManualRecord();
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @param StartTime
     * @param StopTime
     * @return
     */
    @Deprecated
    public static Object setRecordingTimes(Object Airing, long StartTime, long StopTime) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.AiringAPI.SetRecordingTimes(Airing, StartTime, StopTime);
        }

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        MA.setRecordingTimes(StartTime, StopTime);
        return sagex.api.AiringAPI.SetRecordingTimes(Airing, StartTime, StopTime);
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static boolean isManualRecord(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.AiringAPI.IsManualRecord(Airing);
        }

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        return MA.isManualRecord();
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     */
    @Deprecated
    public static void cancelRecord(Object Airing) {

        String User = getLoggedinUser();

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            sagex.api.AiringAPI.CancelRecord(Airing);
            MA.cancelManualRecordForAllUsers();
        } else {
            MA.cancelManualRecord();
        }
        
        return;
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static Object getMediaFileForAiring(Object Airing) {

        String User = getLoggedinUser();
        Object MediaFile = null;

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER) || Airing==null) {
            return sagex.api.AiringAPI.GetMediaFileForAiring(Airing);
        }

        MediaFile = sagex.api.AiringAPI.IsAiringObject(Airing) ? sagex.api.AiringAPI.GetMediaFileForAiring(Airing) : Airing;

        if (MediaFile==null)
            return null;

        MultiMediaFile MMF = new MultiMediaFile(User, MediaFile);
        return (!MMF.isDeleted() ? MediaFile : null);
    }

    // Not implemented, placeholder.
    @Deprecated
    private static Object addAiring() {
        return null;
    }

    // Not implemented, placeholder.
    @Deprecated
    private static Object addAiringDetailed() {
        return null;
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static boolean isDontLike(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER))
            return sagex.api.AiringAPI.IsDontLike(Airing);

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        return MA.isDontLike();
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     */
    @Deprecated
    public static void setDontLike(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            sagex.api.AiringAPI.SetDontLike(Airing);
            return;
        }

        if (SageUtil.GetBoolProperty(Plugin.PROPERTY_UPDATE_IR, true) && isIntelligentRecordingEnabled())
            sagex.api.AiringAPI.SetDontLike(Airing);

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        MA.setDontLike();
        return;
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     */
    @Deprecated
    public static void clearDontLike(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            sagex.api.AiringAPI.ClearDontLike(Airing);
            return;
        }

        if (SageUtil.GetBoolProperty(Plugin.PROPERTY_UPDATE_IR, true) && isIntelligentRecordingEnabled())
            sagex.api.AiringAPI.ClearDontLike(Airing);

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        MA.clearDontLike();
        return;
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static boolean isFavorite(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.AiringAPI.IsFavorite(Airing);
        }

        // If it's not defined in the core as a favorite always return false.
        if (!sagex.api.AiringAPI.IsFavorite(Airing)) {
            return false;
        }

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        return MA.isFavorite();
    }

    /**
     * Invoke in place of core API.
     * @param O
     * @return
     */
    @Deprecated
    public static boolean isFavoriteObject(Object O) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.FavoriteAPI.IsFavoriteObject(O);
        }

        // If it's not defined in the core as a favorite always return false.
        if (!sagex.api.FavoriteAPI.IsFavoriteObject(O)) {
            return false;
        }

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(O));
        return MA.isFavorite();
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static boolean isNotManualOrFavorite(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.AiringAPI.IsNotManualOrFavorite(Airing);
        }

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));

        return (!(MA.isManualRecord() || MA.isFavorite()));
    }


    //
    // Thoughts on managing "watched".
    //
    // IsWatchedCompletely only used in one place in the default STV.  I'm not sure of how this is
    // different from IsWatched so I did not implement it and substituted IsWatched.
    //
    // Must keep track of the settings for MediaFile and Airing because not every MediaFile will have an Airing
    // (DVD, BluRay, Imported) and not all Airings will have a MediaFile (deleted).
    //
    // GetRealWatched(Start/End)Time() returns the real time that the user started and stopped watching.
    // Nowhere in the default STV are these methods used.  Is the info used in the core?
    //
    // GetWatchedDuration() returns the time that the item was watched relative to the item.
    //
    // GetWatched(Start/End)Time() returns the time relative to the item that the user started/stopped watching.
    //
    // SetWatchedTimes(Airing, WatchedEndTime, RealStartTime)
    // - WatchedEndTime: Item relative time that the user has watched up to. The core makes the end time the
    //   maximum of this time and the existing end time so we need to reset or clear the time somehow.
    // - RealStartTime: Real time that the user started watching.
    //
    // GetLatestWatchedTime() returns the item relative time that viewing should start. Not used in the default STV.
    //
    // How is padding handled?
    //

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static boolean isWatched(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER))
            return sagex.api.AiringAPI.IsWatched(Airing);

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        return MA.isWatched();
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     */
    @Deprecated
    public static void setWatched(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            sagex.api.AiringAPI.SetWatched(Airing);
            return;
        }

        // If we mark the Airing ad Watched here we run the risk of having the Airing removed
        // by the Sage core before all users have a chance to watch it.  When the Airing is
        // deleted we check to see if all Users have Watched it.
        
        //if (SageUtil.GetBoolProperty(Plugin.PROPERTY_UPDATE_IR, true) && isIntelligentRecordingEnabled())
            //AiringAPI.SetWatched(Airing);

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        MA.setWatched();
        return;
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     */
    @Deprecated
    public static void clearWatched(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            sagex.api.AiringAPI.ClearWatched(Airing);
            return;
        }

        if (SageUtil.GetBoolProperty(Plugin.PROPERTY_UPDATE_IR, true) && isIntelligentRecordingEnabled())
            sagex.api.AiringAPI.ClearWatched(Airing);

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        MA.clearWatched();
        return;
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static long getRealWatchedStartTime(Object Airing) {
        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.AiringAPI.GetRealWatchedStartTime(Airing);
        }

        long StartTime = 0;

        if (sagex.api.MediaFileAPI.IsMediaFileObject(Airing)) {
            MultiMediaFile MMF = new MultiMediaFile(User, Airing);
            StartTime = MMF.getRealWatchedStartTime();
        } else {
            MultiAiring MA = new MultiAiring(User, Airing);
            StartTime = MA.getRealWatchedStartTime();
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "getRealWatchedStartTime: StartTime " + sagex.api.MediaFileAPI.GetMediaTitle(Airing) + ":" + Plugin.PrintDateAndTime(StartTime));
        return (StartTime == -1 ? sagex.api.AiringAPI.GetRealWatchedStartTime(Airing) : StartTime);
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static long getRealWatchedEndTime(Object Airing) {
        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.AiringAPI.GetRealWatchedEndTime(Airing);
        }

        long EndTime = 0;

        if (sagex.api.MediaFileAPI.IsMediaFileObject(Airing)) {
            MultiMediaFile MMF = new MultiMediaFile(User, Airing);
            EndTime = MMF.getRealWatchedEndTime();
        } else {
            MultiAiring MA = new MultiAiring(User, Airing);
            EndTime = MA.getRealWatchedEndTime();
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "getRealWatchedEndTime: EndTime " + sagex.api.MediaFileAPI.GetMediaTitle(Airing) + ":" + Plugin.PrintDateAndTime(EndTime));
        return (EndTime == -1 ? sagex.api.AiringAPI.GetRealWatchedEndTime(Airing) : EndTime);
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static long getWatchedDuration(Object Airing) {
        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.AiringAPI.GetWatchedDuration(Airing);
        }

        long Duration = 0;

        if (sagex.api.MediaFileAPI.IsMediaFileObject(Airing)) {
            MultiMediaFile MMF = new MultiMediaFile(User, Airing);
            Duration = MMF.getWatchedDuration();
        } else {
            MultiAiring MA = new MultiAiring(User, Airing);
            Duration = MA.getWatchedDuration();
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "getWatchedDuration: Duration " + sagex.api.MediaFileAPI.GetMediaTitle(Airing) + ":" + Plugin.PrintDateAndTime(Duration));
        return (Duration == -1 ? sagex.api.AiringAPI.GetWatchedDuration(Airing) : Duration);
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static long getWatchedStartTime(Object Airing) {
        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.AiringAPI.GetWatchedStartTime(Airing);
        }

        long StartTime = 0;

        if (sagex.api.MediaFileAPI.IsMediaFileObject(Airing)) {
            MultiMediaFile MMF = new MultiMediaFile(User, Airing);
            StartTime = MMF.getWatchedStartTime();
        } else {
            MultiAiring MA = new MultiAiring(User, Airing);
            StartTime = MA.getWatchedStartTime();
        }

        Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getWatchedStartTime: StartTime " + sagex.api.MediaFileAPI.GetMediaTitle(Airing) + ":" + Plugin.PrintDateAndTime(StartTime));
        return (StartTime == -1 ? sagex.api.AiringAPI.GetAiringStartTime(Airing) : StartTime);
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static long getWatchedEndTime(Object Airing) {
        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.AiringAPI.GetWatchedEndTime(Airing);
        }

        // Special case.  If the Airing isWatched we must return 0 for WatchedEndTime.
        //if (isWatched(Airing))
            //return 0;

        long EndTime = 0;

        if (sagex.api.MediaFileAPI.IsMediaFileObject(Airing)) {
            MultiMediaFile MMF = new MultiMediaFile(User, Airing);
            EndTime = MMF.getWatchedEndTime();
        } else {
            MultiAiring MA = new MultiAiring(User, Airing);
            EndTime = MA.getWatchedEndTime();
        }

        Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getWatchedEndTime: EndTime " + sagex.api.MediaFileAPI.GetMediaTitle(Airing) + ":" + Plugin.PrintDateAndTime(EndTime));
        return (EndTime == -1 ? sagex.api.AiringAPI.GetAiringStartTime(Airing) : EndTime);
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static long getAiringDuration(Object Airing) {
        return sagex.api.AiringAPI.GetAiringDuration(Airing);
    }

    /*
     * Favorite API.
     *
     * Behavior:
     * - When a user is created the user inherits all Favorites.
     * - Favorites are added and deleted on a per-user basis.
     * - If a Favorite is added by more than one user, all users share the same Favorite.
     */

    /**
     * Invoke IN PLACE OF core API.
     * @return
     */
    @Deprecated
    public static Object[] getFavorites() {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.FavoriteAPI.GetFavorites();
        } else {
            return MultiFavorite.getFavorites();
        }
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Favorite
     */
    @Deprecated
    public static void removeFavorite(Object Favorite) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            MultiFavorite.removeAllUsers(Favorite);
            sagex.api.FavoriteAPI.RemoveFavorite(Favorite);
            return;
        }

        MultiFavorite MF = new MultiFavorite(User, Favorite);
        MF.removeFavorite();
        return;
    }

    /**
     * Invoke right after Core API.
     * @param Favorite
     */
    @Deprecated
    public static void addFavorite(Object Favorite) {

        String U = getLoggedinUser();

        if (U==null || U.equalsIgnoreCase(Plugin.SUPER_USER)) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "addFavorite: Adding Favorite for all Users.");

            List<String> allUsers = User.getAllUsers(false);

            for (String thisUser : allUsers) {
                MultiFavorite MF = new MultiFavorite(thisUser, Favorite);
                MF.addFavorite();
            }

            return;
        }

        MultiFavorite MF = new MultiFavorite(U, Favorite);
        MF.addFavorite();
        return;
    }

    /**
     * Add the Favorite for the specified UserID.
     * @param UserID
     * @param Favorite
     */
    @Deprecated
    public static void addFavorite(String UserID, Object Favorite) {

        if (UserID==null || UserID.equalsIgnoreCase(Plugin.SUPER_USER)) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "addFavorite: Adding Favorite for all Users.");

            List<String> allUsers = User.getAllUsers(false);

            for (String thisUser : allUsers) {
                MultiFavorite MF = new MultiFavorite(thisUser, Favorite);
                MF.addFavorite();
            }

            return;
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "addFavorite: Adding Favorite for User " + UserID);
        MultiFavorite MF = new MultiFavorite(UserID, Favorite);
        MF.addFavorite();
        return;
    }

    /**
     * Invoke IN PLACE OF core API.
     * @param Airing
     * @return
     */
    @Deprecated
    public static Object getFavoriteForAiring(Object Airing) {

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return sagex.api.FavoriteAPI.GetFavoriteForAiring(Airing);
        }

        MultiAiring MA = new MultiAiring(User, ensureIsAiring(Airing));
        return MA.getFavoriteForAiring();
    }

    // Not in the default STV, but put here as a placeholder.
    @Deprecated
    private static int getFavoriteID(Object Favorite) {
        return 0;
    }

    /**
     * Returns a List of users that have the Favorite defined.
     * @param Favorite
     * @return
     */
    @Deprecated
    public static List<String> GetUsersForFavorite(Object Favorite) {

        List<String> TheList = new ArrayList<String>();

        if (Favorite==null || !sagex.api.FavoriteAPI.IsFavoriteObject(Favorite)) {
            return TheList;
        }

        String User = getLoggedinUser();

        if (User==null || User.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return TheList;
        }

        MultiFavorite MF = new MultiFavorite(User, Favorite);
        return MF.getAllowedUsers();
    }


    /*
     * User related methods.
     */

    /**
     * Check to see if a user exists in the database.
     * @param UserID
     * @return true if the user exists in the database, false otherwise.
     */
    @Deprecated
    public static boolean userExists(String UserID) {

        if (UserID == null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "userExists: null UserID.");
            return false;
        }

        User user = new User(UserID);
        return user.exists();
    }

    /**
     * Creates a user in the user database.  Does NOT initialize the user information in the
     * other databases, use addUserToDatabase() for that.
     * @param UserID The userID must be unique in the system.
     * @param Password The password can't be null.
     * @return true for success, false otherwise.
     */
    @Deprecated
    public static boolean createNewUser(String UserID, String Password) {
        if (UserID==null || Password==null || UserID.isEmpty() || Password.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "createNewUser: Bad parameters " + UserID + ":" + Password);
            return false;
        }

        User user = new User(UserID);

        if (user.exists()) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "createNewUser: User already exists " + UserID);
            return false;
        }

        return user.create(Password);
    }

    /**
     * Removes a user from the user database.  Does NOT remove the user information from
     * the other databases, use removeUserFromDatabase()for that.
     * @param UserID
     * @return true if success, false otherwise.
     */
    @Deprecated
    public static boolean removeUser(String UserID) {
        if (UserID==null || UserID.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "removeUser: Bad parameters " + UserID);
            return false;
        }

        User user = new User(UserID);
        return user.destroy();
    }

    /**
     * Retrieves the user password from the database.  The returned password is not encrypted
     * in any way.
     * @param UserID
     * @return The user password.
     */
    @Deprecated
    public static String getUserPassword(String UserID) {
        User user = new User(UserID);
        return user.getPassword();
    }

    /**
     * Initializes user access to the MediaFile or Airing. The user's view of the object
     * (Watched, Like/Don't Like, viewing times, etc) will initially mirror the core.
     * @param UserID The user to initialize.
     * @param MediaFileOrAiring The MediaFile or Airing to initialize.
     */
    @Deprecated
    public static void addUserToMediaFile(String UserID, Object MediaFileOrAiring) {

        if (UserID==null || MediaFileOrAiring==null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "addUserToMediaFile: null User or MediaFile " + UserID);
            return;
        }

        Log.getInstance().write(Log.LOGLEVEL_WARN, "addUserToMediaFile: Adding User to MediaFile " + UserID + ":" + sagex.api.MediaFileAPI.GetMediaTitle(MediaFileOrAiring));

        User user = new User(UserID);

        Object Airing = null;
        Object MediaFile = null;

        if (sagex.api.AiringAPI.IsAiringObject(MediaFileOrAiring)) {
            Airing = MediaFileOrAiring;
            MediaFile = sagex.api.AiringAPI.GetMediaFileForAiring(MediaFileOrAiring);
        } else {
            MediaFile = MediaFileOrAiring;
            Airing = sagex.api.MediaFileAPI.GetMediaFileAiring(MediaFileOrAiring);
        }

        user.addToMediaFile(MediaFile);
        user.addToAiring(Airing);
    }

    /**
     * Removes all user access data from the specified MediaFile (or Airing).
     * @param UserID
     * @param MediaFile
     */
    @Deprecated
    public static void removeUserFromMediaFile(String UserID, Object MediaFile) {
        User user = new User(UserID);
        user.removeFromMediaFile(MediaFile);
    }

    /**
     * Removes the information of all users from the MediaFile or Airing.
     */
    @Deprecated
    public static void removeAllUsersFromMediaFile(Object MediaFile) {
        List<String> Users = User.getAllUsers();

        for (String User : Users) {
            removeUserFromMediaFile(User, MediaFile);
        }
    }

    /**
     * Get a List of all users in the database including "Admin".
     * @return
     */
    @Deprecated
    public static List<String> getAllDefinedUsers() {
        return User.getAllUsers();
    }

    /**
     * Get a List of all users in the database, optionally returning "Admin".
     * @param includeAdmin true to include "Admin" in the returned List, false otherwise.
     * @return
     */
    @Deprecated
    public static List<String> getAllDefinedUsers(boolean includeAdmin) {
        return User.getAllUsers(includeAdmin);
    }

    /**
     * Completely removes all user information from the database.
     * @param UserID
     * @return true if success, false otherwise.
     */
    @Deprecated
    public static boolean removeUserFromDatabase(String UserID) {

        if (UserID==null || UserID.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "removeUserFromAllMediaFiles: null UserID.");
            return false;
        }

        User user = new User(UserID);
        return user.removeFromDataBase();
    }

    /**
     * Initializes the user in the database.
     * @param UserID
     */
    @Deprecated
    public static void addUserToDatabase(String UserID) {

        if (UserID==null || UserID.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "addUserToAllMediaFiles: null UserID.");
            return;
        }

        User user = new User(UserID);
        user.initializeInDataBase();

        return;
    }

    /**
     * Removes all user information from the user database, leaves the other databases intact.
     */
    @Deprecated
    public static void clearUserDatabase() {
        UserRecordAPI.DeleteAllUserRecords(User.STORE);
    }

    /**
     * Resets all user information (Watched, Like/Don't Like, watched times, etc.)
     * in the MediaFile database.
     */
    @Deprecated
    public static void resetMediaFileDatabase() {
        List<String> AllUsers = User.getAllUsers();
        
        for (String User : AllUsers) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "resetMediaFileDatabase: Removing " + User);
            removeUserFromDatabase(User);

            Log.getInstance().write(Log.LOGLEVEL_TRACE, "resetMediaFileDatabase: Adding " + User);
            addUserToDatabase(User);
        }
    }

    @Deprecated
    public static boolean isShowImports(String U) {

        if (U==null || U.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return true;
        }

        User user = new User(U);
        return user.isShowImports();
    }

    @Deprecated
    public static void setShowImports(String U, boolean Show) {

        if (U==null || U.equalsIgnoreCase(Plugin.SUPER_USER)) {
            return;
        }

        User user = new User(U);
        user.setShowImports(Show);
        return;
    }

    /*
     * Database Maintenance.
     */

    /**
      * Removes all records in the Favorite, MediaFile, Airing and User data stores.
      */
    public static void clearAll() {
        Log.getInstance().write(Log.LOGLEVEL_WARN, "clearAll: Wiping the entire database.");

        Log.getInstance().write(Log.LOGLEVEL_WARN, "clearAll: Wiping the Favorites database.");
        MultiFavorite.WipeDatabase();

        Log.getInstance().write(Log.LOGLEVEL_WARN, "clearAll: Wiping the MediaFile database.");
        MultiMediaFile.WipeDatabase();

        Log.getInstance().write(Log.LOGLEVEL_WARN, "clearAll: Wiping the Airing database.");
        MultiAiring.WipeDatabase();

        Log.getInstance().write(Log.LOGLEVEL_WARN, "clearAll: Wiping the User database.");
        User.wipeDatabase();
        Log.getInstance().write(Log.LOGLEVEL_WARN, "clearAll: Wiping complete.");
    }

    /**
     * @return The number of records in the User data store.
     */
    public static int getUserStoreSize() {
        return getStoreSize(User.STORE);
    }

    /**
     * @return The number of records in the Favorite data store.
     */
    public static int getFavoriteStoreSize() {
        return getStoreSize(MultiFavorite.FAVORITE_STORE);
    }

    /**
     * @return The number of records in the MediaFile data store.
     */
    public static int getMediaFileStoreSize() {
        return getStoreSize(MultiMediaFile.MEDIAFILE_STORE);
    }

    /**
     * @return The number of records in the Airing data store.
     */
    public static int getAiringStoreSize() {
        return getStoreSize(MultiAiring.AIRING_STORE);
    }

    /**
     * Get the number of records in the specified UserRecordAPI store.
     * @param Store The UserRecord data store.
     * @return The number of records in the data store.
     */
    public static int getStoreSize(String Store) {
        if (Store==null || Store.isEmpty())
            return 0;
        else
            return UserRecordAPI.GetAllUserRecords(Store).length;
    }

    /**
     * Makes sure every UserRecord in the MediaFile store has a corresponding MediaFile in
     * the Sage core.  This method will take a long time to execute so it should be called in
     * a separate thread.
     * @param countOnly Set to true to just return the number of orphaned records and not delete
     * any of them.  Set to false to physically remove all orphaned records.
     * @return The number of records removed because there was no corresponding MediaFile.
     */
    public static int cleanMediaFileUserRecord(boolean countOnly) {
        
        // Get all of the records in the Store.
        Object[] allRecords = UserRecordAPI.GetAllUserRecords(MultiMediaFile.MEDIAFILE_STORE);
        
        if (allRecords==null || allRecords.length==0) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanMediaFileUserRecord: No records.");
            return 0;
        }
        
        Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanMediaFileUserRecord: Found records " + allRecords.length);
        
        // Get all of the MediaFiles known to the Sage core.
        Object[] AllMediaFiles = sagex.api.MediaFileAPI.GetMediaFiles();

        // If there are no MediaFiles delete everything in the Store.
        if (AllMediaFiles==null || AllMediaFiles.length==0) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanMediaFileUserRecord: No MediaFiles.");
            if (!countOnly) {
                Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanMediaFileUserRecord: Deleting entire store.");
                UserRecordAPI.DeleteAllUserRecords(MultiMediaFile.MEDIAFILE_STORE);
            }
            
            return allRecords.length;
        }
        
        Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanMediaFileUserRecord: Found MediaFiles " + AllMediaFiles.length);

        // Clear the "keep" flag in all of the records.
        MultiObject.clearKeeperFlag(allRecords);

        // Loop through all of the MediaFiles setting the "keep" flag.
        for (Object MediaFile : AllMediaFiles) {
            Integer ID = sagex.api.MediaFileAPI.GetMediaFileID(MediaFile);
            String key = ID.toString();
            Object record = UserRecordAPI.GetUserRecord(MultiMediaFile.MEDIAFILE_STORE, key);

            if (record==null) {
                Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanMediaFileUserRecord: null record for " + sagex.api.MediaFileAPI.GetMediaTitle(MediaFile));
            } else {
                MultiObject.setKeeperFlag(record);
            }
        }

        // Remove (or maybe just count) all records that do not have the "keep" flag set;
        return MultiObject.deleteNonKeepers(allRecords, countOnly);
    }

    /**
     * Makes sure every UserRecord in the Airing store has a corresponding Airing in
     * the Sage core.  This method will take a long time to execute so it should be called in
     * a separate thread.
     * @param countOnly Set to true to just return the number of orphaned records and not delete
     * any of them.  Set to false to physically remove all orphaned records.
     * @return The number of records removed because there was no corresponding MediaFile.
     */
    public static int cleanAiringUserRecord(boolean countOnly) {

        // Get all of the records in the Store.
        Object[] allRecords = UserRecordAPI.GetAllUserRecords(MultiAiring.AIRING_STORE);

        if (allRecords==null || allRecords.length==0) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanAiringUserRecord: No records.");
            return 0;
        }

        Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanAiringUserRecord: Found records " + allRecords.length);

        // Get all of the Airings known to the Sage core.
        Object[] AllAirings = MultiAiring.getAllSageAirings();

        // If there are no Airings delete everything in the Store.
        if (AllAirings==null || AllAirings.length==0) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanAiringUserRecord: No Airings.");
            if (!countOnly) {
                Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanAiringUserRecord: Deleting entire store.");
                UserRecordAPI.DeleteAllUserRecords(MultiAiring.AIRING_STORE);
            }

            return allRecords.length;
        }

        Log.getInstance().write(Log.LOGLEVEL_WARN, "cleanAiringUserRecord: Found Airings " + AllAirings.length);

        // Clear the "keep" flag in all of the records.
        MultiObject.clearKeeperFlag(allRecords);

        // Loop through all of the MediaFiles setting the "keep" flag.
        for (Object Airing : AllAirings) {
            Integer ID = sagex.api.AiringAPI.GetAiringID(Airing);
            String key = ID.toString();
            Object record = UserRecordAPI.GetUserRecord(MultiAiring.AIRING_STORE, key);
            if (record!=null)
                MultiObject.setKeeperFlag(record);
        }

        // Remove (or maybe just count) all records that do not have the "keep" flag set;
        return MultiObject.deleteNonKeepers(allRecords, countOnly);
    }

    /**
     * Makes sure that no records in any of the stores used in the Plugin contain references
     * to users that no longer exist.
     * @param remove Set to true to just return the number of occurances and not delete
     * any of them.  Set to false to physically remove all occurances records.
     * @return The number of occurances.
     */
    public static Set<String> scanForOrphanedUserData(boolean remove) {
        Set<String> orphans = new HashSet<String>();

        orphans.addAll(scanFlagsForOrpanedUserData(remove));
        orphans.addAll(scanPrefixedFlagsForOrpanedUserData(remove));

        return orphans;
    }

    /**
     * Removes all undefined users from all Flags that are comprised of delimited Strings.
     * Has the side effect of removing the "KEEP" flag but this should not cause any problems.
     * @param remove
     * @return
     */
    private static Set<String> scanFlagsForOrpanedUserData(boolean remove) {
        Set<String> orphans = new HashSet<String>();

        List<String> allFlags = new ArrayList<String>();
        allFlags.addAll(Arrays.asList(MultiObject.OBJECT_FLAGS));
        allFlags.addAll(Arrays.asList(MultiMediaFile.FLAGS));
        allFlags.addAll(Arrays.asList(MultiAiring.FLAGS));
        allFlags.addAll(Arrays.asList(MultiFavorite.FLAGS));
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanFlagsForOrpanedUserData: allFlags " + allFlags);

        // Nothing to do if there are no Flags.
        if (allFlags==null || allFlags.isEmpty())
            return orphans;

        List<String> allUsers = User.getAllUsers(false);
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanFlagsForOrpanedUserData: allUsers " + allUsers);

        // Error if allUsers == null.
        if (allUsers==null)
            return orphans;

        List<Object> allRecords = new ArrayList<Object>();
        allRecords.addAll(Arrays.asList(UserRecordAPI.GetAllUserRecords(MultiMediaFile.MEDIAFILE_STORE)));
        allRecords.addAll(Arrays.asList(UserRecordAPI.GetAllUserRecords(MultiAiring.AIRING_STORE)));
        allRecords.addAll(Arrays.asList(UserRecordAPI.GetAllUserRecords(MultiFavorite.FAVORITE_STORE)));
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanFlagsForOrpanedUserData: allRecords " + allRecords.size());

        // Nothing to do if there are no records.
        if (allRecords==null || allRecords.isEmpty())
            return orphans;

        // Search every record for Flags that contain a user name that does not exist.
        for (Object record : allRecords) {
            for (String flag : allFlags) {

                // Get the raw flag data from the record.
                String delimitedString = UserRecordAPI.GetUserRecordData(record, flag);

                // Convert it to a List of Users.
                List<String> usersInFlag = DelimitedString.delimitedStringToList(delimitedString, Plugin.LIST_SEPARATOR);

                // See if there are any users in the flag that are not defined. 
                //  Ignore the Key flag.
                if(!flag.equalsIgnoreCase(MultiObject.KEY) && !allUsers.containsAll(usersInFlag)) {
                    Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanFlagsForOrpanedUserData: Found flag with undefined user " + flag + ":" + usersInFlag);

                    // Create a working List because delimitedStringToList returns a non-mutable List.
                    List<String> orphanUsers = new ArrayList<String>();
                    orphanUsers.addAll(usersInFlag);

                    // Isolate orphan users.
                    orphanUsers.removeAll(allUsers);
                    orphans.addAll(orphanUsers);
                    Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanFlagsForOrpanedUserData: orphan users " + orphanUsers);

                    if (remove) {
                        Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanFlagsForOrpanedUserData: Removing orphan user data.");

                        for (String u : orphanUsers) {
                            DelimitedString DS = new DelimitedString(delimitedString, Plugin.LIST_SEPARATOR);
                            DS.removeElement(u);
                            UserRecordAPI.SetUserRecordData(record, flag, DS.toString());
                        }
                    }
                }
            }
        }

        return orphans;
    }

    private static Set<String> scanPrefixedFlagsForOrpanedUserData(boolean remove) {
        Set<String> orphans = new HashSet<String>();

        List<String> allFlags = new ArrayList<String>();
        allFlags.addAll(Arrays.asList(MultiObject.OBJECT_FLAG_PREFIXES));
        allFlags.addAll(Arrays.asList(MultiMediaFile.FLAG_PREFIXES));
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanPrefixedFlagsForOrpanedUserData: allFlags " + allFlags);

        // Nothing to do if there are no Flags.
        if (allFlags==null || allFlags.isEmpty())
            return orphans;

        List<String> allUsers = User.getAllUsers(false);
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanPrefixedFlagsForOrpanedUserData: allUsers " + allUsers);

        // Error if allUsers == null.
        if (allUsers==null)
            return orphans;

        List<Object> allRecords = new ArrayList<Object>();
        allRecords.addAll(Arrays.asList(UserRecordAPI.GetAllUserRecords(MultiMediaFile.MEDIAFILE_STORE)));
        allRecords.addAll(Arrays.asList(UserRecordAPI.GetAllUserRecords(MultiAiring.AIRING_STORE)));
        allRecords.addAll(Arrays.asList(UserRecordAPI.GetAllUserRecords(MultiFavorite.FAVORITE_STORE)));
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanPrefixedFlagsForOrpanedUserData: allRecords " + allRecords.size());

        // Nothing to do if there are no records.
        if (allRecords==null || allRecords.isEmpty())
            return orphans;

        // Search every record for Flags that contain a user name that does not exist.
        for (Object record : allRecords) {

            // Get all of the field names in the record.
            String[] names = UserRecordAPI.GetUserRecordNames(record);

            if (names==null || names.length==0) {
                Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "scanPrefixeFlagsForOrpanedUserData: No names in record.");
            } else for (String name : names) {

                // Check if the format is Prefix_UserID, skip it if it is not.
                List<String> componentsInFlag = DelimitedString.delimitedStringToList(name, "_");

                if (componentsInFlag!=null && componentsInFlag.size()==2) {

                    // The first element is the prefix, the second is the userID.
                    String user = componentsInFlag.get(1);

                    if (!allUsers.contains(user)) {
                        orphans.add(user);
                        Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanPrefixeFlagsForOrpanedUserData: Found flag with undefined user " + ":" + name);
                        if (remove) {
                            Log.getInstance().write(Log.LOGLEVEL_TRACE, "scanPrefixedFlagsForOrpanedUserData: Removing orphan user data.");
                            UserRecordAPI.SetUserRecordData(record, name, null);
                        }
                    }
                }
            }
        }

        return orphans;
    }

    /*
     * Logging.
     */

    /**
     * Returns the current loglevel.
     * @return
     */
    public static int getLoglevel() {
        return Log.getInstance().GetLogLevel();
    }

    /**
     * Set the current loglevel.
     */
    public static void setLoglevel(int NewLevel) {
        Log.getInstance().SetLogLevel(NewLevel);
    }

    public static boolean multiUserfySTV(String ContextName, Object[] ExistingWidgets, Object[] ImportedWidgets) {
        System.out.println("MUS: Begin multiuserfication process for context " + ContextName);

        if (ContextName==null || ContextName.isEmpty()) {
            System.out.println("MUS: null of empty ContextName " + ContextName);
            return false;
        }

        MultiSTV MSTV = new MultiSTV(ContextName, ExistingWidgets, ImportedWidgets);
        int numChanges = MSTV.modifyWidgets(false);

        System.out.println("MUS: End multiuserfication process for context " + ContextName + ":" + numChanges);
        return true;
    }

    /*
     * Support methods.
     */

    /**
     * Check that the SageObject is an Airing.  If it is a MediaFile return the Airing for the
     * MediaFile.  If it's not an Airing and not a MediaFile, return the original Object.
     * @param SageObject The Object to check.
     * @return The Airing if possible, the original Object otherwise.
     */
    static Object ensureIsAiring(Object SageObject) {
        if (sagex.api.AiringAPI.IsAiringObject(SageObject)) {
            return SageObject;
        } else if (sagex.api.MediaFileAPI.IsMediaFileObject(SageObject)) {
            return sagex.api.MediaFileAPI.GetMediaFileAiring(SageObject);
        } else {
            if (ShowAPI.IsShowObject(SageObject))
                Log.getInstance().write(Log.LOGLEVEL_WARN, "ensureIsAiring: Found a Show.");
            else
                Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "ensureIsAiring: Found unknown Object " + sagex.api.AiringAPI.PrintAiringShort(SageObject));
            return SageObject;
        }
    }

    /**
     * Check that the SageObject is a MediaFile.  If it is an Airing return the MediaFile for the
     * Airing.  If it's not an Airing and not a MediaFile, return the original Object.
     * @param SageObject The Object to check.
     * @return The Airing if possible, the original Object otherwise.
     */
    static Object ensureIsMediaFile(Object SageObject) {
        if (sagex.api.MediaFileAPI.IsMediaFileObject(SageObject)) {
            return SageObject;
        } else if (sagex.api.AiringAPI.IsAiringObject(SageObject)) {
            return sagex.api.AiringAPI.GetMediaFileForAiring(SageObject);
        } else {
            if (ShowAPI.IsShowObject(SageObject))
                Log.getInstance().write(Log.LOGLEVEL_WARN, "ensureIsMediaFile: Found a Show.");
            else
                Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "ensureIsMediaFile: Found unknown Object " + sagex.api.AiringAPI.PrintAiringShort(SageObject));
            return SageObject;
        }
    }

    /*
     * Debug stuff.
     */
    public static List<String> getFlagsForMediaFile(Object MediaFile) {
        List<String> TheList = new ArrayList<String>();

        if (MediaFile==null) {
            return TheList;
        }

        if (sagex.api.MediaFileAPI.IsMediaFileObject(MediaFile)) {

            MultiMediaFile MMF = new MultiMediaFile(getLoggedinUser(), MediaFile);

            for (String Flag : MultiMediaFile.FLAGS)
                TheList.add(Flag + "=" + MMF.getFlagString(Flag));

        } else {
            MultiAiring MA = new MultiAiring(getLoggedinUser(), MediaFile);

            for (String Flag : MultiAiring.FLAGS)
                TheList.add(Flag + "=" + MA.getFlagString(Flag));
        }

        Object Favorite = sagex.api.FavoriteAPI.GetFavoriteForAiring(MediaFile);

        if (Favorite!=null) {
            MultiFavorite MF = new MultiFavorite(getLoggedinUser(), Favorite);
            for (String Flag : MultiFavorite.FLAGS)
                TheList.add(Flag + "=" + MF.getFlagString(Flag));
        }

        TheList.addAll(MultiMediaFile.GetFlagsStartingWith(MultiMediaFile.DURATION_PREFIX));
        //TheList.addAll(MultiMediaFile.GetFlagsStartingWith(MultiMediaFile.MEDIATIME_PREFIX));
        TheList.addAll(MultiMediaFile.GetFlagsStartingWith(MultiMediaFile.CHAPTERNUM_PREFIX));
        TheList.addAll(MultiMediaFile.GetFlagsStartingWith(MultiMediaFile.TITLENUM_PREFIX));

        Boolean IsArchived = sagex.api.MediaFileAPI.IsLibraryFile(MediaFile);
        Boolean DontLike = sagex.api.AiringAPI.IsDontLike(MediaFile);
        Boolean Manual = sagex.api.AiringAPI.IsManualRecord(MediaFile);
        Boolean IsFavorite = sagex.api.AiringAPI.IsFavorite(MediaFile);
        TheList.add("Core: Archived=" + IsArchived.toString() + " DontLike=" + DontLike.toString() + " Manual=" + Manual.toString() + " Favorite=" + IsFavorite.toString());

        return TheList;
    }

    public static List<String> getFlagsForUser(Object MediaFile) {

        List<String> TheList = new ArrayList<String>();

        if (MediaFile==null) {
            return TheList;
        }

        if (sagex.api.MediaFileAPI.IsMediaFileObject(MediaFile)) {
            MultiMediaFile MMF = new MultiMediaFile(getLoggedinUser(), MediaFile);
            return MMF.getFlagsForUser();
        } else {
            MultiAiring MA = new MultiAiring(getLoggedinUser(), MediaFile);
            return MA.getFlagsForUser();
        }
    }
}
