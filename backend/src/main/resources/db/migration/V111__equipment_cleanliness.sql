-- CLN-004-A — o equipamento passa a ter estado de limpeza, e o evento passa a ter consumidor.
--
-- O DÉBITO, ABERTO DESDE A SPRINT 08. A CLN-004 publica `CleaningCycleReleased` desde então, e ninguém
-- escuta. A metade que faltava não era o listener: era o estado que ele atualizaria. Sem estado, a
-- plataforma afirmava condicionar o uso à limpeza e só cumpria isso no envase (PKG-001), que consulta a
-- última liberação por conta própria. Um fermentador podia receber cerveja logo depois de esvaziar outro
-- lote, sem que nada perguntasse se ele havia sido limpo.
--
-- SUJO É O ESTADO PADRÃO DE QUEM USOU, LIMPO É O DE QUEM NUNCA USOU. Equipamento recém-cadastrado nasce
-- CLEAN: exigir um ciclo de limpeza antes do primeiro uso obrigaria a cervejaria a registrar limpeza de
-- um tanque que acabou de chegar, e a primeira coisa que se aprende a fazer com uma regra assim é
-- burlá-la. O que suja é receber cerveja; o que limpa é um ciclo liberado.
ALTER TABLE equipment
    ADD COLUMN cleanliness VARCHAR(8) NOT NULL DEFAULT 'CLEAN';

ALTER TABLE equipment
    ADD CONSTRAINT ck_equipment_cleanliness CHECK (cleanliness IN ('CLEAN', 'DIRTY'));

-- Quando sujou e quando foi limpo. As duas datas existem porque "está sujo" não é acionável sozinho:
-- quem programa a limpeza precisa saber se o tanque esvaziou hoje de manhã ou há três semanas — e um
-- tanque parado sujo há três semanas é um problema diferente, e pior.
ALTER TABLE equipment ADD COLUMN soiled_at TIMESTAMPTZ;
ALTER TABLE equipment ADD COLUMN cleaned_at TIMESTAMPTZ;

-- O ciclo que deixou o equipamento limpo. É o que liga o estado à evidência: sem ele, "limpo" é uma
-- palavra numa coluna, e quem audita não tem como chegar às medições de concentração, temperatura e ATP
-- que sustentam a liberação.
ALTER TABLE equipment ADD COLUMN cleaned_by_cycle_id UUID;

-- As datas acompanham o estado, e o CHECK impede a combinação que mente: sujo sem quando, ou limpo por
-- ciclo nenhum depois de já ter sido usado. Equipamento nunca usado é o único que fica CLEAN sem data —
-- e é exatamente o que ele é: nunca sujou, ninguém precisou limpar.
ALTER TABLE equipment
    ADD CONSTRAINT ck_equipment_soiled_at CHECK (
        cleanliness <> 'DIRTY' OR soiled_at IS NOT NULL
    );

ALTER TABLE equipment
    ADD CONSTRAINT ck_equipment_cleaned_evidence CHECK (
        (cleaned_at IS NULL AND cleaned_by_cycle_id IS NULL)
        OR (cleaned_at IS NOT NULL AND cleaned_by_cycle_id IS NOT NULL)
    );

-- Índice para a pergunta operacional do dia: o que está sujo esperando limpeza, do mais antigo para o
-- mais novo. Sem ele a tela varreria a tabela inteira para achar meia dúzia de tanques.
CREATE INDEX ix_equipment_dirty ON equipment (brewery_id, soiled_at)
    WHERE cleanliness = 'DIRTY';
