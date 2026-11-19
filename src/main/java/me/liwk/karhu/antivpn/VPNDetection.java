package me.liwk.karhu.antivpn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Lookup against VPNDetection's keyless API (https://vpndetection.io/terms#no-key).
 */
public final class VPNDetection {

    private VPNDetection() {
    }

    /**
     * Blocking lookup, must never be called from the main thread.
     *
     * @return whether the address is a VPN, Tor exit or privacy relay, or null when the API gave no answer
     */
    public static Boolean isAnonymous(String address) throws IOException {
        HttpURLConnection request = (HttpURLConnection) new URL("https://api.vpndetection.io/" + address).openConnection();
        request.setRequestProperty("User-Agent", "KarhuAC");
        request.setConnectTimeout(3000);
        request.setReadTimeout(4000);

        if (request.getResponseCode() != HttpURLConnection.HTTP_OK) {
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8)) {
            return parse(new JsonParser().parse(reader).getAsJsonObject());
        }
    }

    static Boolean parse(JsonObject data) {
        if (!data.has("is_vpn")) {
            return null;
        }

        return flag(data, "is_vpn") || flag(data, "is_tor") || flag(data, "is_relay");
    }

    private static boolean flag(JsonObject data, String key) {
        JsonElement value = data.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }
}
