-- PWA-002: idempotência dos apontamentos vindos da fila offline.
--
-- A fila do aparelho tem uma garantia fraca por natureza: ela reenvia até receber confirmação, porque a
-- alternativa — desistir na primeira falha — perderia o apontamento de quem estava sem rede. "Ao menos uma
-- vez" só vira "exatamente uma vez" se este lado souber reconhecer a repetição, e é isso que a coluna faz.
--
-- A chave é gerada NO APARELHO, no instante em que a pessoa registra a medição — não no envio. A diferença
-- é o cenário inteiro: a medição é registrada às 9h sem rede, a fila tenta às 11h, a resposta se perde, e a
-- fila tenta de novo às 11h05. Uma chave gerada no envio seria diferente nas duas tentativas e criaria duas
-- medições da mesma leitura; gerada no registro, ela identifica O FATO, e as duas tentativas são a mesma.
ALTER TABLE production_measurement ADD COLUMN client_request_id VARCHAR(80);

-- Único POR CERVEJARIA, não global: a chave vem de um aparelho e não há autoridade central que garanta
-- unicidade entre cervejarias diferentes. Colisão entre duas cervejarias é astronomicamente improvável com
-- UUID, mas fazer a restrição depender dessa improbabilidade seria fazer a corretude depender de sorte.
--
-- Índice PARCIAL porque a coluna é nula para tudo que foi registrado online: a maioria das medições não vem
-- de fila, e um índice único comum trataria os nulos como distintos (correto no PostgreSQL) mas ocuparia
-- espaço com eles à toa.
CREATE UNIQUE INDEX uq_production_measurement_client_request
    ON production_measurement (brewery_id, client_request_id)
    WHERE client_request_id IS NOT NULL;
