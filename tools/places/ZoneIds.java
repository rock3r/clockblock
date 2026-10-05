import java.time.ZoneId;

/**
 * Prints every zone id the JDK's {@code java.time} knows about, one per line, sorted.
 * Run with {@code java tools/places/ZoneIds.java} (single-file source launch, JDK 11+).
 * {@code build_places.py} uses this to validate the dataset against exactly what the app will parse.
 */
public class ZoneIds {
    public static void main(String[] args) {
        ZoneId.getAvailableZoneIds().stream().sorted().forEach(System.out::println);
    }
}
