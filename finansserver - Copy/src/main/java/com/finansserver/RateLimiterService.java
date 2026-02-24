package com.finansserver;

import java.util.HashMap;
import java.util.Map;

public class RateLimiterService {
    public static final String RESET = "\033[0m"; 
    public static final String GREEN = "\033[32m"; 
    public static final String YELLOW = "\033[33m"; 
    public static final String RED = "\033[31m";

    private static final Map<String, RateLimitConfig> endpointLimits = new HashMap<>();
    private static final Map<String, RequestInfo> requestMap = new HashMap<>();
    private static final Map<String, Long> banMap = new HashMap<>();

    static {
        registerEndpoint("/user/getUid", 1, 10000000, 1);
        registerEndpoint("/user/passwordCorrection", 1, 3, 2);
        registerEndpoint("/action", 1, 1, 2);
    }

    public static void registerEndpoint(String endpoint, int X, int Y, int Z) {
        endpointLimits.put(endpoint, new RateLimitConfig(X, Y, Z));
    }

    public static boolean isAllowed(String endpoint, String ip) {
        long currentTime = System.currentTimeMillis();
        String key = ip + ":" + endpoint;

        // Endpoint kayıtlı mı kontrol et
        if (!endpointLimits.containsKey(endpoint)) {
            System.out.println("⚠️ Uyarı: " + endpoint + " için rate limit tanımlanmamış!");
            return true; // Tanımlanmamışsa engelleme yapma
        }

        RateLimitConfig config = endpointLimits.get(endpoint);

        // Eğer IP banlıysa, ban süresi doldu mu kontrol et
        if (banMap.containsKey(key)) {
            long banTime = banMap.get(key);
            if (currentTime - banTime < (config.Z * 60 * 1000)) {
                System.out.println(GREEN + "[INFO] " + RESET +"IP " + ip + " is banned from " + endpoint + " so access denied");
                return false;
            } else {
                banMap.remove(key); // Ban süresi dolduysa kaldır
            }
        }

        // Daha önce istek atılmış mı?
        if (!requestMap.containsKey(key)) {
            requestMap.put(key, new RequestInfo(1, currentTime)); // İlk isteği kaydet
            return true;
        }

        RequestInfo info = requestMap.get(key);

        // Süre dolduysa sıfırla
        if ((currentTime - info.firstRequestTime) > (config.X * 60 * 1000)) {
            info.requestCount = 1;
            info.firstRequestTime = currentTime;
            return true;
        }

        // Eğer limit aşıldıysa, banla
        if (info.requestCount >= config.Y) {
            banMap.put(key, currentTime);
            requestMap.remove(key); // Banlandığında gereksiz veriyi sil
            System.out.println(GREEN + "[INFO] " + RESET +"IP " + ip + " is banned from " + endpoint + " for " + config.Z + " minutes");
            return false;
        }

        // İstek sayısını artır
        info.requestCount++;
        return true;
    }

    public static boolean isAllowedWOCount(String endpoint, String ip) {
        long currentTime = System.currentTimeMillis();
        String key = ip + ":" + endpoint;

        if (!endpointLimits.containsKey(endpoint)) {
            return true;
        }

        RateLimitConfig config = endpointLimits.get(endpoint);

        if (banMap.containsKey(key)) {
            long banTime = banMap.get(key);
            if (currentTime - banTime < (config.Z * 60 * 1000)) {
                System.out.println(GREEN + "[INFO] " + RESET + "IP " + ip + " is banned from " + endpoint + ", access denied");
                return false;
            } else {
                banMap.remove(key);
            }
        }

        return true;
    }

    public static void wrongEntry(String endpoint, String ip) {
        long currentTime = System.currentTimeMillis();
        String key = ip + ":" + endpoint;

        if (!endpointLimits.containsKey(endpoint)) {
            return;
        }

        RateLimitConfig config = endpointLimits.get(endpoint);

        if (banMap.containsKey(key)) {
            return; // Banlı IP tekrar banlanmaz, süresi dolmasını bekler
        }

        requestMap.putIfAbsent(key, new RequestInfo(0, currentTime));
        RequestInfo info = requestMap.get(key);

        if ((currentTime - info.firstRequestTime) > (config.X * 60 * 1000)) {
            info.requestCount = 0;
            info.firstRequestTime = currentTime;
        }

        info.requestCount++;

        if (info.requestCount >= config.Y) {
            banMap.put(key, currentTime);
            requestMap.remove(key);
            System.out.println(GREEN + "[INFO] " + RESET + "IP " + ip + " is banned from " + endpoint + " for " + config.Z + " minutes due to wrong entries");
        }
    }

}

class RateLimitConfig {
    int X, Y, Z;

    public RateLimitConfig(int X, int Y, int Z) {
        this.X = X;
        this.Y = Y;
        this.Z = Z;
    }
}

class RequestInfo {
    int requestCount;
    long firstRequestTime;

    public RequestInfo(int requestCount, long firstRequestTime) {
        this.requestCount = requestCount;
        this.firstRequestTime = firstRequestTime;
    }
}
