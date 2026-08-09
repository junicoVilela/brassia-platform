package br.com.brew.brassia.integration.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * O que está escrito num código lido (INT-003).
 *
 * <p><strong>O código não carrega autorização, e essa é a história inteira.</strong> Ele contém apenas
 * <em>o quê</em> — tipo e identificador —, nunca um token, uma assinatura ou uma credencial. A razão é
 * física: um QR colado num tanque é legível por qualquer pessoa que entre na sala, fotografável de longe e
 * copiável para outra etiqueta. Qualquer segredo impresso nele é um segredo público.
 *
 * <p>A consequência é que ler o código não é ganhar acesso: é fazer uma pergunta que ainda precisa ser
 * autorizada. Quem lê precisa da permissão do tipo apontado ({@link ScanTarget#requiredPermission()}) e da
 * cervejaria correta — as mesmas exigências de quem chega pelo menu, porque são a <em>mesma</em>
 * informação.
 *
 * <p><strong>Formato:</strong> {@code brassia://<tipo>/<identificador>}, com segmentos extras ignorados. O
 * esquema já existia nas etiquetas de envase (PKG-004, {@code LabelField.QR_PAYLOAD}), que imprimem
 * {@code brassia://lote/<código>/envase/<plano>} — a tolerância ao sufixo é o que permite ler as etiquetas
 * já impressas em vez de invalidá-las.
 */
public record ScanReference(ScanTarget target, String identifier) {

    private static final String SCHEME = "brassia://";

    public ScanReference {
        Objects.requireNonNull(target, "target");
        identifier = requireIdentifier(identifier);
    }

    /**
     * Interpreta o conteúdo lido.
     *
     * <p>Recusa com {@link UnknownScanCodeException}, cuja mensagem é sempre a mesma para "não é do formato",
     * "tipo desconhecido" e "sem identificador". A uniformidade é deliberada: distinguir os casos ensinaria
     * a quem estivesse sondando quais tipos existem.
     */
    public static ScanReference parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UnknownScanCodeException("código vazio");
        }
        var value = raw.trim();
        if (!value.toLowerCase(Locale.ROOT).startsWith(SCHEME)) {
            throw new UnknownScanCodeException("código fora do formato esperado");
        }
        var path = value.substring(SCHEME.length());
        var parts = path.split("/");
        if (parts.length < 2) {
            throw new UnknownScanCodeException("código fora do formato esperado");
        }
        return new ScanReference(ScanTarget.of(parts[0]), parts[1]);
    }

    /** O que se imprime na etiqueta. */
    public String encoded() {
        return SCHEME + target.segment() + "/" + identifier;
    }

    private static String requireIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UnknownScanCodeException("código sem identificador");
        }
        var identifier = raw.trim();
        if (identifier.length() > 80) {
            throw new UnknownScanCodeException("identificador do código é longo demais");
        }
        // Só o alfabeto de um identificador: letras, dígitos, hífen, sublinhado e ponto. É o que impede um
        // código impresso de carregar caminho, query ou script para dentro da aplicação — a etiqueta é
        // entrada de terceiro tanto quanto um formulário.
        if (!identifier.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) {
            throw new UnknownScanCodeException("identificador do código é inválido");
        }
        return identifier;
    }
}
