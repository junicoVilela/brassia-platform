package br.com.brew.brassia.knowledge;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Recuperação de trechos de documento, para outros módulos (RAG-001).
 *
 * <p><strong>A permissão entra na consulta, não numa verificação antes dela.</strong> Quem chama passa
 * as permissões que tem, e o que não pode ser visto não é recuperado — não vem com o conteúdo escondido,
 * não vem como "acesso negado". Um título de laudo já é informação, e "existe um laudo que você não pode
 * ler sobre este lote" é justamente o tipo de informação que não se dá de graça.
 *
 * <p>Filtrar dentro da consulta, e não depois dela, também é o que faz o limite de resultados significar
 * algo: filtrar depois devolveria três trechos de dez porque sete eram invisíveis, e a resposta sairia
 * pobre sem que ninguém soubesse por quê.
 *
 * <p><strong>O que volta é dado não confiável, e o tipo diz isso.</strong> {@link Evidence} carrega texto
 * escrito por terceiros — fabricante, laboratório, fornecedor — que pode conter instrução endereçada ao
 * modelo. Quem consome trata como conteúdo sobre o qual raciocinar, nunca como ordem a seguir.
 */
public interface KnowledgeRetrieval {

    /**
     * Trechos mais relevantes para a pergunta, já filtrados por cervejaria, permissão e vigência.
     *
     * @return lista possivelmente vazia, ordenada da maior para a menor relevância; vazia é resposta
     *         legítima e significa "não há fonte para isto", que é diferente de erro
     */
    List<Evidence> search(Query query);

    /**
     * @param breweryId   cervejaria do contexto autenticado
     * @param permissions permissões de quem pergunta; documento fora delas não é recuperado
     * @param question    a pergunta, em linguagem natural
     * @param onDate      data de referência da vigência — normalmente hoje, outra quando se investiga
     *                    o passado ("o que o laudo dizia quando o lote foi produzido?")
     * @param equipmentId quando informado, restringe ao que se refere a este equipamento e ao que não
     *                    se refere a equipamento nenhum; manual de bomba não responde sobre a caldeira
     * @param limit       quantos trechos no máximo
     */
    record Query(
            UUID breweryId,
            Set<String> permissions,
            String question,
            LocalDate onDate,
            UUID equipmentId,
            int limit) {

        public Query {
            Objects.requireNonNull(breweryId, "breweryId");
            permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
            Objects.requireNonNull(question, "question");
            Objects.requireNonNull(onDate, "onDate");
            if (limit <= 0) {
                throw new IllegalArgumentException("limit deve ser positivo");
            }
        }
    }

    /**
     * Um trecho recuperado, com o suficiente para ser citado e conferido.
     *
     * <p>Tipo, versão e vigência viajam junto do texto porque mudam a autoridade da citação: "a FISPQ
     * vigente diz" e "uma FISPQ de 2019, substituída, dizia" são afirmações diferentes sobre a mesma
     * frase, e a segunda não deve ser apresentada como a primeira.
     *
     * @param documentId  documento de origem
     * @param code        código do documento, para citação legível
     * @param title       título do documento
     * @param type        que espécie de documento é — muda como a citação deve ser lida
     * @param version     versão do documento
     * @param effectiveOn verdadeiro quando o documento vale na data consultada
     * @param ordinal     posição do trecho no documento, para localizar a conferência
     * @param text        o texto do trecho — <strong>não confiável</strong>
     * @param score       relevância relativa dentro desta consulta; não é probabilidade de nada
     */
    record Evidence(
            UUID documentId,
            String code,
            String title,
            String type,
            int version,
            boolean effectiveOn,
            int ordinal,
            String text,
            double score) {
    }
}
