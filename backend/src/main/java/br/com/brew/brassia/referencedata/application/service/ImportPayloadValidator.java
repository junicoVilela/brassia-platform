package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.referencedata.domain.ValidationIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validação de segurança e formato do payload de importação (REF-002): tamanho,
 * MIME suportado e boa-formação. Erros bloqueiam a publicação (job → FAILED).
 * A deduplicação por checksum é feita no handler (depende do repositório).
 */
public final class ImportPayloadValidator {

    /** Limite defensivo contra arquivos grandes demais. */
    static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME = Set.of("application/json", "application/xml", "text/xml");

    // Uso interno apenas para checar boa-formação; não é o mapper de serialização web.
    private final ObjectMapper json = new ObjectMapper();

    public List<ValidationIssue> validate(String contentType, String payload, long sizeBytes) {
        var issues = new ArrayList<ValidationIssue>();
        if (sizeBytes > MAX_BYTES) {
            issues.add(ValidationIssue.error("size", "payload excede o limite de 2 MB"));
        }
        String mime = contentType == null ? "" : contentType.toLowerCase();
        boolean allowed = ALLOWED_MIME.stream().anyMatch(mime::startsWith);
        if (!allowed) {
            issues.add(ValidationIssue.error("mime", "content-type não suportado: " + contentType));
        } else if (mime.startsWith("application/json")) {
            try {
                json.readTree(payload);
            } catch (Exception e) {
                issues.add(ValidationIssue.error("schema", "JSON malformado: " + rootMessage(e)));
            }
        }
        return issues;
    }

    private static String rootMessage(Throwable e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message.split("\n", 2)[0];
    }
}
