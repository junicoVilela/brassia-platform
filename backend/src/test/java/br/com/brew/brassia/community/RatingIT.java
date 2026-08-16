package br.com.brew.brassia.community;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Avaliação e denúncia de ponta a ponta (COM-005).
 *
 * <p>O que estes testes fixam: <strong>a média nunca viaja sem a contagem</strong>, uma nota por pessoa
 * se troca em vez de acumular, e <strong>denunciar registra sem esconder nada</strong>.
 *
 * <p>O que não está aqui, e é decisão registrada: <strong>não há endpoint de revisão</strong>
 * (DUV-COM-001). "Moderação auditada" pressupõe um papel acima das cervejarias, e quem pode escondê-lo
 * é decisão de modelo de segurança — não detalhe de implementação.
 */
@SpringBootTest
@Testcontainers
class RatingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LIBRARY = "/api/v1/community/library";

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void aMediaNuncaViajaSemAContagem() throws Exception {
        // "5,0" de uma avaliação e "5,0" de duzentas são o mesmo número e significam coisas opostas.
        var session = login();
        var publicacao = publica(session);

        // Sem votos a média é NULA, e não zero: zero é a pior nota possível, e uma receita nova
        // nasceria parecendo péssima.
        mockMvc.perform(get(LIBRARY + "/" + publicacao + "/rating").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").doesNotExist())
                .andExpect(jsonPath("$.count", is(0)))
                .andExpect(jsonPath("$.meaningful", is(false)));

        avalia(leitor(), publicacao, 5, status().isNoContent());

        mockMvc.perform(get(LIBRARY + "/" + publicacao + "/rating").session(session))
                .andExpect(jsonPath("$.average", is(5.0)))
                .andExpect(jsonPath("$.count", is(1)))
                // Um voto não é reputação: a tela precisa saber mostrar o número como opinião.
                .andExpect(jsonPath("$.meaningful", is(false)));
    }

    @Test
    void umaNotaPorPessoaSeTrocaEmVezDeAcumular() throws Exception {
        // Acumular transformaria a média numa contagem de quem insistiu mais — o jeito mais simples de
        // manipular reputação sem robô nenhum.
        var session = login();
        var publicacao = publica(session);
        var bruno = leitor();

        avalia(bruno, publicacao, 5, status().isNoContent());
        avalia(bruno, publicacao, 2, status().isNoContent());

        mockMvc.perform(get(LIBRARY + "/" + publicacao + "/rating").session(session))
                .andExpect(jsonPath("$.count", is(1)))
                .andExpect(jsonPath("$.average", is(2.0)));

        // E quem avaliou vê a própria nota, que é o que permite a tela mostrar "você deu 2".
        mockMvc.perform(get(LIBRARY + "/" + publicacao + "/rating").with(authentication(bruno)))
                .andExpect(jsonPath("$.myRating", is(2)));
    }

    @Test
    void oAutorNaoAvaliaAPropriaReceita() throws Exception {
        // A nota do autor não informa ninguém, e uma média que a inclui mede outra coisa.
        var session = login();
        var publicacao = publica(session);

        mockMvc.perform(put(LIBRARY + "/" + publicacao + "/rating").session(session).with(csrf())
                        .contentType("application/json").content("{\"value\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("self_rating")));
    }

    @Test
    void naoSeAvaliaOQueNaoSePodeLer() throws Exception {
        // A mesma matriz de visibilidade de sempre, e não uma regra nova.
        var session = login();
        var publicacao = publica(session);
        mockMvc.perform(put(LIBRARY + "/" + publicacao + "/visibility").session(session).with(csrf())
                        .contentType("application/json").content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isNoContent());

        avalia(leitor(), publicacao, 4, status().isNotFound());
    }

    @Test
    void aNotaVaiDeUmACinco() throws Exception {
        avalia(leitor(), publica(login()), 0, status().isBadRequest());
    }

    @Test
    void denunciarRegistraENaoEscondeNada() throws Exception {
        // Uma denúncia que tirasse o conteúdo do ar seria uma arma: qualquer um derrubaria a receita de
        // um concorrente escrevendo três linhas.
        var session = login();
        var publicacao = publica(session);

        denuncia(leitor(), publicacao, "PLAGIARISM", "é cópia da receita do Bruno",
                status().isCreated());

        // A publicação continua no ar, legível para qualquer um, exatamente como antes.
        mockMvc.perform(get(LIBRARY + "/" + publicacao).with(authentication(leitor())))
                .andExpect(status().isOk());
    }

    @Test
    void oAutorVeAsDenunciasContraSiSemSaberQuemDenunciou() throws Exception {
        // O direito de resposta: saber do que se é acusado é o mínimo antes de qualquer revisão existir.
        // Já a identidade do denunciante exposta ao denunciado seria convite à retaliação.
        var session = login();
        var publicacao = publica(session);
        denuncia(leitor(), publicacao, "SPAM", null, status().isCreated());

        var corpo = mockMvc.perform(get(LIBRARY + "/" + publicacao + "/reports").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].reason", is("SPAM")))
                // Aberta: não há quem revise, e a história registra isso (DUV-COM-001).
                .andExpect(jsonPath("$[0].outcome").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(corpo)
                .doesNotContain("reporter").doesNotContain("breweryId");
    }

    @Test
    void outraCervejariaNaoLeAsDenunciasAlheias() throws Exception {
        // A lista é do autor. Ler as denúncias contra a receita do concorrente seria inteligência
        // competitiva servida de graça.
        var session = login();
        var publicacao = publica(session);
        denuncia(leitor(), publicacao, "ABUSE", "texto ofensivo", status().isCreated());

        mockMvc.perform(get(LIBRARY + "/" + publicacao + "/reports")
                        .with(authentication(principal(UUID.randomUUID(), UUID.randomUUID(),
                                Set.of("community.recipe.publish")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMesmaPessoaNaoDenunciaDuasVezesPeloMesmoMotivo() throws Exception {
        // A contagem de denúncias é sinal; um sinal que a mesma pessoa repete mede a insistência.
        var session = login();
        var publicacao = publica(session);
        var bruno = leitor();

        denuncia(bruno, publicacao, "SPAM", null, status().isCreated());
        denuncia(bruno, publicacao, "SPAM", null, status().isConflict());

        // Por outro motivo, sim: é outra acusação.
        denuncia(bruno, publicacao, "ABUSE", "e é ofensivo", status().isCreated());
    }

    @Test
    void outroMotivoExigeExplicacao() throws Exception {
        // "Outro" sem texto não é denúncia, é ruído: ninguém revisa o que não foi dito.
        var session = login();
        var publicacao = publica(session);

        denuncia(leitor(), publicacao, "OTHER", null, status().isBadRequest());
    }

    @Test
    void oAutorNaoSeDenuncia() throws Exception {
        // Se ele quer tirar do ar, o botão é despublicar.
        var session = login();
        var publicacao = publica(session);

        mockMvc.perform(post(LIBRARY + "/" + publicacao + "/reports").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("self_rating")));
    }

    @Test
    void semPermissaoNaoSeAvalia() throws Exception {
        var session = login();
        var publicacao = publica(session);

        mockMvc.perform(put(LIBRARY + "/" + publicacao + "/rating")
                        .with(authentication(principal(UUID.randomUUID(), UUID.randomUUID(),
                                Set.of("community.library.read"))))
                        .with(csrf()).contentType("application/json").content("{\"value\":4}"))
                .andExpect(status().isForbidden());
    }

    // --- ações ---

    private void avalia(Authentication quem, String publicacao, int nota,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        mockMvc.perform(put(LIBRARY + "/" + publicacao + "/rating").with(authentication(quem))
                        .with(csrf()).contentType("application/json")
                        .content("{\"value\":%d}".formatted(nota)))
                .andExpect(esperado);
    }

    private void denuncia(Authentication quem, String publicacao, String motivo, String texto,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        var corpo = texto == null
                ? "{\"reason\":\"%s\"}".formatted(motivo)
                : "{\"reason\":\"%s\",\"note\":\"%s\"}".formatted(motivo, texto);
        mockMvc.perform(post(LIBRARY + "/" + publicacao + "/reports").with(authentication(quem))
                        .with(csrf()).contentType("application/json").content(corpo))
                .andExpect(esperado);
    }

    /**
     * Uma pessoa de VERDADE em outra cervejaria.
     *
     * <p>A linha em {@code security_user} não é cerimônia de teste: a chave estrangeira da avaliação
     * aponta para lá, e é ela que impede uma nota de existir sem alguém por trás.
     */
    private Authentication leitor() {
        if (bruno == null) {
            bruno = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO security_user (id, email, normalized_email, display_name, status)
                    VALUES (:id, :email, :email, 'Bruno', 'ACTIVE')
                    """)
                    .param("id", bruno).param("email", "bruno-" + bruno + "@brassia.local").update();
        }
        return principal(bruno, UUID.randomUUID(),
                Set.of("community.rating.write", "community.library.read"));
    }

    private UUID bruno;

    // --- cenário ---

    private String escreve(MockHttpSession session, String publicacao, String kind, String body,
            String context) throws Exception {
        var corpo = context == null
                ? "{\"kind\":\"%s\",\"body\":\"%s\"}".formatted(kind, body)
                : "{\"kind\":\"%s\",\"body\":\"%s\",\"context\":\"%s\"}".formatted(kind, body, context);
        var resposta = mockMvc.perform(post(LIBRARY + "/" + publicacao + "/contributions")
                        .session(session).with(csrf()).contentType("application/json").content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(resposta).get("id").asText();
    }

    private String retrato(MockHttpSession session, String publicacao) throws Exception {
        var corpo = mockMvc.perform(get(LIBRARY + "/" + publicacao).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(corpo).get("recipe").toString();
    }

    private String publica(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var maiusculo = sfx.toUpperCase(java.util.Locale.ROOT);
        var equipamento = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"BH-%s","name":"BH","capacityLiters":900,"deadSpaceLiters":20,
                                 "mashEfficiencyPercent":72,"boilOffLitersPerHour":8}
                                """.formatted(maiusculo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var ingrediente = idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("""
                                {"type":"MALT","code":"MALTE-%s","name":"Malte Pilsen %s","useUnit":"KG",
                                 "purchaseUnit":"KG","attributes":{"potentialSg":"1.037","colorEbc":"3"}}
                                """.formatted(maiusculo, sfx)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var receita = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"IPA %s","equipmentId":"%s","batchVolumeLiters":400,
                                 "boilTimeMinutes":60,
                                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"}]}
                                """.formatted(sfx, equipamento, ingrediente)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/recipes/" + receita + "/publish").session(session).with(csrf()))
                .andExpect(status().is2xxSuccessful());

        var body = mockMvc.perform(post(LIBRARY).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"recipeId":"%s","title":"IPA %s","license":"CC0","visibility":"PUBLIC"}
                                """.formatted(receita, sfx)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID userId, UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(userId, breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
