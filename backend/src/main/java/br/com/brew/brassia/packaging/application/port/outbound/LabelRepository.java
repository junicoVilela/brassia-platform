package br.com.brew.brassia.packaging.application.port.outbound;

import br.com.brew.brassia.packaging.domain.LabelPrint;
import br.com.brew.brassia.packaging.domain.LabelRegulatoryRule;
import br.com.brew.brassia.packaging.domain.LabelTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabelRepository {

    /** Versão nova do template; a anterior nunca é reescrita. */
    void insertTemplate(LabelTemplate template);

    Optional<LabelTemplate> findTemplate(UUID breweryId, UUID templateId);

    /** Versão vigente (a maior) de um código de template. */
    Optional<LabelTemplate> findLatestTemplate(UUID breweryId, String code);

    /** Só a versão vigente de cada código; o histórico fica acessível por id. */
    List<LabelTemplate> findLatestTemplates(UUID breweryId);

    List<LabelTemplate> findTemplateVersions(UUID breweryId, String code);

    Optional<LabelRegulatoryRule> findRule(UUID breweryId);

    void saveRule(UUID breweryId, LabelRegulatoryRule rule);

    void insertPrint(LabelPrint print);

    /** Impressões do plano, da mais recente para a mais antiga. */
    List<LabelPrint> findPrints(UUID breweryId, UUID planId);

    /** Já houve impressão para o plano? É o que define se a próxima é reimpressão. */
    boolean hasPrint(UUID breweryId, UUID planId);
}
