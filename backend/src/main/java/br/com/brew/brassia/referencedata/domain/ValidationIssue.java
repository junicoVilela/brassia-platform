package br.com.brew.brassia.referencedata.domain;

import java.util.Objects;

/** Problema de validação de um job de importação, por linha/campo quando aplicável. */
public record ValidationIssue(Integer line, String field, String code, String message, ValidationSeverity severity) {

    public ValidationIssue {
        code = requireText(code, "code");
        message = requireText(message, "message");
        Objects.requireNonNull(severity, "severity");
        field = field == null || field.isBlank() ? null : field.trim();
    }

    public boolean isError() {
        return severity == ValidationSeverity.ERROR;
    }

    public static ValidationIssue error(String code, String message) {
        return new ValidationIssue(null, null, code, message, ValidationSeverity.ERROR);
    }

    public static ValidationIssue error(Integer line, String field, String code, String message) {
        return new ValidationIssue(line, field, code, message, ValidationSeverity.ERROR);
    }

    public static ValidationIssue warning(String code, String message) {
        return new ValidationIssue(null, null, code, message, ValidationSeverity.WARNING);
    }

    private static String requireText(String value, String name) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(name + " é obrigatório");
        }
        return trimmed;
    }
}
