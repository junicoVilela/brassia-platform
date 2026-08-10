package br.com.brew.brassia.fieldfeedback.domain;

/** Onde a reclamação está (FLD-001). */
public enum ComplaintStatus {
    OPEN,
    UNDER_ANALYSIS,
    /** Encerrada — só depois de atendidas as ações exigidas, ou com dispensa justificada. */
    CLOSED
}
