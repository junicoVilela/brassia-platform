package br.com.brew.brassia.catalog.adapter.inbound.web.dto;

import br.com.brew.brassia.catalog.domain.PropertyRange;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateTechnicalProfileRequest(
        @Size(max = 160) String manufacturer,
        @Size(max = 160) String origin,
        @Size(max = 60) String form,
        @Size(max = 60) String purpose,
        @Size(max = 160) String laboratory,
        @Size(max = 60) String labCode,
        Map<String, RangeRequest> ranges,
        List<String> descriptors,
        UUID sourceId,
        @Size(max = 200) String sourceName) {

    public Map<String, PropertyRange> toRanges() {
        var result = new LinkedHashMap<String, PropertyRange>();
        if (ranges != null) {
            ranges.forEach((key, range) -> {
                if (range != null) {
                    result.put(key, range.toRange());
                }
            });
        }
        return result;
    }
}
