-- BOP-003: cancelamento da OP. Registra o motivo e o momento do cancelamento.
ALTER TABLE brew_order
    ADD COLUMN cancel_reason VARCHAR(500),
    ADD COLUMN cancelled_at TIMESTAMPTZ;
