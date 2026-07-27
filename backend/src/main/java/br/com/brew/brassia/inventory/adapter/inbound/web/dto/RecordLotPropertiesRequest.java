package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RecordLotPropertiesRequest(@NotEmpty List<@Valid LotPropertyInput> properties) {}
