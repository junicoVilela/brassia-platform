package br.com.brew.brassia.sensory.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Payloads de entrada da análise sensorial (SEN-001). */
public final class SensoryDtos {

    private SensoryDtos() {
    }

    public record CreateSession(@NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 200) String purpose, @NotNull LocalDate scheduledFor) {}

    public record AmendSession(@NotBlank @Size(max = 200) String purpose,
            @NotNull LocalDate scheduledFor) {}

    /** O código cego não vem do cliente: é sorteado pelo sistema para não vazar ordem nem intenção. */
    public record AddSample(@NotNull UUID batchId, @Size(max = 500) String note) {}

    public record SubmitEvaluation(@NotNull UUID sampleId, @NotNull Map<String, Integer> scores,
            List<String> descriptors, @Size(max = 1000) String note) {}
}
