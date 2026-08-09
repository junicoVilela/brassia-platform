package br.com.brew.brassia.integration.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * O que um código pode apontar (INT-003).
 *
 * <p><strong>A permissão mora aqui, junto do tipo.</strong> Não é organização por conveniência: é o que
 * garante que a alçada exigida seja a mesma independentemente de por onde a leitura entrou — pela câmera,
 * por um link colado, por um teclado de código de barras. Um mapa mantido na borda HTTP protegeria só o
 * caminho que passa por ela.
 *
 * <p>O <strong>segmento</strong> é a palavra que aparece no código impresso e é parte do contrato com a
 * etiqueta: uma etiqueta colada num tanque em 2026 precisa continuar sendo lida em 2030, mesmo que a classe
 * de domínio por trás mude de nome. Por isso ele é escrito em português e separado do nome da enum.
 */
public enum ScanTarget {

    EQUIPAMENTO("equipamento", "equipment.read", "/equipment"),
    LOTE("lote", "production.batch.read", "/production/batches"),
    OP("op", "planning.order.read", "/brew-orders"),
    EMBALAGEM("embalagem", "packaging.plan.read", "/packaging/finished-lots");

    private final String segment;
    private final String requiredPermission;
    private final String route;

    ScanTarget(String segment, String requiredPermission, String route) {
        this.segment = segment;
        this.requiredPermission = requiredPermission;
        this.route = route;
    }

    public static ScanTarget of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tipo do código é obrigatório");
        }
        var normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(t -> t.segment.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new UnknownScanCodeException("tipo de código desconhecido"));
    }

    public String segment() {
        return segment;
    }

    /** A alçada que a leitura exige. O código não a substitui — ver {@link ScanReference}. */
    public String requiredPermission() {
        return requiredPermission;
    }

    /** Para onde a interface leva depois de resolver. */
    public String route() {
        return route;
    }
}
