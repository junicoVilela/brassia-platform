-- Agora que a tabela brewery existe (V6), amarra as FKs de tenant do RBAC que
-- foram adiadas na V5. Linhas atuais têm brewery_id NULL (associações globais).
ALTER TABLE user_group_membership
    ADD CONSTRAINT fk_membership_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (id);

ALTER TABLE security_group
    ADD CONSTRAINT fk_group_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (id);
