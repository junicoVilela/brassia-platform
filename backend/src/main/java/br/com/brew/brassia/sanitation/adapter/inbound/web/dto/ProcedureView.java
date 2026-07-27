package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import br.com.brew.brassia.sanitation.domain.CleaningProcedure;
import java.util.List;
import java.util.UUID;

public record ProcedureView(
        UUID id, String code, String name, int version, String status, List<ProcedureStepDto> steps) {

    public static ProcedureView from(CleaningProcedure p) {
        return new ProcedureView(p.id().value(), p.code(), p.name(), p.version(), p.status().name(),
                p.steps().stream().map(ProcedureStepDto::from).toList());
    }
}
