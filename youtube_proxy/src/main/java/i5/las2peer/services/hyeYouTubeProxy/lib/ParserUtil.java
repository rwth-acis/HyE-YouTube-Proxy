package i5.las2peer.services.hyeYouTubeProxy.lib;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * ParserUtil
 *
 * Contains a collection of generally useful functions while parsing
 */

public abstract class ParserUtil {

    /**
     * Identifies blank space characters, such as tabs, spaces, and line breaks
     *
     * @param c The character in question
     * @return Boolean value whether given character is a whitespace character
     */
    public static boolean isBlankSpace(char c) {
        return (c == ' ' || c == '\t' || c == '\r' || c == '\n' || Character.isWhitespace(c));
    }

    /**
     * Identifies alphanumeric characters (0-9, A-z)
     *
     * @param c The character in question
     * @return Boolean value whether given character is an alphanumeric character
     */
    public static boolean isAlphaNumeric(char c) {
        return ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
                Character.isLetterOrDigit(c));
    }

    /**
     * Transforms the given string into a Json element
     *
     * @param element String to transform
     * @return Given String as Json Element
     */
    public static JsonElement toJsonElement(String element) {
        return new Gson().fromJson(element, JsonElement.class);
    }

    /**
     * Transforms the given string into a Json object
     *
     * @param jsonString Json object string
     * @return Given String as Json Object
     */
    public static JsonObject toJsonObject(String jsonString) {
        return new Gson().fromJson(jsonString, JsonObject.class);
    }

    /**
     * Transforms the given string into a Json array
     *
     * @param arrayString Json array as string
     * @return Given String as Json Array
     */
    public static JsonArray toJsonArray(String arrayString) { return new Gson().fromJson(arrayString, JsonArray.class); }

    /**
     * Transforms the given array list into a Json array
     *
     * @param arrayList List to transform
     * @return Given array list as Json Array
     */
    public static JsonArray toJsonArray(ArrayList<?> arrayList) { return toJsonArray(toJsonString(arrayList)); }

    /**
     * Transforms the given hash set into a Json array
     *
     * @param hashSet Set to transform
     * @return Given hash set as Json Array
     */
    public static JsonArray toJsonArray(HashSet<?> hashSet) { return toJsonArray(toJsonString(hashSet)); }

    /**
     * Transforms the given array list into a Json array string
     *
     * @param arrayList List to transform
     * @return Given array list as a Json Array string
     */
    public static String toJsonString(ArrayList<?> arrayList) {
        return new Gson().toJson(arrayList);
    }

    /**
     * Transforms the given hash set into a Json array string
     *
     * @param arrayList Set to transform
     * @return Given hash set as a Json Array string
     */
    public static String toJsonString(HashSet<?> arrayList) {
        return new Gson().toJson(arrayList);
    }

    /**
     * Transforms the given json object into a hash map
     *
     * @param jsonObject The Json object to transform
     * @return Given Json Object as a hash map with strings for both keys and values
     */
    public static HashMap<String, String> jsonToMap(JsonObject jsonObject) {
        HashMap<String, String> result = new HashMap<String, String>();
        Set<String> keys = jsonObject.keySet();
        Iterator<String> it = keys.iterator();
        while (it.hasNext()) {
            String key = it.next();
            result.put(key, jsonObject.get(key).getAsString());
        }
        return result;
    }

    /**
     * Transforms the given json array into an array list
     *
     * @param jsonArray The Json object to transform
     * @return Given Json Object as an array list of strings
     */
    public static ArrayList<String> jsonToArrayList(JsonArray jsonArray) {
        ArrayList<String> result = new ArrayList<String>();
        Iterator<JsonElement> it = jsonArray.iterator();
        while (it.hasNext()) {
            result.add(it.next().getAsString());
        }
        return result;
    }


    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /**
     * Transforms the given byte array into a string in order to make it human-readable (or at least comparable)
     *
     * @param bytes The byte array to transform
     * @return Given byte array as a string of hexadecimal digits
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}
