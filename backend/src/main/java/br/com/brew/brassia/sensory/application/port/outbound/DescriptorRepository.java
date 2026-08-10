package br.com.brew.brassia.sensory.application.port.outbound;

import br.com.brew.brassia.sensory.domain.SensoryDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Biblioteca de descritores (SEN-002). */
public interface DescriptorRepository {

    void insert(SensoryDescriptor descriptor);

    Optional<SensoryDescriptor> find(UUID breweryId, UUID descriptorId);

    List<SensoryDescriptor> list(UUID breweryId);

    /**
     * Busca por termo, incluindo sinônimos.
     *
     * <p>A busca é do banco e não do domínio porque percorrer todos os descritores em memória para
     * comparar texto é o tipo de coisa que funciona com trinta e não com trezentos — e a biblioteca
     * cresce a cada treinamento.
     */
    List<SensoryDescriptor> searchByTerm(UUID breweryId, String term);

    /** Descritores vinculados a um estilo, para o scoresheet. */
    List<StyleLink> byStyle(UUID breweryId, String styleCode);

    void linkToStyle(UUID breweryId, String styleCode, UUID descriptorId, boolean expected);

    /**
     * @param expected se o descritor é esperado NAQUELE estilo. O mesmo descritor muda de papel: banana é
     *                 atributo numa Weissbier e desvio numa Pilsen
     */
    record StyleLink(SensoryDescriptor descriptor, boolean expected) {
    }
}
