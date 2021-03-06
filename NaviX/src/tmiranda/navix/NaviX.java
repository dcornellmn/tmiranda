
package tmiranda.navix;

import java.util.regex.Pattern;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.io.*;
import java.util.*;

/**
 * Utility methods.
 *
 * @author Tom Miranda.
 */
public class NaviX {

    public static final String VERSION = "0.01";

    public static final long SERIAL_UID = 1;

    static final long serialVersionUID = SERIAL_UID;

    /**
     * Get the version number of the JAR.
     *
     * @return
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     *
     * Some text has the embedded XML [COLOR=....] [/COLOR].  This method removes it.
     *
     * @param str
     * @return
     */
    public static String stripCOLOR(String str) {
        if (str==null)
            return str;

        str = str.replace("[/COLOR]", "");

        if (str != null) {
            str = str.replaceAll(Pattern.quote("[COLOR=") + "[a-fA-F0-9]{8}" + Pattern.quote("]"), "");
        }

        return str;
    }

    /**
     * Returns the number of cacher threads that are running.
     * @return
     */
    public static int numberCacherThreads() {
        int number = 0;
        
        for (Thread t : getAllThreads())
            if (t.getName().startsWith(Cacher.CACHER_THREAD_NAME))
                number++;
        
        return number;
    }

    private static Thread[] getAllThreads() {
        final ThreadGroup root = getRootThreadGroup( );
        final ThreadMXBean thbean = ManagementFactory.getThreadMXBean( );
        int nAlloc = thbean.getThreadCount( );
        int n = 0;
        Thread[] threads;
        do {
            nAlloc *= 2;
            threads = new Thread[ nAlloc ];
            n = root.enumerate( threads, true );
        } while ( n == nAlloc );
        return java.util.Arrays.copyOf( threads, n );
    }

    private static ThreadGroup rootThreadGroup = null;

    private static ThreadGroup getRootThreadGroup( ) {
        if (rootThreadGroup != null )
            return rootThreadGroup;

        ThreadGroup tg = Thread.currentThread( ).getThreadGroup( );
        ThreadGroup ptg;

        while ((ptg = tg.getParent( )) != null )
            tg = ptg;

        rootThreadGroup = tg;
        return tg;
    }

    /**
     * Deletes the file and creates a new empty file.  This can also be used by the
     * STV to determine if the user has input a valid name for a file.
     *
     * @param fileName
     * @return
     */
    public static boolean initFile(String fileName) {
        if (fileName==null || fileName.isEmpty())
            return false;

        File f = new File(fileName);

        if (f.exists())
            return f.delete();

        try {
            return f.createNewFile();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Converts a comma separated string into a List.
     *
     * @param s
     * @return
     */
    public static List<String> stringToList(String s) {
        if (s==null || s.isEmpty())
            return null;

        List<String> list = new ArrayList<String>();

        String[] parts = s.split(",");
        if (parts==null || parts.length==0)
            return null;

        for (String part : parts)
            if (part!=null && !part.isEmpty())
                list.add(part);

        return list;
    }

    /**
     * Checks if the List contains any "types" that are not valid Playlist types.
     * @param list
     * @return
     */
    public static boolean containsInvalidTypes(List<String> list) {
        List<String> values = new ArrayList<String>();
        for (PlaylistType type : PlaylistType.values())
            values.add(type.toString());

        for (String item : list)
            if (!values.contains(item))
                return true;

        return false;
    }
}
