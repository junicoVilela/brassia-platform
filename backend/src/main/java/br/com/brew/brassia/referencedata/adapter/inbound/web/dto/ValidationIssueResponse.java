package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.domain.ValidationIssue;
import java.util.List;

public record ValidationIssueResponse(Integer line, String field, String code, String message, String severity) {

    public static ValidationIssueResponse from(ValidationIssue issue) {
        return new ValidationIssueResponse(issue.line(), issue.field(), issue.code(), issue.message(),
                issue.severity().name());
    }

    public static List<ValidationIssueResponse> fromAll(List<ValidationIssue> issues) {
        return issues == null ? List.of() : issues.stream().map(ValidationIssueResponse::from).toList();
    }
}
