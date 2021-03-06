/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package tmiranda.cd;

import java.io.*;
import java.util.*;
import sagex.api.*;

/**
 * Provide methods that enhance the Sage MediaFile Object.
 *
 * @author Tom Miranda
 */
public class CommercialDetectorMediaFile {

    Object MediaFile = null;
    int mediaFileID;
    boolean deleted;

    public CommercialDetectorMediaFile(Object SageMediaFile, boolean isDeleted) {
        MediaFile = SageMediaFile;
        deleted = isDeleted;
        mediaFileID = MediaFileAPI.GetMediaFileID(MediaFile);
    }

    public CommercialDetectorMediaFile(Object SageMediaFile) {
        MediaFile = SageMediaFile;
        deleted = false;
        mediaFileID = MediaFileAPI.GetMediaFileID(MediaFile);
    }

    public synchronized boolean queue() {

        File Parent = MediaFileAPI.GetParentDirectory(MediaFile);
        File[] files = MediaFileAPI.GetSegmentFiles(MediaFile);
        int ID = MediaFileAPI.GetMediaFileID(MediaFile);

        if (files[0]==null || files.length==0) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.queue: null or empty segment 0.");
            return false;
        }

        String Path = Parent.getAbsolutePath();
        if (Path==null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.queue: null path.");
            return false;
        }

