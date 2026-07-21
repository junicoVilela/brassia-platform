package br.com.brew.brassia.security.domain;

/**
 * Senha em texto puro recebida na fronteira, antes de virar hash. Aqui só a
 * validação <em>mínima</em> (comprimento) — a política plena (força, blocklist
 * de comprometidas, histórico) é da SEC-003. O valor nunca é persistido nem logado.
 */
public record RawPassword(String value) {
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 200;

    public RawPassword {
        if (value == null || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("senha deve conter de 8 a 200 caracteres");
        }
    }

    @Override
    public String toString() {
        return "RawPassword[***]";
    }
}
