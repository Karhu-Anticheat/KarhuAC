package me.liwk.karhu.antivpn;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.KarhuLogger;
import me.liwk.karhu.util.json.JsonReader;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

public class VPNCheck {

    private static final Cache<String, Boolean> cachedIPs = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(6, TimeUnit.HOURS)
            .build();

    /**
     * Blocking lookup, must never be called from the main thread.
     */
    public static boolean checkAddress(InetAddress inetAddress) {

        String ip = inetAddress.getHostAddress();

        Boolean cached = cachedIPs.getIfPresent(ip);

        if (cached != null) {
            return cached;
        }

        try {
            Boolean blocked = checkVPN(ip);

            if (blocked != null) {
                cachedIPs.put(ip, blocked);
                return blocked;
            }
        } catch (Exception ex) {
            KarhuLogger.critical("ip check services down? message: " + ex.getMessage());
            ex.printStackTrace();
        }

        return false;
    }

    private static Boolean checkVPN(String address) throws Exception {
        String[] dataFromIP = JsonReader.getData(address);

        if(dataFromIP[0] == null || dataFromIP[2] == null) {
            return null;
        }


        boolean proxy = Boolean.parseBoolean(dataFromIP[0]);
        boolean risk = Boolean.parseBoolean(dataFromIP[2]);

        if (proxy && Karhu.getInstance().getConfigManager().isProxycheck()) {
            return true;
        }

        return risk && Karhu.getInstance().getConfigManager().isMaliciouscheck();
    }
}
