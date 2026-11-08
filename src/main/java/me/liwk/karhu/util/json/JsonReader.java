package me.liwk.karhu.util.json;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.check.api.ViolationX;
import me.liwk.karhu.util.BanData;
import me.liwk.karhu.util.haste.Hastebin;
import me.liwk.karhu.util.text.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class JsonReader {

    private static JsonObject readIp(String address) throws IOException {
        //String sURL = "https://gatekeeper.fozzie.space/?ip=" + address + "&key=H7ehpxsq9$^xx*M@7Way6G4e7cgz!7w$LR^E!$yKW9JYEfQgmR"; //just a string
        String sURL = "http://proxycheck.io/v2/" + address + "?key=0q2s38-80419r-466076-h10l90&risk=1&vpn=1&asn=1";

        URL url = new URL(sURL);
        URLConnection request = url.openConnection();
        request.setRequestProperty("User-Agent", "KarhuAC");
        request.setConnectTimeout(3000);
        request.setReadTimeout(4000);
        request.connect();

        try (InputStreamReader reader = new InputStreamReader((InputStream) request.getContent(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    public static JsonObject sendBan(BanData ban) throws IOException {
        String sURL = "https://karhu.cc/api/bandata"; //just a string
        // Connect to the URL using java's native library
        HttpsURLConnection request = (HttpsURLConnection) new URL(sURL).openConnection();
        request.setDoOutput(true);
        request.setRequestMethod("POST");
        request.addRequestProperty("User-Agent", "KarhuAC");
        request.addRequestProperty("content-type", "application/json");
        request.addRequestProperty("Authorization", "gdY2P44WZtacyQQ4xmefvZxhUX6YcZEhmv6XEkA6");
        request.setReadTimeout(10000);
        request.connect();

        JsonObject json = new JsonObject();

        json.addProperty("license", ban.license);
        json.addProperty("karhu_version", ban.karhuVer);
        json.addProperty("server_version", ban.serverVer);

        json.addProperty("tps", ban.tps);
        json.addProperty("player", ban.player);
        json.addProperty("banned_for", ban.type);

        json.addProperty("client", ban.client);
        json.addProperty("time_played", ban.sessionTime);
        json.addProperty("coordinates", ban.coordinates);
        json.addProperty("ping", ban.ping);
        String[] paska = getHaste(ban);
        if(paska[0].length() > 1) {
            try {
                String hasteURL = Hastebin.uploadPaste(paska[0]);
                if(hasteURL != null) {
                    json.addProperty("logs", hasteURL);
                } else {
                    json.addProperty("logs", "Couldn't paste logs, maybe the file is too big?");
                }
            } catch (Exception e) {
                json.addProperty("logs", "Couldn't paste logs, maybe the file is too big? (" + e.getMessage() + ")");
            }
        } else {
            json.addProperty("logs", paska[1]);
        }

        byte[] out = json.toString().getBytes(StandardCharsets.UTF_8);

        try(OutputStream os = request.getOutputStream()) {
            os.write(out);
            os.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Gson gson = new Gson();

        return gson.fromJson(new InputStreamReader(request.getInputStream(), Charsets.UTF_8), JsonObject.class);

    }

    public static String[] getData(String address) throws IOException {

        JsonObject object = readIp(address);

        String proxy;
        String geoLocation;
        String risk;

        if(object.entrySet() != null) {

            for(Map.Entry<String, JsonElement> obj : object.entrySet()) {
                if(obj.getKey().equalsIgnoreCase(address)) {

                    JsonObject data = obj.getValue().getAsJsonObject();

                    proxy = data.get("proxy").getAsString().equalsIgnoreCase("yes") ? "true" : "false";

                    geoLocation = data.get("country").getAsString();

                    JsonElement testRisk = data.get("risk");

                    int riskLevel = testRisk == null ? 0 : testRisk.getAsInt();

                    risk = riskLevel > 50 ? "true" : "false";

                    return new String[]{proxy, geoLocation, risk};
                }

            }
        }

        return new String[]{null, null, null};
    }

    private static String[] getHaste(BanData data) {
        String uuid;
        if (Karhu.getInstance().getConfigManager().isCrackedServer()) {
            Player target = data.playerObj;

            if (target != null) {
                uuid = target.getName();
            } else {
                uuid = data.player;
            }
        } else {
            Player target = data.playerObj;

            if (target != null) {
                uuid = target.getUniqueId().toString();
            } else {
                uuid = Bukkit.getOfflinePlayer(data.player).getUniqueId().toString();
            }
        }

        List<ViolationX> vls = Karhu.storage.getAllViolations(uuid);

        if (vls.isEmpty()) {
            return new String[]{"", "Player has no logs!"};
        }

        StringBuilder end = new StringBuilder("Anticheat logs for player " + data.player + " pasted with " + Karhu.getInstance().getConfigManager().getName() + " " + Karhu.getInstance().getVersion());


            try {
                for (ViolationX v : vls) {
                    String logline = TextUtils.formatMillis(System.currentTimeMillis() - v.time) + " ago | " + ChatColor.stripColor(v.type).replaceAll("\n", " ") + " [" + ChatColor.stripColor(v.data.replaceAll("\n", " ") + "]" + " [" + v.ping + "ms]" + "/[" + v.TPS + " TPS]" + " (x" + v.vl + ")");
                    end.append("\n").append(logline);
                }
                //String url = Hastebin.uploadPaste(end.toString());
                return new String[]{end.toString(), "Pasted logs to: "};
            } catch (Exception ex) {
                return new String[]{"", "Couldn't paste logs, maybe the file is too big? (" + ex.getMessage() + ")"};
            }


    }

}
