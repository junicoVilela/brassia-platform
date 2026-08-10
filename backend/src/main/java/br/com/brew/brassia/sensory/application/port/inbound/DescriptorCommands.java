package br.com.brew.brassia.sensory.application.port.inbound;

import br.com.brew.brassia.sensory.domain.DescriptorCategory;
import br.com.brew.brassia.sensory.domain.Hypothesis;
import br.com.brew.brassia.sensory.domain.LicenseTier;
import br.com.brew.brassia.sensory.domain.SensoryDescriptor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Manter a biblioteca de descritores (SEN-002). */
public interface DescriptorCommands {

    SensoryDescriptor create(CreateCommand command);

    /** Vincula um descritor a um estilo, dizendo se ele é esperado ali. */
    void linkToStyle(UUID breweryId, String styleCode, UUID descriptorId, boolean expected, UUID actor);

    /**
     * @param perceptionThreshold só é aceito quando a licença da fonte o autoriza. Recusado na criação,
     *                            não filtrado na leitura: dado que não pode ser publicado e mesmo assim
     *                            está gravado é vazamento esperando exportação
     */
    record CreateCommand(UUID breweryId, String code, String name, DescriptorCategory category,
            Set<String> synonyms, String sourceName, String sourceReference, LicenseTier licenseTier,
            String attribution, BigDecimal perceptionThreshold, String thresholdUnit,
            List<Hypothesis> hypotheses, UUID actor) {
    }
}
