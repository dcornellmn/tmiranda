/*
 * Buildingblock.
 */

package tmiranda.rpad;

import sagex.api.*;
import java.util.*;

/**
 *
 * @author Tom Miranda.
 */
public class PluginAndDependencies {
    private Object Plugin;                              // The Plugin we are interested in.
    private Object Parent;                              // The parent Plugin
    private List<PluginAndDependencies> Dependencies;   // null means the end of the branch.

    /**
     * Constructor.  Used to create a new PluginAndDependencies tree.
     *
     * @param ThePlugin The Plugin to create the Tree for.
     * @param TheParent Set to the same value as ThePlugin.
     */
    public PluginAndDependencies(Object ThePlugin, Object TheParent) {
        Plugin = ThePlugin;
        Parent = TheParent;

        if (ThePlugin == null || TheParent == null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "PluginAndDependecies: Error. null parameter.");
            return;
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "PluginAndDependecies: Plugin and Parent = " + PluginAPI.GetPluginIdentifier(Plugin) + ":" + PluginAPI.GetPluginIdentifier(Parent));

        if (Dependencies == null) {
            Dependencies = new ArrayList<PluginAndDependencies>();
        }

        List<Object> Plugins = getPluginDependencies(ThePlugin);

        if (Plugins == null || Plugins.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "PluginAndDependecies: No dependencies for " + PluginAPI.GetPluginIdentifier(Plugin));
            return;
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "PluginAndDependecies: Dependencies found = " + Plugins.size());

        for (Object ThisPlugin : Plugins) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "PluginAndDependecies: Recursing = " + PluginAPI.GetPluginIdentifier(Plugin) + ":" + PluginAPI.GetPluginIdentifier(ThisPlugin));
            Dependencies.add(new PluginAndDependencies(ThisPlugin, ThePlugin));
        }

        return;
    }

    /**
     * Prints the complete dependency tree to the debug log.
     *
     * @param Tree The Tree to display.
     */
    public static void showDependencyTree(PluginAndDependencies Tree) {

        System.out.println("RPAD: Showing Tree for " + PluginAPI.GetPluginIdentifier(Tree.Plugin));

        if (isRoot(Tree)) {
            System.out.println("RPAD: == It is the root.");
        } else {
            System.out.println("RPAD: == It's parent is " + PluginAPI.GetPluginIdentifier(Tree.Parent));
        }

        if (Tree.Dependencies != null && !Tree.Dependencies.isEmpty()) {

            System.out.println("RPAD: == It has " + Tree.Dependencies.size() + " dependencies.");

            for (PluginAndDependencies Dependency : Tree.Dependencies) {
                System.out.println("RPAD:   == Dependency = " + PluginAPI.GetPluginIdentifier(Dependency.Plugin));
            }

            for (PluginAndDependencies Dependency : Tree.Dependencies) {
                showDependencyTree(Dependency);
            }
        } else {
            System.out.println("RPAD: == It has no dependencies.");
        }

        System.out.println("RPAD: == Completed Tree for " + PluginAPI.GetPluginIdentifier(Tree.Plugin));
    }

    /**
     * Gets a List of all the Plugins that are dependencies in the given PluginAndDependencies.  It does NOT
     * differentiate between installed, uninstalled, needed or unneeded.
     *
     * The Plugins will be returned in reverse-dependency order, so the dependencies can be removed in
     * the order they are returned.
     *
     * No Plugins are duplicated.
     *
     * It does NOT return the root of the Tree. (The Plugin that is the parent.)
     *
     * @param Tree The PluginAndDependencies tree to process.
     * @param CurrentList The current List of Plugins.  Use null to start.
     * @return A List of Plugins that are dependencies of the parent Plugin.
     */
    public static List<Object> getListOfDependencies(PluginAndDependencies Tree) {

        // Parameter check.
        if (Tree == null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "getListOfDependecies: Error. null Tree.");
            return null;
        }

        List<Object> NewList = new ArrayList<Object>();

        // Recursively add dependencies.
        if (Tree.Dependencies != null && !Tree.Dependencies.isEmpty()) {
            for (PluginAndDependencies Dependency : Tree.Dependencies) {
                Log.getInstance().write(Log.LOGLEVEL_TRACE, "getListOfDependecies: Processing dependency " + PluginAPI.GetPluginIdentifier(Dependency.Plugin));

                // Get the Set.
                List<Object> TempList = getListOfDependencies(Dependency);

                // If there are items in it add them, if not already added.
                if (TempList != null && !TempList.isEmpty()) {
                    for (Object Plugin : TempList) {
                        Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getListOfDependecies: Checking " + PluginAPI.GetPluginName(Plugin));
                        if (!listContainsPlugin(NewList, Plugin)) {
                            Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getListOfDependecies: Adding it.");
                            NewList.add(Plugin);
                        }
                    }
                }
            }
        }

        // Add in this Plugin if it's not the root and it's not already added.
        if (!listContainsPlugin(NewList, Tree.Plugin) && !isRoot(Tree)) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "getListOfDependecies: Finally, adding " + PluginAPI.GetPluginName(Tree.Plugin));
            NewList.add(Tree.Plugin);
        }

        return NewList;
    }

    public static void showDependencyList(List<Object> Plugins) {

        System.out.println("RPAD: Showing dependencies.");

        if (Plugins == null || Plugins.isEmpty()) {
            System.out.println("RPAD: = None or null.");
            return;
        }

        for (Object Plugin : Plugins) {
            System.out.println("RPAD: = " + PluginAPI.GetPluginIdentifier(Plugin));
        }

    }

    /**
     * Determines if a Plugin is needed by any of the Plugins in the List
     * of PluginAndDependencies.
     *
     * @param The Tree for the Plugin.
     * @param Dependencies.
     * @return
     */
    public static boolean isNeeded(Object Plugin, List<PluginAndDependencies> Dependencies) {

        // Parameter check.
        if (Plugin == null || Dependencies == null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "isNeeded: Found null Plugin or Dependencies.");
            return false;
        }

        for (PluginAndDependencies Dependency : Dependencies) {

            // Check if we have a bad parameter.
            if (Dependency == null) {
                Log.getInstance().write(Log.LOGLEVEL_WARN, "isNeeded: Found null Dependency.");
                return false;
            }

            // Skip (not needed) if we are comparing ThePlugin to itself.
            if (!(PluginsAreEqual(Plugin, Dependency.Plugin) && isRoot(Dependency))) {

                Log.getInstance().write(Log.LOGLEVEL_TRACE, "isNeeded: Checking equality and isNeeded for " + PluginAPI.GetPluginName(Dependency.Plugin));

                // Return true if it's needed by this Plugin or any of its dependencies.
                if (PluginsAreEqual(Plugin, Dependency.Plugin) || isNeeded(Plugin, Dependency.Dependencies)) {
                    return true;
                }
            }
        }

        // It wasn't needed.
        return false;
    }

    /**
     * See if the specified Plugin is only needed in the dependency tree provided.
     *
     * @param Plugin
     * @param Tree
     * @param InstalledPlugins
     * @return
     */
    public static boolean OLDisOnlyNeededBy(Object Plugin, PluginAndDependencies Tree, List<Object> InstalledPlugins) {

        // Parameter check.
        if (Plugin == null || Tree == null || InstalledPlugins == null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "isOnlyNeededBy: Found null parameter.");
            return false;
        }

        // See what Plugins use this Plugin.
        List<String> UsedInPlugins = getPluginIDsThatUse(Plugin, InstalledPlugins);

        // Error check.
        if (UsedInPlugins == null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "isOnlyNeededBy: null UsedIn.");
            return false;
        }

        for (String PluginID : UsedInPlugins) {

            Object ThisPlugin = getPluginForID(PluginID, InstalledPlugins);

            if (ThisPlugin != null) {

                for (Object InstalledPlugin : InstalledPlugins) {

                    // Skip if we are comparing Plugin to itself.
                    if (!PluginsAreEqual(ThisPlugin, Plugin)) {

                        // Create a Tree for this plugin.
                        PluginAndDependencies InstalledPluginTree = new PluginAndDependencies(InstalledPlugin, InstalledPlugin);

                        // See if the Tree contains it.
                        if (treeContainsPlugin(InstalledPluginTree, ThisPlugin)) {
                            return false;
                        }
                    }
                }
            } else {
                Log.getInstance().write(Log.LOGLEVEL_WARN, "isOnlyNeededBy: Found null Plugin.");
            }
        }

        return true;
        
    }

    public static boolean isOnlyNeededBy(Object PluginToCheck, Object PluginToCheckAgainst, List<Object> InstalledPlugins) {

        // Parameter check.
        if (PluginToCheck == null || PluginToCheckAgainst == null || InstalledPlugins == null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "isOnlyNeededBy: Found null parameter.");
            return false;
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "isOnlyNeededBy: PluginToCheck and PluginToCheckAgainst = " + PluginAPI.GetPluginName(PluginToCheck) + ":" + PluginAPI.GetPluginName(PluginToCheckAgainst));

        // See what Plugins use this Plugin.
        List<String> UsedInPlugins = getPluginIDsThatUse(PluginToCheck, InstalledPlugins);

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "isOnlyNeededBy: Plugin and UsedInPlugins = " + PluginAPI.GetPluginName(PluginToCheck) + ":" + UsedInPlugins);

        // Error check.
        if (UsedInPlugins == null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "isOnlyNeededBy: null UsedIn.");
            return false;
        }

        // If no Plugins use the PluginToCheck we know it isOnlyUsedBy PluginToCheckAgainst.
        if (UsedInPlugins.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "isOnlyNeededBy: UsedInPlugins in empty.");
            return true;
        }

        // See if any of the Plugins that use PluginToCheck are not only needed by PluginToCheckAgainst.
        for (String PluginID : UsedInPlugins) {

            Object PluginThatUses = getPluginForID(PluginID, InstalledPlugins);

            if (PluginThatUses != null) {

                Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "isOnlyNeededBy: Checking UsedInPlugin " + PluginAPI.GetPluginName(PluginThatUses));

                // Skip if we are comparing Plugin to itself.
                if (!PluginsAreEqual(PluginToCheckAgainst, PluginThatUses)) {

                    Log.getInstance().write(Log.LOGLEVEL_TRACE, "isOnlyNeededBy: Recursing " + PluginAPI.GetPluginName(PluginThatUses));

                    // If the PluginToCheckAgainst does not even use PluginThatUses, we know it not only needed by.
                    if (!pluginUses(PluginToCheckAgainst, PluginThatUses)) {
                        return false;
                    }

                    // See if it's also only needed by PluginToCheckAgainst.
                    if (!isOnlyNeededBy(PluginThatUses, PluginToCheckAgainst, InstalledPlugins)) {
                        Log.getInstance().write(Log.LOGLEVEL_TRACE, "isOnlyNeededBy: returning false.");
                        return false;
                    }
                } else {
                    Log.getInstance().write(Log.LOGLEVEL_TRACE, "isOnlyNeededBy: Plugins are equal.");
                }

            } else {
                Log.getInstance().write(Log.LOGLEVEL_WARN, "isOnlyNeededBy: Found null Plugin.");
            }
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "isOnlyNeededBy: returning true.");
        return true;

    }

    private static boolean pluginUses(Object ThisPlugin, Object ThatPlugin) {

        List<PluginAndDependencies> TheList = new ArrayList<PluginAndDependencies>();
        PluginAndDependencies Tree = new PluginAndDependencies(ThisPlugin, ThisPlugin);
        TheList.add(Tree);

        return isNeeded(ThatPlugin, TheList);
    }

    /**
     * Return the installed Plugin with the ID specified.
     *
     * @param ID
     * @param InstalledPlugins
     * @return
     */
    public static Object getPluginForID(String ID, List<Object> InstalledPlugins) {

        for (Object Plugin : InstalledPlugins) {
            if (ID.compareToIgnoreCase(PluginAPI.GetPluginIdentifier(Plugin)) == 0)
                return Plugin;
        }

        return null;
    }

    public static List<Object> getPluginsThatAreNotDependencies(List<Object> InstalledPlugins) {

        List<Object> TheList = new ArrayList<Object>();

        if ((InstalledPlugins == null) || (InstalledPlugins.isEmpty()))  {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "getPluginsThatAreNoDependencies: Found null or empty parameter.");
            return TheList;
        }

        for (Object Plugin : InstalledPlugins) {
            if (getPluginIDsThatUse(Plugin, InstalledPlugins).isEmpty()) {
                TheList.add(Plugin);
            }
        }

        return TheList;
    }

    /*
     * See if thePlugin is contained in TheTree or any of its dependencies.
     */
    private static boolean treeContainsPlugin(PluginAndDependencies TheTree, Object ThePlugin) {

        // Parameter check.
        if (ThePlugin == null || TheTree == null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "treeContainsPlugin: Found null parameter.");
            return false;
        }

        // See if it matches the current Plugin.
        if (PluginsAreEqual(ThePlugin, TheTree.Plugin)) {
            return true;
        }

        // See if it matches any of the other dependencies.
        for (PluginAndDependencies Dependency : TheTree.Dependencies) {
            if (treeContainsPlugin(Dependency, ThePlugin)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Looks through the InstalledPlugins List and returns the plugins that use Plugin.
     *
     * @param Plugin The Plugin that we are interested in.
     * @param InstalledPlugins The List of Plugins to scan.
     * @return A List of Plugins that use the referenced Plugin.
     */
    public static List<String> getPluginNamesThatUse(Object Plugin, List<Object> InstalledPlugins) {

        List<String> NewList = new ArrayList<String>();

        if (Plugin == null || InstalledPlugins == null || InstalledPlugins.isEmpty())
            return NewList;

        String ThisID = PluginAPI.GetPluginIdentifier(Plugin);

        for (Object ThisPlugin : InstalledPlugins) {

            if (!PluginAndDependencies.PluginsAreEqual(Plugin, ThisPlugin)) {
                List<String> DependencyIDs = getDependencyIDs(ThisPlugin);

                if (DependencyIDs.contains(ThisID)) {
                    NewList.add(PluginAPI.GetPluginName(ThisPlugin));
                }
            }

        }

        return NewList;

    }

    public static List<String> getPluginIDsThatUse(Object Plugin, List<Object> InstalledPlugins) {

        List<String> NewList = new ArrayList<String>();

        if (Plugin == null || InstalledPlugins == null || InstalledPlugins.isEmpty())
            return NewList;

        String ThisID = PluginAPI.GetPluginIdentifier(Plugin);

        for (Object ThisPlugin : InstalledPlugins) {

            if (!PluginAndDependencies.PluginsAreEqual(Plugin, ThisPlugin)) {
                List<String> DependencyIDs = getDependencyIDs(ThisPlugin);

                if (DependencyIDs.contains(ThisID)) {
                    NewList.add(PluginAPI.GetPluginIdentifier(ThisPlugin));
                }
            }

        }

        return NewList;
    }

    /*
     * Returns true if this Tree is the root, false otherwise.
     */
    private static boolean isRoot(PluginAndDependencies Tree) {
        return PluginsAreEqual(Tree.Parent, Tree.Plugin);
    }

    private static List<Object> getPluginDependencies(Object Plugin) {

        List<Object> Dependencies = new ArrayList<Object>();

        if (Plugin == null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "getPluginDependencies: null Plugin.");
            return Dependencies;
        }

        List<String> Descriptions = getDependencyDescriptions(Plugin);
        Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getPluginDependencies: Descriptions = " + Descriptions);

        if (Descriptions.size() == 0) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "getPluginDependencies: No dependencies.");
            return Dependencies;
        }

        List<Object> PluginsForDependencies = new ArrayList<Object>();

        for (String Description : Descriptions) {
            if (Description != null) {

                Log.getInstance().write(Log.LOGLEVEL_ALL, "getPluginDependencies: Description = " + Description);

                Object P = getPluginForDescription(Description);

                if (P != null) {
                    PluginsForDependencies.add(P);
                    Log.getInstance().write(Log.LOGLEVEL_ALL, "getPluginDependencies: Adding " + PluginAPI.GetPluginDescription(P));
                }

            }
        }

        return PluginsForDependencies;
    }

    /*
     * May return null if Description does not match any Plugin.
     */
    private static Object getPluginForDescription(String Description) {
        String ID = createIdFromDescription(Description);
        Log.getInstance().write(Log.LOGLEVEL_ALL, "getPluginForDescription: ID " + ID);
        return (Description == null ? null : PluginAPI.GetAvailablePluginForID(ID));
    }

    /*
     * Takes a description in the format "Type: ID xxx" and returns the ID.
     */
    private static String createIdFromDescription(String Description) {

        if (Description == null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "createIdFromDescription: null Description.");
            return null;
        }

        String[] Parts = Description.split(" ");
        if (Parts.length > 1)
            Log.getInstance().write(Log.LOGLEVEL_ALL, "createIdFromDescription: Description and Parts[1] = " + Description + "&" + Parts[1]);
        return (Parts.length > 1 ? Parts[1] : "UNKNOWN");
    }

      /*
     * Will never return null.
     */
    private static List<String> getDependencyDescriptions(Object Plugin) {
        List<String> Descriptions = new ArrayList<String>();

        if (Plugin == null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "getDependencyDescriptions: null Plugin.");
            return Descriptions;
        }

        String[] DescriptionArray = PluginAPI.GetPluginDependencies(Plugin);

        if (DescriptionArray == null) {
            Log.getInstance().write(Log.LOGLEVEL_WARN, "getDependencyDescriptions: null DescriptionArray.");
            return Descriptions;
        }

        if (DescriptionArray.length != 0) {
            Descriptions = Arrays.asList(DescriptionArray);
        }

        Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getDependencyDescriptions: Descriptions " + Descriptions);
        return Descriptions;
    }

    /*
     * Will never return null.
     */
    private static List<String> getDependencyIDs(Object Plugin) {

        List<String> IDs = new ArrayList<String>();

        if (Plugin == null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "getDependencyIDs: null Plugin.");
            return IDs;
        }

        List<String> Descriptions = getDependencyDescriptions(Plugin);

        for (String D : Descriptions) {
            if (D != null)
                IDs.add(createIdFromDescription(D));
        }

        Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "getDependencyIDs: IDs = " + IDs);
        return IDs;
    }

    /*
     * Helper method needed because .equals and .contains do not work for Sage Plugin Objects.
     */
    public static boolean PluginsAreEqual(Object P1, Object P2) {
        if (P1 == null || P2 == null) {
            return false;
        }

        String Name1 = PluginAPI.GetPluginIdentifier(P1);
        String Name2 = PluginAPI.GetPluginIdentifier(P2);

        if (Name1 == null || Name2 == null) {
            return false;
        }

        return Name1.compareToIgnoreCase(Name2) == 0;
    }

    /*
     * Helper method needed because .equals and .contains do not work for Sage Plugin Objects.
     */
    private static boolean listContainsPlugin(List<Object> List, Object Plugin) {

        if (List == null || Plugin == null || List.isEmpty())
            return false;

        for (Object P : List)
            if (PluginsAreEqual(P, Plugin))
                return true;

        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PluginAndDependencies other = (PluginAndDependencies) obj;
        if (this.Plugin != other.Plugin && (this.Plugin == null || !this.Plugin.equals(other.Plugin))) {
            return false;
        }
        if (this.Parent != other.Parent && (this.Parent == null || !this.Parent.equals(other.Parent))) {
            return false;
        }
        if (this.Dependencies != other.Dependencies && (this.Dependencies == null || !this.Dependencies.equals(other.Dependencies))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 31 * hash + (this.Plugin != null ? this.Plugin.hashCode() : 0);
        hash = 31 * hash + (this.Parent != null ? this.Parent.hashCode() : 0);
        hash = 31 * hash + (this.Dependencies != null ? this.Dependencies.hashCode() : 0);
        return hash;
    }

}
