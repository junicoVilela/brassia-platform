package br.com.brew.brassia.shared.observability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mascara valores de campos e cabeçalhos sensíveis antes de logar ou auditar.
 * Nunca registrar token, senha, cookie, segredo ou credencial em claro.
 */
public final class SensitiveDataMasker {

    public static final String MASK = "***";

    private static final List<String> SENSITIVE_FRAGMENTS = List.of(
            "password", "senha", "secret", "segredo", "token", "authorization",
            "cookie", "credential", "pepper", "apikey", "api-key", "private-key",
            "client-secret", "otp", "mfa");

    private SensitiveDataMasker() {
    }

    public static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    /** Retorna {@link #MASK} para chaves sensíveis; caso contrário, o valor original. */
    public static String maskValue(String key, String value) {
        return isSensitive(key) ? MASK : value;
    }

    /** Cópia do mapa com os valores das chaves sensíveis mascarados. Preserva a ordem. */
    public static Map<String, String> mask(Map<String, String> data) {
        var masked = new LinkedHashMap<String, String>();
        data.forEach((key, value) -> masked.put(key, maskValue(key, value)));
        return masked;
    }
}
