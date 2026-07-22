package br.com.brew.brassia.security.adapter.inbound.web;

/** Atributos de sessão para login pendente de MFA (SEC-009). */
final class PendingMfaSession {
    static final String USER_ID = "brassia.pendingMfa.userId";
    static final String DISPLAY_NAME = "brassia.pendingMfa.displayName";
    static final String REAUTH_AT = "brassia.mfa.reauthAt";

    private PendingMfaSession() {}

    static void store(jakarta.servlet.http.HttpSession session, java.util.UUID userId, String displayName) {
        session.setAttribute(USER_ID, userId.toString());
        session.setAttribute(DISPLAY_NAME, displayName);
    }

    static java.util.UUID requireUserId(jakarta.servlet.http.HttpSession session) {
        var raw = (String) session.getAttribute(USER_ID);
        if (raw == null) {
            throw new IllegalArgumentException("nenhum login MFA pendente");
        }
        return java.util.UUID.fromString(raw);
    }

    static String displayName(jakarta.servlet.http.HttpSession session) {
        return (String) session.getAttribute(DISPLAY_NAME);
    }

    static void clear(jakarta.servlet.http.HttpSession session) {
        session.removeAttribute(USER_ID);
        session.removeAttribute(DISPLAY_NAME);
    }

    static boolean hasRecentReauth(jakarta.servlet.http.HttpSession session) {
        var at = (java.time.Instant) session.getAttribute(REAUTH_AT);
        return at != null && at.isAfter(java.time.Instant.now().minusSeconds(300));
    }

    static void markReauth(jakarta.servlet.http.HttpSession session) {
        session.setAttribute(REAUTH_AT, java.time.Instant.now());
    }
}
