-- SEC-B02: origem mascarada no histórico de login. Além do hash (irreversível,
-- para retenção sem dado pessoal em claro), guardamos uma representação mascarada
-- só para exibição: IP com os octetos finais ocultos e um rótulo grosseiro de
-- navegador/SO. Nada identificável em claro.
ALTER TABLE login_event ADD COLUMN ip_masked VARCHAR(64);
ALTER TABLE login_event ADD COLUMN user_agent_label VARCHAR(120);
