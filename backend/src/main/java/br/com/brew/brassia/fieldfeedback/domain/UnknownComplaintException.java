package br.com.brew.brassia.fieldfeedback.domain;

import java.util.UUID;

/** Reclamação que não existe nesta cervejaria. */
public final class UnknownComplaintException extends RuntimeException {

    public UnknownComplaintException(UUID complaintId) {
        super("reclamação desconhecida: " + complaintId);
    }
}
