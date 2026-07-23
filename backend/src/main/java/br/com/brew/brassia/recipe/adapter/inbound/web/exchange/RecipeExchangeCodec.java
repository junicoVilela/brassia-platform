package br.com.brew.brassia.recipe.adapter.inbound.web.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Converte receitas entre o modelo neutro e os formatos BeerJSON/BeerXML (subset),
 * reportando os campos não reconhecidos na importação.
 */
@Component
public class RecipeExchangeCodec {
    public enum Format { BEERJSON, BEERXML }

    private static final Set<String> ROOT_FIELDS = Set.of("name", "equipmentId", "batchVolumeLiters",
            "boilTimeMinutes", "targetOgPoints", "targetIbu", "targetColorEbc", "targetAbv", "items");
    private static final Set<String> ITEM_FIELDS = Set.of("ingredientId", "stage", "quantity", "unit",
            "timingMinutes", "percentage");

    private final ObjectMapper json = new ObjectMapper();

    public Format format(String raw) {
        if (raw == null) {
            return Format.BEERJSON;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "beerxml", "xml" -> Format.BEERXML;
            case "beerjson", "json" -> Format.BEERJSON;
            default -> throw new IllegalArgumentException("formato inválido (use beerjson ou beerxml)");
        };
    }

    public String contentType(Format format) {
        return format == Format.BEERXML ? "application/xml" : "application/json";
    }

    public ParsedDocument parse(Format format, String content) {
        return format == Format.BEERXML ? parseXml(content) : parseJson(content);
    }

    public String write(Format format, RecipeDocument document) {
        return format == Format.BEERXML ? writeXml(document) : writeJson(document);
    }

    // ---------- BeerJSON ----------

    private ParsedDocument parseJson(String content) {
        JsonNode root;
        try {
            root = json.readTree(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("documento BeerJSON inválido");
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("documento BeerJSON inválido");
        }
        var unknown = new ArrayList<String>();
        root.fieldNames().forEachRemaining(f -> {
            if (!ROOT_FIELDS.contains(f)) {
                unknown.add(f);
            }
        });
        var items = new ArrayList<RecipeDocument.Item>();
        var itemsNode = root.get("items");
        if (itemsNode != null && itemsNode.isArray()) {
            int i = 0;
            for (var itemNode : itemsNode) {
                final int idx = i++;
                itemNode.fieldNames().forEachRemaining(f -> {
                    if (!ITEM_FIELDS.contains(f)) {
                        unknown.add("items[" + idx + "]." + f);
                    }
                });
                items.add(new RecipeDocument.Item(uuid(text(itemNode, "ingredientId")), text(itemNode, "stage"),
                        decimal(text(itemNode, "quantity")), text(itemNode, "unit"),
                        integer(text(itemNode, "timingMinutes")), decimal(text(itemNode, "percentage"))));
            }
        }
        var document = new RecipeDocument(text(root, "name"), uuid(text(root, "equipmentId")),
                decimal(text(root, "batchVolumeLiters")), integer(text(root, "boilTimeMinutes")),
                decimal(text(root, "targetOgPoints")), decimal(text(root, "targetIbu")),
                decimal(text(root, "targetColorEbc")), decimal(text(root, "targetAbv")), items);
        return new ParsedDocument(document, unknown);
    }

    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String writeJson(RecipeDocument document) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(document);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar BeerJSON", e);
        }
    }

    // ---------- BeerXML ----------

    private ParsedDocument parseXml(String content) {
        Element root;
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            root = doc.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalArgumentException("documento BeerXML inválido");
        }
        var unknown = new ArrayList<String>();
        var items = new ArrayList<RecipeDocument.Item>();
        for (var child : elements(root)) {
            var tag = child.getTagName();
            if (tag.equals("items")) {
                int idx = 0;
                for (var itemEl : elements(child)) {
                    if (!itemEl.getTagName().equals("item")) {
                        unknown.add("items." + itemEl.getTagName());
                        continue;
                    }
                    for (var field : elements(itemEl)) {
                        if (!ITEM_FIELDS.contains(field.getTagName())) {
                            unknown.add("items[" + idx + "]." + field.getTagName());
                        }
                    }
                    items.add(new RecipeDocument.Item(uuid(child(itemEl, "ingredientId")), child(itemEl, "stage"),
                            decimal(child(itemEl, "quantity")), child(itemEl, "unit"),
                            integer(child(itemEl, "timingMinutes")), decimal(child(itemEl, "percentage"))));
                    idx++;
                }
            } else if (!ROOT_FIELDS.contains(tag)) {
                unknown.add(tag);
            }
        }
        var document = new RecipeDocument(child(root, "name"), uuid(child(root, "equipmentId")),
                decimal(child(root, "batchVolumeLiters")), integer(child(root, "boilTimeMinutes")),
                decimal(child(root, "targetOgPoints")), decimal(child(root, "targetIbu")),
                decimal(child(root, "targetColorEbc")), decimal(child(root, "targetAbv")), items);
        return new ParsedDocument(document, unknown);
    }

    private String writeXml(RecipeDocument d) {
        var sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<recipe>\n");
        el(sb, "name", d.name());
        el(sb, "equipmentId", d.equipmentId());
        el(sb, "batchVolumeLiters", d.batchVolumeLiters());
        el(sb, "boilTimeMinutes", d.boilTimeMinutes());
        el(sb, "targetOgPoints", d.targetOgPoints());
        el(sb, "targetIbu", d.targetIbu());
        el(sb, "targetColorEbc", d.targetColorEbc());
        el(sb, "targetAbv", d.targetAbv());
        sb.append("  <items>\n");
        for (var i : d.items()) {
            sb.append("    <item>\n");
            el(sb, "ingredientId", i.ingredientId(), 6);
            el(sb, "stage", i.stage(), 6);
            el(sb, "quantity", i.quantity(), 6);
            el(sb, "unit", i.unit(), 6);
            el(sb, "timingMinutes", i.timingMinutes(), 6);
            el(sb, "percentage", i.percentage(), 6);
            sb.append("    </item>\n");
        }
        sb.append("  </items>\n</recipe>\n");
        return sb.toString();
    }

    private static void el(StringBuilder sb, String tag, Object value) {
        el(sb, tag, value, 2);
    }

    private static void el(StringBuilder sb, String tag, Object value, int indent) {
        if (value == null) {
            return;
        }
        sb.append(" ".repeat(indent)).append('<').append(tag).append('>')
                .append(escape(value.toString())).append("</").append(tag).append(">\n");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static List<Element> elements(Element parent) {
        var result = new ArrayList<Element>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) n);
            }
        }
        return result;
    }

    private static String child(Element parent, String tag) {
        for (var el : elements(parent)) {
            if (el.getTagName().equals(tag)) {
                var text = el.getTextContent();
                return text == null || text.isBlank() ? null : text.trim();
            }
        }
        return null;
    }

    // ---------- valores ----------

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("id inválido no documento: " + raw);
        }
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("número inválido no documento: " + raw);
        }
    }

    private static Integer integer(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("inteiro inválido no documento: " + raw);
        }
    }
}
