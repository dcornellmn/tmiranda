/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package tmiranda.aar;

import sagex.api.*;

/**
 * Logging methods. Singleton class.
 * @author Tom Miranda
 */
public class Log {

    /**
     * Possible values for the LogLevel.
     */

    /**
     * Property used to store the current log level.
     */
    public static final String  PROPERTY_LOGLEVEL = "aar/loglevel";

    public static final int     LOGLEVEL_NONE       = 100;
    public static final int     LOGLEVEL_ERROR      = 75;
    public static final int     LOGLEVEL_WARN       = 50;
    public static final int     LOGLEVEL_TRACE      = 25;
    public static final int     LOGLEVEL_VERBOSE    = 10;
    public static final int     LOGLEVEL_MAX        = 0;

    private static int CurrentLogLevel = LOGLEVEL_WARN;

    private static final String     DEFAULT_LOGLEVEL = "50";

    private static Log instance = new Log();

    /*
     * Private constructor.  Only let getInstance return a valid instance.
     */
    private Log() {
        String LoglevelString = Configuration.GetServerProperty(PROPERTY_LOGLEVEL, DEFAULT_LOGLEVEL);

        try {
            CurrentLogLevel = Integer.parseInt(LoglevelString);
            System.out.println("AAR: Log: Setting CurrentLoglevel to " + LoglevelString);
        } catch (NumberFormatException e) {
            CurrentLogLevel = LOGLEVEL_WARN;
            System.out.println("AAR: Log: Malformed loglevel, setting to WARN. " + e.getMessage());
        }
    }

    /**
     * Gets the one and only instance for the Log class.
     * <p>
     * @return  The instance for the Logger.
     */
    public static Log getInstance() {
        return instance;
    }

    /**
     * Destroy the logger.
     */
    public static void destroy() {
        instance = null;
    }

    /**
     * Restart the logger.
     */
    public static void start() {
        if (instance == null)
            instance = new Log();
    }

    /**
     * Writes a string to the logfile if the level indicated is at least at the current LogLevel.
     * <p>
     * @param level The LogLevel at which this message should be written.
     * @param s     The String to write.
     */
    public void write(int level, String s) {
        if (level >= CurrentLogLevel)
            System.out.println("AAR: " + s);

        if (level == Log.LOGLEVEL_ERROR) {
            Exception e = new Exception();
            e.printStackTrace();
        }
    }

    /**
     * Set the current LogLevel.
     * <p>
     * @param NewLevel  The new Loglevel.
     */
    public void SetLogLevel(Integer NewLevel) {
        CurrentLogLevel = NewLevel;

        if (NewLevel<LOGLEVEL_MAX || NewLevel>LOGLEVEL_NONE) {
            System.out.println("AAR: Invalid new loglevel, setting loglevel to default.");
            Configuration.SetServerProperty(PROPERTY_LOGLEVEL, DEFAULT_LOGLEVEL);
            return;
        }

        Configuration.SetServerProperty(PROPERTY_LOGLEVEL, NewLevel.toString());

        switch (NewLevel) {
            case LOGLEVEL_NONE:     System.out.println("AAR: Setting loglevel to None.");   break;
            case LOGLEVEL_ERROR:    System.out.println("AAR: Setting loglevel to Error.");  break;
            case LOGLEVEL_WARN:     System.out.println("AAR: Setting loglevel to Warn.");   break;
            case LOGLEVEL_TRACE:    System.out.println("AAR: Setting loglevel to Trace.");  break;
            case LOGLEVEL_VERBOSE:  System.out.println("AAR: Setting loglevel to Verbose.");break;
            case LOGLEVEL_MAX:      System.out.println("AAR: Setting loglevel to Max.");    break;
        }
    }

    /**
     * Gets the current LogLevel.
     * <p>
     * @return  The current LogLevel.
     */
    public int GetLogLevel() {
        return CurrentLogLevel;
    }
}
