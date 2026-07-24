package br.com.brew.brassia.referencedata.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Estilo cervejeiro dentro de um conjunto: código, nome, família e faixas de
 * parâmetros. A impressão geral é sempre permitida; o perfil detalhado (texto
 * sensorial integral) só é guardado quando a permissão da fonte é integral.
 */
public final class Style {

    private final StyleId id;
    private final String code;
    private final String name;
    private final String family;
    private final String category;
    private final StyleRange og;
    private final StyleRange fg;
    private final StyleRange abv;
    private final StyleRange ibu;
    private final StyleRange color;
    private final String generalImpression;
    private final String detailedProfile;

    private Style(StyleId id, String code, String name, String family, String category, StyleRange og, StyleRange fg,
            StyleRange abv, StyleRange ibu, StyleRange color, String generalImpression, String detailedProfile) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = requireText(code, "code");
        this.name = requireText(name, "name");
        this.family = blankToNull(family);
        this.category = blankToNull(category);
        this.og = og == null ? StyleRange.none() : og;
        this.fg = fg == null ? StyleRange.none() : fg;
        this.abv = abv == null ? StyleRange.none() : abv;
        this.ibu = ibu == null ? StyleRange.none() : ibu;
        this.color = color == null ? StyleRange.none() : color;
        this.generalImpression = blankToNull(generalImpression);
        this.detailedProfile = blankToNull(detailedProfile);
    }

    /**
     * Cria um estilo aplicando o gate de conteúdo: o perfil detalhado só é
     * guardado quando {@code permission} autoriza o conteúdo integral.
     */
    public static Style create(String code, String name, String family, String category, StyleRange og,
            StyleRange fg, StyleRange abv, StyleRange ibu, StyleRange color, String generalImpression,
            String detailedProfile, PermissionStatus permission) {
        String gatedProfile = permission != null && permission.allowsFullContent() ? detailedProfile : null;
        return new Style(StyleId.newId(), code, name, family, category, og, fg, abv, ibu, color, generalImpression,
                gatedProfile);
    }

    public static Style reconstitute(StyleId id, String code, String name, String family, String category,
            StyleRange og, StyleRange fg, StyleRange abv, StyleRange ibu, StyleRange color, String generalImpression,
            String detailedProfile) {
        return new Style(id, code, name, family, category, og, fg, abv, ibu, color, generalImpression,
                detailedProfile);
    }

    /**
     * Compara os parâmetros calculados de uma receita com as faixas do estilo.
     * Só avalia parâmetros informados e faixas presentes; fora da faixa é aviso.
     */
    public List<RangeCheck> evaluate(BigDecimal ogValue, BigDecimal fgValue, BigDecimal abvValue, BigDecimal ibuValue,
            BigDecimal colorValue) {
        var checks = new ArrayList<RangeCheck>();
        addCheck(checks, "OG", ogValue, og);
        addCheck(checks, "FG", fgValue, fg);
        addCheck(checks, "ABV", abvValue, abv);
        addCheck(checks, "IBU", ibuValue, ibu);
        addCheck(checks, "COLOR", colorValue, color);
        return checks;
    }

    private static void addCheck(List<RangeCheck> checks, String metric, BigDecimal value, StyleRange range) {
        if (value == null || range.isEmpty()) {
            return;
        }
        checks.add(new RangeCheck(metric, value, range.min(), range.max(), range.unit(), range.contains(value)));
    }

    public StyleId id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String family() {
        return family;
    }

    public String category() {
        return category;
    }

    public StyleRange og() {
        return og;
    }

    public StyleRange fg() {
        return fg;
    }

    public StyleRange abv() {
        return abv;
    }

    public StyleRange ibu() {
        return ibu;
    }

    public StyleRange color() {
        return color;
    }

    public String generalImpression() {
        return generalImpression;
    }

    public String detailedProfile() {
        return detailedProfile;
    }

    private static String requireText(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
