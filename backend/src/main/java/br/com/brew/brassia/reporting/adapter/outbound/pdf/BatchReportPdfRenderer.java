package br.com.brew.brassia.reporting.adapter.outbound.pdf;

import br.com.brew.brassia.shared.money.Money;
import br.com.brew.brassia.reporting.domain.BatchReport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * O dossiê do lote em PDF (RPT-001-A).
 *
 * <p><strong>Layout simples e sem identidade visual, por decisão.</strong> O débito ficou três sprints
 * aberto esperando "decisão sobre marca e assinatura". Esperar a marca para imprimir o documento é o que
 * mantinha a cervejaria sem nada para mandar ao auditor — e marca é acabamento, não conteúdo. Cabeçalho,
 * dados, rodapé; quando houver identidade, ela entra sobre um documento que já funciona.
 *
 * <p><strong>Fontes padrão do PDF, e é uma decisão de portabilidade.</strong> Helvetica é uma das 14
 * fontes que todo leitor de PDF tem: embutir uma fonte própria pesaria o arquivo e exigiria licença de
 * distribuição — para um documento que a cervejaria manda por e-mail, isso é custo sem retorno.
 *
 * <p><strong>As lacunas vêm no topo, logo abaixo do cabeçalho.</strong> É a mesma regra da tela: quem
 * imprime precisa ver o que o relatório <em>não</em> prova antes de mandá-lo a um cliente que vai lê-lo
 * como se provasse tudo. Num papel, o rodapé é onde a informação vai morrer.
 */
@Component
public class BatchReportPdfRenderer {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private static final float MARGIN = 50;
    private static final float TITLE_SIZE = 16;
    private static final float HEADING_SIZE = 11;
    private static final float BODY_SIZE = 9.5f;
    private static final float LINE = 14;

    public byte[] render(BatchReport report) {
        try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var writer = new PageWriter(document);

            writer.title("Relatório do lote " + report.batchCode());
            writer.body(nullSafe(report.recipeName()) + " · versão " + report.recipeVersion()
                    + " · situação " + nullSafe(report.status()));
            // A data de geração é do documento, não do lote: um relatório impresso ontem e lido hoje
            // descreve o lote de ontem, e sem a data ninguém sabe disso.
            writer.body("Gerado em " + TIMESTAMP.format(report.generatedAt()));
            writer.gap();

            if (report.incomplete()) {
                writer.heading("O que este relatório NÃO prova");
                for (var gap : report.gaps()) {
                    writer.bullet(gap);
                }
                writer.gap();
            }

            writer.heading("Plano");
            writer.body("Volume planejado: " + liters(report.plan().volumeLiters()));
            if (report.plan().materials().isEmpty()) {
                writer.body("Sem plano de materiais confiável para este lote.");
            } else {
                writer.body("Materiais planejados: " + report.plan().materials().size() + " item(ns)");
            }
            writer.gap();

            writer.heading("Execução");
            var execution = report.execution();
            if (execution.transferred()) {
                writer.body("Transferido ao fermentador: " + liters(execution.transferredVolumeLiters())
                        + " · perda declarada: " + liters(execution.transferLossesLiters()));
            } else {
                writer.body("Lote ainda não transferido.");
            }
            if (execution.packaged()) {
                for (var run : execution.packaging()) {
                    writer.bullet("Plano " + run.planCode() + ": envasado "
                            + liters(run.packagedVolumeLiters()) + ", rejeito "
                            + liters(run.rejectedVolumeLiters()) + ", perda de linha "
                            + liters(run.lossesLiters()));
                }
            } else {
                writer.body("Lote ainda não envasado.");
            }
            writer.gap();

            writer.heading("Qualidade");
            var quality = report.quality();
            if (quality == null) {
                writer.body("Sem dados de qualidade.");
            } else {
                writer.body(quality.measurements() + " medição(ões), " + quality.withinSpec()
                        + " dentro da especificação, " + quality.outOfSpec().size() + " fora, "
                        + quality.deviations().size() + " desvio(s), "
                        + quality.nonConformities().size() + " não conformidade(s)");
            }
            writer.gap();

            writer.heading("Custo");
            var cost = report.cost();
            if (cost == null) {
                writer.body("Custo ainda não apurado.");
            } else {
                // A moeda entra no papel: um relatório impresso circula fora do sistema, e "1.240,00"
                // sem moeda é o número que alguém soma com outro de outra casa.
                writer.body("Total: " + money(cost.total()) + " · por litro: "
                        + money(cost.costPerLiter())
                        + " · " + (cost.closed() ? "fechado" : "aberto, ainda muda"));
                for (var gap : cost.gaps()) {
                    writer.bullet("Fora do custo: " + gap);
                }
            }
            writer.gap();

            writer.heading("Genealogia");
            var lineage = report.lineage();
            if (lineage == null) {
                writer.body("Sem genealogia.");
            } else {
                writer.body(lineage.origins().size() + " origem(ns) e " + lineage.destinations().size()
                        + " destino(s)" + (lineage.truncated() ? " (travessia truncada)" : ""));
            }

            writer.close();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            // Falha ao montar o PDF é falha do servidor, não do pedido: quem chamou fez tudo certo.
            throw new UncheckedIOException("falha ao gerar o PDF do relatório", e);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "—" : value;
    }

