package br.com.brew.brassia.packaging.application.port.inbound;

import br.com.brew.brassia.packaging.domain.LabelPreview;
import br.com.brew.brassia.packaging.domain.LabelPrint;
import br.com.brew.brassia.packaging.domain.LabelRegulatoryRule;
import br.com.brew.brassia.packaging.domain.LabelTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Rótulo e ficha do lote (PKG-004). */
public final class LabelCommands {

    private LabelCommands() {
    }

    /** Salvar cria uma versão nova do template e preserva a anterior. */
    public interface SaveTemplate {
        Result handle(Command command);

        record Command(UUID actorId, UUID breweryId, String code, String name, List<String> fields,
                String note) {}

        record Result(UUID templateId, int version) {}
    }

    /** Regra regulatória: quais campos são obrigatórios. Vive separada do layout. */
    public interface SaveRule {
        void handle(UUID actorId, UUID breweryId, LabelRegulatoryRule rule);
    }

    /** Prévia: monta os campos das fontes rastreáveis e acusa o que falta antes da impressão. */
    public interface Preview {
        LabelPreview handle(UUID breweryId, UUID planId, UUID templateId);
    }

    /**
     * Registra a impressão. Se já houve impressão para o plano, esta é reimpressão e o motivo passa
     * a ser obrigatório — quem chama não escolhe isso.
     */
    public interface Print {
        Result handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID planId, UUID templateId, int quantity,
                String reason) {}

        record Result(UUID printId, boolean reprint, int quantity) {}
    }

    public interface Queries {
        List<LabelTemplate> templates(UUID breweryId);

        List<LabelTemplate> templateVersions(UUID breweryId, String code);

        Optional<LabelRegulatoryRule> rule(UUID breweryId);

        List<LabelPrint> prints(UUID breweryId, UUID planId);
    }
}
