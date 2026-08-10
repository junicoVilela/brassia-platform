package br.com.brew.brassia.security.adapter.outbound.federation;

import br.com.brew.brassia.security.domain.InvalidSsoHandshakeException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import net.shibboleth.shared.xml.SerializeSupport;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.security.credential.BasicCredential;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.w3c.dom.Element;

/**
 * Verifica a resposta SAML do provedor (DEB-SEC-001, parte SAML).
 *
 * <p><strong>A assinatura é conferida antes de qualquer coisa ser lida</strong>, exatamente como no OIDC.
 * Um XML não assinado — ou assinado por outra chave — é texto que o atacante escreveu, e ler o
 * {@code NameID} dele antes de validar é acreditar no que se está tentando verificar.
 *
 * <p><strong>Assinatura na ASSERTION, não só na Response.</strong> É a diferença que os ataques de
 * <em>XML Signature Wrapping</em> exploram: uma Response assinada pode carregar uma assertion trocada, e
 * quem valida só o envelope aceita o conteúdo adulterado. Aqui a assertion consumida é a mesma que teve a
 * assinatura conferida — a referência é a mesma instância, não uma busca repetida no documento.
 *
 * <p><strong>DOCTYPE é recusado</strong> (XXE): entidade externa num XML de fronteira lê arquivo do
 * servidor. Mesma decisão do BeerXML, e aqui vale mais porque a fonte é ainda menos confiável.
 */
final class SamlResponseVerifier {

    private static final Base64.Decoder BASE64 = Base64.getMimeDecoder();

    static {
        try {
            InitializationService.initialize();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private SamlResponseVerifier() {
    }

    /**
     * @param samlResponse o {@code SAMLResponse} como veio no form POST, em base64
     * @param certificate  o certificado do IdP, em base64 (sem cabeçalho PEM)
     * @return a assertion cuja assinatura foi conferida
     */
    static Assertion verify(String samlResponse, String certificate) {
        var response = parse(samlResponse);
        var credential = credentialOf(certificate);

        // Assinatura na Response, quando existir. Não basta sozinha — ver o Javadoc sobre wrapping.
        signatureOf(response.getSignature()).ifPresent(sig -> check(sig, credential));

        var assertions = response.getAssertions();
        if (assertions.size() != 1) {
            // Zero é resposta sem identidade; mais de uma é ambiguidade que o wrapping usa. Nos dois
            // casos a recusa é a mesma, porque distinguir ensinaria qual caminho tentar.
            throw new InvalidSsoHandshakeException("resposta SAML não confere");
        }
        var assertion = assertions.getFirst();

        // A ASSERTION precisa estar assinada. Uma Response assinada com assertion sem assinatura é
        // exatamente o vetor de wrapping: o envelope confere e o conteúdo não foi verificado.
        var assertionSignature = signatureOf(assertion.getSignature())
                .orElseThrow(() -> new InvalidSsoHandshakeException("resposta SAML não confere"));
        check(assertionSignature, credential);
        return assertion;
    }

    private static Response parse(String samlResponse) {
        try {
            var xml = new String(BASE64.decode(samlResponse), StandardCharsets.UTF_8);
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE: sem DOCTYPE não há declaração de entidade, e sem entidade não há leitura de arquivo.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);

            var document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = document.getDocumentElement();

            UnmarshallerFactory unmarshallers = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
            var unmarshaller = unmarshallers.getUnmarshaller(root);
            if (unmarshaller == null) {
                throw new InvalidSsoHandshakeException("resposta SAML não confere");
            }
            var object = unmarshaller.unmarshall(root);
            if (!(object instanceof Response response)) {
                throw new InvalidSsoHandshakeException("resposta SAML não confere");
            }
            return response;
        } catch (InvalidSsoHandshakeException e) {
            throw e;
        } catch (Exception e) {
            // XML malformado, base64 inválido, elemento desconhecido — todos viram a mesma recusa.
            throw new InvalidSsoHandshakeException("resposta SAML não confere");
        }
    }

    private static Optional<Signature> signatureOf(Signature signature) {
        return Optional.ofNullable(signature);
    }

    private static void check(Signature signature, BasicCredential credential) {
        try {
            SignatureValidator.validate(signature, credential);
        } catch (Exception e) {
            throw new InvalidSsoHandshakeException("resposta SAML não confere");
        }
    }

    private static BasicCredential credentialOf(String certificate) {
        try {
            var der = Base64.getMimeDecoder().decode(certificate
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .trim());
            var x509 = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
            return new BasicCredential(x509.getPublicKey());
        } catch (Exception e) {
            // Certificado mal cadastrado é erro de configuração, não de quem tenta entrar — mas a
            // mensagem não distingue, pelo mesmo motivo das demais.
            throw new InvalidSsoHandshakeException("resposta SAML não confere");
        }
    }

    /** O XML da assertion como texto, para diagnóstico em teste. Não usado em produção. */
    static String serialize(Assertion assertion) {
        return SerializeSupport.nodeToString(assertion.getDOM());
    }

    static List<String> attributeValues(Assertion assertion, String name) {
        return assertion.getAttributeStatements().stream()
                .flatMap(st -> st.getAttributes().stream())
                .filter(a -> name.equals(a.getName()) || name.equals(a.getFriendlyName()))
                .flatMap(a -> a.getAttributeValues().stream())
                .map(v -> v.getDOM() == null ? null : v.getDOM().getTextContent())
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
