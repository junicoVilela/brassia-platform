-- FER-003: critério de estabilidade de FG configurável, guardado no perfil de fermentação.
-- Fica no perfil (e não numa preferência global) porque a versão publicada é imutável: o
-- critério usado numa avaliação passada continua reproduzível, e Ale/Lager podem divergir.
-- Perfis criados antes desta migration recebem o padrão do domínio (48h / 3 leituras / 0,0020 SG).

ALTER TABLE fermentation_profile
    ADD COLUMN stability_window_hours INTEGER NOT NULL DEFAULT 48,
    ADD COLUMN stability_min_readings INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN stability_tolerance_sg NUMERIC(6, 4) NOT NULL DEFAULT 0.0020;

ALTER TABLE fermentation_profile
    ADD CONSTRAINT ck_fermentation_profile_stability_window CHECK (stability_window_hours > 0),
    ADD CONSTRAINT ck_fermentation_profile_stability_readings CHECK (stability_min_readings >= 2),
    ADD CONSTRAINT ck_fermentation_profile_stability_tolerance CHECK (stability_tolerance_sg > 0);
