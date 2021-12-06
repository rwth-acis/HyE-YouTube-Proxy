package i5.las2peer.services.hyeYouTubeProxy.lib;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Set;
import java.util.ArrayList;
import java.util.Iterator;

public abstract class Util {

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

    public static JsonArray toJsonArray(String arrayString) {
        return new Gson().fromJson(arrayString, JsonArray.class);
    }

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
}
