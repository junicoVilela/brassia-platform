package br.com.brew.brassia.security.application.port.outbound;

public interface PasswordHasher {
    String hash(CharSequence rawPassword);
    boolean matches(CharSequence rawPassword, String encodedPassword);
    boolean needsUpgrade(String encodedPassword);
}
