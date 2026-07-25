package maxigregrze.cobblesafari.csmusic;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compiled {@code when.structure_piece} pattern: a glob over a structure piece id where {@code *}
 * matches any run of characters (including {@code /}). Everything else is literal - the pattern is
 * built from {@link Pattern#quote(String)} chunks, so a datapack cannot inject regex syntax.
 *
 * <p>The target case is "any house of any village", i.e.
 * {@code minecraft:village/*&#47;houses/*}, which needs a wildcard in the middle of the path.</p>
 */
public record CsMusicPiecePattern(String raw, Pattern compiled) {

    /** Compiles the json value; returns {@code null} when it is blank. */
    @Nullable
    public static CsMusicPiecePattern fromJson(String rawValue) {
        String glob = rawValue.trim().toLowerCase(Locale.ROOT);
        if (glob.isEmpty()) {
            return null;
        }
        StringBuilder regex = new StringBuilder("^");
        int from = 0;
        while (true) {
            int star = glob.indexOf('*', from);
            if (star < 0) {
                if (from < glob.length()) {
                    regex.append(Pattern.quote(glob.substring(from)));
                }
                break;
            }
            if (star > from) {
                regex.append(Pattern.quote(glob.substring(from, star)));
            }
            regex.append(".*");
            from = star + 1;
        }
        return new CsMusicPiecePattern(glob, Pattern.compile(regex.append('$').toString()));
    }

    public boolean matches(String pieceId) {
        return compiled.matcher(pieceId).matches();
    }
}
