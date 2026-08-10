package br.com.brew.brassia.sensory.application.port.inbound;

import br.com.brew.brassia.sensory.application.port.outbound.DescriptorRepository;
import br.com.brew.brassia.sensory.domain.SensoryDescriptor;
import java.util.List;
import java.util.UUID;

public interface DescriptorQueries {

    List<SensoryDescriptor> list(UUID breweryId);

    /** Busca por termo, incluindo sinônimos — o caminho de quem digita com a taça na mão. */
    List<SensoryDescriptor> search(UUID breweryId, String term);

    /** O vocabulário do estilo, para o scoresheet. */
    List<DescriptorRepository.StyleLink> forStyle(UUID breweryId, String styleCode);
}
