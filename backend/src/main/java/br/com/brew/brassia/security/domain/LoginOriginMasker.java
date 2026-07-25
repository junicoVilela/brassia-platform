package br.com.brew.brassia.security.domain;

/**
 * Deriva uma representação mascarada da origem do login (SEC-B02), só para
 * exibição no histórico. Nada identificável em claro: o IP tem os octetos finais
 * ocultos e o user-agent vira um rótulo grosseiro de navegador/SO.
 */
public final class LoginOriginMasker {

    private LoginOriginMasker() {}

    /** IPv4 {@code a.b.c.d} → {@code a.b.x.x}; IPv6 → primeiros dois grupos + {@code ::}. */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String value = ip.trim();
        if (value.contains(":")) {
            String[] groups = value.split(":");
            if (groups.length >= 2 && !groups[0].isBlank()) {
                return groups[0] + ":" + groups[1] + "::";
            }
            return "::";
        }
        String[] octets = value.split("\\.");
        if (octets.length == 4) {
            return octets[0] + "." + octets[1] + ".x.x";
        }
        return null;
    }

    /** Rótulo grosseiro do user-agent, ex.: {@code "Chrome · Windows"}. */
    public static String browserLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent;
        String browser = browser(ua);
        String os = os(ua);
        return os == null ? browser : browser + " · " + os;
    }

    private static String browser(String ua) {
        if (ua.contains("Edg")) {
            return "Edge";
        }
        if (ua.contains("Chrome")) {
            return "Chrome";
        }
        if (ua.contains("Firefox")) {
            return "Firefox";
        }
        if (ua.contains("Safari")) {
            return "Safari";
        }
        return "Navegador";
    }

    private static String os(String ua) {
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone") || ua.contains("iPad")) {
            return "iOS";
        }
        if (ua.contains("Mac OS")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return null;
    }
}
