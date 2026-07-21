package br.com.brew.brassia.security.application.port.outbound;

/** Verifica se uma senha em texto puro consta em uma blocklist de comprometidas. */
public interface CompromisedPasswordChecker {
    boolean isCompromised(String rawPassword);
}
