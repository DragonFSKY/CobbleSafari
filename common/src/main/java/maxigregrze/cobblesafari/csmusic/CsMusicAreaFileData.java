package maxigregrze.cobblesafari.csmusic;

import java.util.ArrayList;
import java.util.List;

public class CsMusicAreaFileData {
    public List<AreaEntry> areas = new ArrayList<>();

    public static class AreaEntry {
        public String id;
        /** Optional: absent/blank = geometry-only area, playing nothing by itself. */
        public String music;
        /** Optional: free-form tags targetable by a rule's {@code when.area_tag}. */
        public List<String> tags = new ArrayList<>();
        public boolean activated;
        /** 0 or missing = use config {@code defaultAreaPriority} (backward compatible). */
        public int priority;
        public List<BoxEntry> boxes = new ArrayList<>();
    }

    public static class BoxEntry {
        public int[] min;
        public int[] max;
    }
}
