package br.com.brew.brassia.ai;

import java.util.Objects;
import java.util.UUID;

/**
 * O único caminho pelo qual uma chamada a modelo de linguagem sai deste sistema (AIA-001).
 *
 * <p><strong>Por que um só caminho.</strong> Provedor, timeout, orçamento, contrato de resposta e
 * registro de custo são a mesma decisão vista de cinco ângulos: se cada módulo chamasse o provedor
 * direto, cada um teria a sua versão de "o que fazer quando não responde" e ninguém teria a conta.
 * Aqui a resposta é uma só, e o custo aparece inteiro num lugar.
 *
 * <p><strong>O que este contrato promete.</strong> Ou devolve um objeto do tipo pedido, ou lança.
 * Não existe meio caminho: texto livre que não satisfaz o contrato é recusado inteiro, porque
 * aproveitar metade de uma resposta inválida é inventar a outra metade. Provedor desligado é estado
 * normal e recusa explícita, não exceção de programação.
 */
public interface ModelGateway {

    /**
     * Pede ao modelo uma resposta que caiba em {@code contract}.
     *
     * @param prompt   o que perguntar, para quem e com que teto
     * @param contract o tipo que a resposta precisa satisfazer; qualquer desvio é recusa
     * @throws br.com.brew.brassia.ai.domain.AiUnavailableException        provedor desligado ou sem resposta
     * @throws br.com.brew.brassia.ai.domain.InvalidModelResponseException resposta fora do contrato
     * @throws br.com.brew.brassia.ai.domain.AiBudgetExceededException     orçamento do mês esgotado
     */
    <T> T complete(Prompt prompt, Class<T> contract);

    /**
     * Um pedido ao modelo, já com dono e teto.
     *
     * <p>{@code untrustedInput} existe separado de {@code instruction} de propósito: o que vem de
     * documento, medição ou usuário é dado, não ordem. A separação é o que permite, na RAG-002,
     * tratar conteúdo recuperado como suspeito sem reescrever este contrato.
     *
     * @param breweryId      cervejaria do contexto autenticado
     * @param actorId        quem pediu; a IA não age sozinha
     * @param purpose        para que serve, para custo e auditoria
     * @param instruction    a instrução do sistema — confiável, escrita por nós
     * @param untrustedInput o conteúdo sobre o qual raciocinar — não confiável
     * @param responseSchema JSON Schema que a resposta deve satisfazer
     * @param maxOutputTokens teto de saída desta chamada
     */
    record Prompt(
            UUID breweryId,
            UUID actorId,
            ModelPurpose purpose,
            String instruction,
            String untrustedInput,
            String responseSchema,
            int maxOutputTokens) {

        public Prompt {
            Objects.requireNonNull(breweryId, "breweryId");
            Objects.requireNonNull(actorId, "actorId é obrigatório: a IA não age sem autor");
            Objects.requireNonNull(purpose, "purpose");
            instruction = requireText(instruction, "instruction");
            responseSchema = requireText(responseSchema, "responseSchema");
            if (maxOutputTokens <= 0) {
                throw new IllegalArgumentException("maxOutputTokens deve ser positivo");
            }
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " é obrigatório");
            }
            return value;
        }
    }
}
