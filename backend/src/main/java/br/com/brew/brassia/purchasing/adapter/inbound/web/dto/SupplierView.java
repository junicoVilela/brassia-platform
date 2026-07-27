package br.com.brew.brassia.purchasing.adapter.inbound.web.dto;

import br.com.brew.brassia.purchasing.domain.Supplier;
import java.util.UUID;

public record SupplierView(UUID id, String name, String code, Integer leadTimeDays) {
    public static SupplierView from(Supplier s) {
        return new SupplierView(s.id().value(), s.name(), s.code(), s.leadTimeDays());
    }
}
