-- BOP-002: liberação da OP. Registra o responsável e o momento da liberação.
ALTER TABLE brew_order
    ADD COLUMN assigned_user_id UUID,
    ADD COLUMN released_at TIMESTAMPTZ;
