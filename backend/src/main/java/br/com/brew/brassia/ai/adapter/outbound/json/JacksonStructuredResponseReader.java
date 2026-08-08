package br.com.brew.brassia.ai.adapter.outbound.json;

import br.com.brew.brassia.ai.application.port.outbound.StructuredResponseReader;
import br.com.brew.brassia.ai.domain.InvalidModelResponseException;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

/**
 * Lê a resposta do modelo no tipo contratado, com desconfiança configurada (AIA-001).
 *
 * <p><strong>Mapper próprio, não o do MVC.</strong> O mapper da aplicação é permissivo de propósito —
 * ele lê corpo de requisição de cliente nosso, que pode ganhar campos novos sem quebrar. Este lê texto
 * gerado por um modelo, onde campo desconhecido não é evolução de contrato: é sinal de que a resposta
 * não é a que pedimos. Compartilhar o mapper seria herdar a tolerância errada.
 *
 * <p>As três recusas que importam, todas ligadas explicitamente:
 *
 * <ul>
 *   <li><strong>Campo desconhecido</strong> — o modelo inventou estrutura.
 *   <li><strong>Primitivo nulo</strong> — sem isto, um {@code boolean} ausente viraria {@code false} em
 *       silêncio, e "não respondeu" passaria por "respondeu que não".
 *   <li><strong>Campo obrigatório nulo</strong> — o construtor do contrato é a última palavra sobre as
 *       invariantes dele.
 * </ul>
 *
 * <p>Nem o JSON recusado nem a mensagem do desserializador entram na exceção: resposta de modelo carrega
 * o que estava no prompt, e prompt carrega POP, laudo e medição. O motivo vai por extenso, o conteúdo
 * fica na causa, que não é apresentada ao usuário.
 */
@Component
class JacksonStructuredResponseReader implements StructuredResponseReader {

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .build();

    @Override
    public <T> T read(String json, Class<T> contract) {
        if (json == null || json.isBlank()) {
            throw new InvalidModelResponseException("o modelo respondeu vazio");
        }
        try {
            return mapper.readValue(json, contract);
        } catch (JacksonException malformed) {
            throw new InvalidModelResponseException(
                    "a resposta do modelo não satisfaz o contrato " + contract.getSimpleName(), malformed);
        }
    }
}
