
package tmiranda.podcastrecorder;

import java.io.*;

/**
 *
 * @author Default
 */
public class EpisodeData implements Serializable {

    private static final long serialVersionUID = 1L;

    Podcast podcast;
    String  ID;
    int     AiringID;

    public EpisodeData(Episode e) {
        podcast = e.podcast;
        ID = e.ID;
        AiringID = e.AiringID;
    }
}
