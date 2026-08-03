package br.com.brew.brassia.quality.domain;

/** Tentativa de pular uma fase do tratamento. */
public final class PhaseOutOfOrderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final NonConformityStatus current;
    private final String attempted;

    public PhaseOutOfOrderException(String code, NonConformityStatus current, String attempted) {
        super("a não conformidade %s está em %s e não aceita %s agora"
                .formatted(code, current.label(), attempted));
        this.code = code;
        this.current = current;
        this.attempted = attempted;
    }

    public String code() {
        return code;
    }

    public NonConformityStatus current() {
        return current;
    }

    public String attempted() {
        return attempted;
    }
}