    /** Vazio é "não existe", e vira travessão — não zero, que seria afirmar que não houve volume. */
    private static String liters(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString() + " L";
    }

    private static String money(Money value) {
        return value == null ? "—" : value.toMinorUnit().toPlainString() + " " + value.currency();
    }

    /**
     * Escreve de cima para baixo e vira a página sozinho.
     *
     * <p>Sem isto, um lote com muitas lacunas escreveria fora do papel — o PDFBox não recusa, ele
     * simplesmente desenha o texto onde ninguém vai ler.
     */
    private static final class PageWriter {

        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;

        PageWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void ensureRoom() throws IOException {
            if (y < MARGIN + LINE) {
                newPage();
            }
        }

        void title(String text) throws IOException {
            write(text, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TITLE_SIZE);
            y -= 4;
        }

        void heading(String text) throws IOException {
            write(text, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), HEADING_SIZE);
        }

        void body(String text) throws IOException {
            write(text, new PDType1Font(Standard14Fonts.FontName.HELVETICA), BODY_SIZE);
        }

        void bullet(String text) throws IOException {
            // Quebra por largura, e não por contagem de caracteres: um texto com palavras longas
            // estouraria a margem justamente nas lacunas, que são as linhas mais compridas.
            for (var line : wrap("• " + text, 95)) {
                write(line, new PDType1Font(Standard14Fonts.FontName.HELVETICA), BODY_SIZE);
            }
        }

        void gap() {
            y -= LINE / 2;
        }

        private void write(String text, PDType1Font font, float size) throws IOException {
            ensureRoom();
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(sanitize(text));
            stream.endText();
            y -= LINE;
        }

        /**
         * O Helvetica padrão do PDF não codifica tudo que o português escreve.
         *
         * <p>Um caractere fora do WinAnsi derruba a geração inteira com exceção — e derrubar o
         * relatório por causa de um símbolo num texto de lacuna seria perder o documento por causa do
         * acabamento. Trocar por interrogação preserva o resto.
         */
        private static String sanitize(String text) {
            var out = new StringBuilder(text.length());
            for (var c : text.toCharArray()) {
                out.append(c < 256 || c == '•' || c == '—' || c == '·' ? c : '?');
            }
            return out.toString();
        }

        private static List<String> wrap(String text, int max) {
            var lines = new ArrayList<String>();
            var current = new StringBuilder();
            for (var word : text.split(" ")) {
                if (current.length() + word.length() + 1 > max && !current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append("  ");
                }
                if (!current.isEmpty() && current.charAt(current.length() - 1) != ' ') {
                    current.append(' ');
                }
                current.append(word);
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }

        void close() throws IOException {
            stream.close();
        }
    }
}
