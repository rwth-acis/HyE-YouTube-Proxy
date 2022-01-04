package i5.las2peer.services.hyeYouTubeProxy.lib;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

public abstract class ParserUtil {

    public static boolean isBlankSpace(char c) {
        return (c == ' ' || c == '\t' || c == '\r' || c == '\n' || Character.isWhitespace(c));
    }

    public static boolean isAlphaNumeric(char c) {
        return ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
                Character.isLetterOrDigit(c));
    }

    public static JsonObject toJsonObject(String jsonString) {
        return new Gson().fromJson(jsonString, JsonObject.class);
    }

    public static JsonArray toJsonArray(String arrayString) { return new Gson().fromJson(arrayString, JsonArray.class); }
    public static JsonArray toJsonArray(ArrayList<?> arrayList) { return toJsonArray(toJsonString(arrayList)); }

    public static String toJsonString(ArrayList<?> arrayList) {
        return new Gson().toJson(arrayList);
    }

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

    public static ArrayList<String> jsonToArrayList(JsonArray jsonArray) {
        ArrayList<String> result = new ArrayList<String>();
        Iterator<JsonElement> it = jsonArray.iterator();
        while (it.hasNext()) {
            result.add(it.next().getAsString());
        }
        return result;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
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
