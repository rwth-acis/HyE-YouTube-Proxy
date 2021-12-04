package i5.las2peer.services.hyeYouTubeProxy.lib;

public abstract class Util {
    public static boolean isBlankSpace(char c) {
        return (c == ' ' || c == '\t' || c == '\r' || c == '\n' || Character.isWhitespace(c));
    }

    public static boolean isAlphaNumeric(char c) {
        return ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
                Character.isLetterOrDigit(c));
    }
}