        // Check if we need to remap drives to UNC paths.
        String UNCMap = Configuration.GetProperty(plugin.PROPERTY_DRIVE_MAP, plugin.PROPERTY_DEFAULT_DRIVE_MAP);
        if (!(UNCMap==null || UNCMap.isEmpty())) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.queue: Found UNC mappings " + UNCMap);
            Path = remapDrive(Path, UNCMap);
        }

        // Get a list of all the files that make up this MediaFile.
        List<String> FilesToProcess = new ArrayList<String>();

        for (File f : files) {
            String name = CreateFullPath(Path, f.getName());
            if (name==null)
                Log.getInstance().write(Log.LOGLEVEL_WARN, "CommercialDetectorMediaFile.queue: null name skipped.");
            else
                FilesToProcess.add(name);
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.queue: FilesToProcess = " + FilesToProcess);

        // Add this job to the queue on disk.
        QueuedJob NewJob = new QueuedJob(FilesToProcess, ID);

        if (!ComskipManager.getInstance().addToQueue(NewJob)) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.queue: Error from addToQueue.");
            return false;
        }

        ComskipManager.getInstance().startMaxJobs();

        return true;
    }

    public boolean cleanup() {

        // Get the string of comma delimited video file extensions.
        String VideoExt = Configuration.GetServerProperty(plugin.PROPERTY_VIDEO_FILE_EXTENSIONS, plugin.PROPERTY_DEFAULT_VIDEO_FILE_EXTENSIONS);

        if (VideoExt==null || VideoExt.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.cleanup: No video extensions specified. Assuming defaults.");
            VideoExt = plugin.PROPERTY_DEFAULT_VIDEO_FILE_EXTENSIONS;
        }

        // Split them up.
        String[] VideoExtensions = VideoExt.split(",");

        if (VideoExt==null || VideoExt.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.cleanup: Malformed video extension. Assuming defaults " + VideoExt);
            VideoExtensions = plugin.ARRAY_VIDEO_EXTENSIONS;
        }

        // Get the string of comma delimited cleanup extensions.
        String CleanupExt = Configuration.GetServerProperty(plugin.PROPERTY_CLEANUP_EXTENSIONS, plugin.PROPERTY_DEFAULT_CLEANUP_EXTENSIONS);

        if (CleanupExt==null || CleanupExt.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.cleanup: No extensions to cleanup.");
            return true;
        }

        // Split them up.
        String[] Extensions = CleanupExt.split(",");

        if (CleanupExt==null || Extensions.length==0) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.cleanup: Invalid cleanup extension string " + CleanupExt);
            return false;
        }

        // Get the directory that the file is in.
        File Parent = MediaFileAPI.GetParentDirectory(MediaFile);
        if (Parent==null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.cleanup: null Parent.");
            return false;
        }

        // Convert the directory into a String.
        String Path = Parent.getAbsolutePath();
        if (Path==null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.cleanup: null Path.");
            return false;
        }

        // Check if we need to remap drives to UNC paths.
        String UNCMap = Configuration.GetProperty(plugin.PROPERTY_DRIVE_MAP, plugin.PROPERTY_DEFAULT_DRIVE_MAP);
        if (!(UNCMap==null || UNCMap.isEmpty())) {
            Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.cleanup: Found UNC mappings " + UNCMap);
            Path = remapDrive(Path, UNCMap);
        }

        // See how many files make up this MediaFile. Sage will always return 1, even if the deleted MediaFile
        // had multiple segments, so we need to do a manual search for files ending in -XXX.
        List<File> VideoFiles = getVideoFiles();

        // Cleanup all of the files for all of the segments.
        for (File FileName : VideoFiles) {

            if (FileName==null) {
                Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.cleanup: null FileName.");
                return false;
            }

            // Get the fully qualified path to this file.
            String FullName = CreateFullPath(Path, FileName.getName());

            // Split into pieces separated by "."
            String[] NameExt = FullName.split("\\.");

            // Get the extension.
            String MediaFileExt = null;

            if (NameExt.length==1) {

                // No extension.
                MediaFileExt = "";
            }  else if (NameExt.length == 2) {

                // Most common case: "filename.ext"
                MediaFileExt = NameExt[1];
            } else {
                int lastIndex = FullName.lastIndexOf(".");
                MediaFileExt = lastIndex == -1 ? "" : FullName.substring(lastIndex+1);
            }

            String FileNameToDelete = null;

            for (String Extension : Extensions) {

                if (NameExt.length==2) {

                    // Most common case: "filename.ext"
                    FileNameToDelete = NameExt[0] + "." + Extension;

                } else if (NameExt.length>=2) {

                    // Name has multiple "." in it: "filename.x.y.ext"
                    FileNameToDelete = NameExt[0] + ".";
                    for (int i=1; i<(NameExt.length); i++) {
                        FileNameToDelete = FileNameToDelete + NameExt[i] + ".";
                    }
                    FileNameToDelete = FileNameToDelete + Extension;
                } else {

                    // Something bad happened.
                    Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.cleanup: Malformed FullName " + FullName);
                    FileNameToDelete = "ERROR.ERROR";
                }

                if (hasAnyVideoFiles(FileNameToDelete, VideoExtensions, MediaFileExt)) {
                    Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.cleanup: MediaFile still has corresponding video file.");
                    return true;
                }

                Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.cleanup: Attempting to delete " + FileNameToDelete);

                File FileToDelete = new File(FileNameToDelete);

                if (FileToDelete==null) {
                    Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.cleanup: null FileToDelete.");
                } else {
                    if (FileToDelete.exists()) {
                        if (FileToDelete.delete()) {
                            Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.cleanup: File deleted " + FileNameToDelete);
                        } else {
                            Log.getInstance().write(Log.LOGLEVEL_WARN, "CommercialDetectorMediaFile.cleanup: File could not be deleted, will try on exit " + FileNameToDelete);
                            FileToDelete.deleteOnExit();
                        }
                    }
                }
            }
        }

        return true;
    }

    private static String CreateFullPath(String Path, String FileName) {
        if (Path.contains("/"))
            return Path + "/" + FileName;
        else
            return Path + "\\" + FileName;
    }

    private String remapDrive(String Path, String UNCMap) {

        // Split the string into Drive-Path pairs;
        String[] Pairs = UNCMap.split(",");
        if (Pairs==null || Pairs.length==0) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.remapDrive: Invalid mappings.");
            return Path;
        }

        String[] DrivePath = Path.split(":");
        if (DrivePath==null || DrivePath.length==0) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.remapDrive: Invalid Path.");
            return Path;
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.remapDrive: Drive = " + DrivePath[0]);

        for (String Pair : Pairs) {

            // Split the string into a Drive and a UNC Path.
            String[] Map = Pair.split("-");
            if (Map==null || Map.length==0) {
                Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.remapDrive: No mappings.");
                return Path;
            }

            // See if the drive matches the drive in Path.
            if (Map[0].equalsIgnoreCase(DrivePath[0])) {
                Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.remapDrive: Found Drive match. Remapping " + DrivePath[0] + "->"+ Map[1] + DrivePath[1]);
                return Map[1] + DrivePath[1];
            }
        }

        return Path;
    }

    private boolean hasAnyVideoFiles(String FileName, String[] VideoExtensions, String MediaFileExt) {
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.hasAnyVideoFiles: Looking for video file for " + FileName + ":" + MediaFileExt);

        int dotPos = FileName.lastIndexOf(".");

        if (dotPos<0) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.hasAnyVideoFiles: Failed to find extension." + FileName);
            return false;
        }

        String BaseFileName = FileName.substring(0, dotPos);

        BaseFileName = BaseFileName + ".";

        for (String Extension : VideoExtensions) {

            if (Extension.equalsIgnoreCase(MediaFileExt)) {
                Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.hasAnyVideoFiles: Skipping deleted file extension " + MediaFileExt);
            } else {
                File FileToTry = new File(BaseFileName+Extension);

                Log.getInstance().write(Log.LOGLEVEL_VERBOSE, "CommercialDetectorMediaFile.hasAnyVideoFiles: Looking for " + BaseFileName+Extension);

                if (FileToTry==null) {
                    Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.hasAnyVideoFiles: null FileToTry.");
                } else {
                    if (FileToTry.exists()) {

                        Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.hasAnyVideoFiles: Found match, checking for deleted MediaFile " + BaseFileName+Extension);
                        
                        if (!deleted) {
                            Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.hasAnyVideoFiles: This is not a deleted MediaFile.");
                            return true;
                        } else {
                            Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.hasAnyVideoFiles: This is a deleted MediaFile.");
                            File VidFile = MediaFileAPI.GetFileForSegment(MediaFile, 0);

                            if (VidFile == null) {
                                Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.hasAnyVideoFiles: Null VidFile.");
                                return true;
                            }

                            String VidFileString = VidFile.getAbsolutePath();
                            Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.hasAnyVideoFiles: Comparing " + VidFileString + ":" + BaseFileName+Extension);
                            
                            if (!VidFileString.equalsIgnoreCase(BaseFileName+Extension))
                                return true;
                        }
                    }
                }
            }
        }

        // No matches found.
        return false;
    }

    /**
     * Returns a List of File that represent all of the video files that make up this particular MediaFile.
     * @return A List of Files that are video files for the Sage MediaFile.
     */
    public List<File> getVideoFiles() {

        List<File> VideoFiles = new ArrayList<File>();

        int NumberOfSegments = MediaFileAPI.GetNumberOfSegments(MediaFile);
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.getVideoFiles: Segments for MediaFile " + NumberOfSegments);

        if (NumberOfSegments==0) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.getVideoFiles: 0 segments.");
            return VideoFiles;
        }

        // If GetNumberOfSegments returns something greater than 1 we know the MediaFile is not deleted and
        // we can use the normal Sage API to get a list of the Files.
        if (NumberOfSegments>1) {

            Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.getVideoFiles: File has multiple segments.");

            for (int Segment=0; Segment<NumberOfSegments; Segment++) {

                File FileName = MediaFileAPI.GetFileForSegment(MediaFile, Segment);

                if (FileName==null) {
                    Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.getVideoFiles: null FileName.");
                    return VideoFiles;
                }

                Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.getVideoFiles: Video file " + FileName.getAbsolutePath());
                VideoFiles.add(FileName);
            }

            return VideoFiles;
        }
        
        // Check to see if there really is only 1 segment, there may be more because if a MediaFile is deleted
        // Sage always reports back there this 1 segment even if multiple segments did exist.
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.getVideoFiles: File has one segment, checking for more.");

        // Get the first segment (this should always exist).
        File CompleteFile = MediaFileAPI.GetFileForSegment(MediaFile, 0);

        if (CompleteFile==null) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.getVideoFiles: null CompleteFile.");
            return VideoFiles;
        }

        // Get the Path, which is the directory where the file is located.
        String Path = CompleteFile.getParent();
        if (Path==null || Path.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.getVideoFiles: null Path.");
            return VideoFiles;
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.getVideoFiles: Path " + Path);

        // Get the name of the file minus the directory path information.
        String Name = CompleteFile.getName();
        if (Name==null || Name.isEmpty()) {
            Log.getInstance().write(Log.LOGLEVEL_ERROR, "CommercialDetectorMediaFile.getVideoFiles: null Name.");
            return VideoFiles;
        }

        // Get the name minus the "-xxx" where xxx is the segment number.
        int lastIndex = Name.lastIndexOf("-");
        String StartOfName = lastIndex == -1 ? Name : Name.substring(0, lastIndex);
        Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.getVideoFiles: StartOfName " + StartOfName);

        // Now get a listing of all the files in the directory that start with the name we are interested in.
        File DirectoryFile = new File(Path);

        FilenameFilter StartsWith = new FileNameStartsWith(StartOfName);

        File[] FileNames = DirectoryFile.listFiles(StartsWith);

        if (FileNames!=null) {
            VideoFiles = Arrays.asList(FileNames);
        }

        Log.getInstance().write(Log.LOGLEVEL_TRACE, "CommercialDetectorMediaFile.getVideoFiles: Found VideoFiles " + VideoFiles.size());
        return VideoFiles;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CommercialDetectorMediaFile other = (CommercialDetectorMediaFile) obj;
        if (this.mediaFileID != other.mediaFileID) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        return hash;
    }
}

/**
 * Class used to get a list of Files in a directory that start with a particular String.
 *
 * @author Tom Miranda
 */
class FileNameStartsWith implements FilenameFilter {
    String Start;

    public FileNameStartsWith(String NameStart) {
        Start = NameStart;
    }

    @Override
    public boolean accept(File F, String Name) {
        return Name.startsWith(Start);
    }
}
