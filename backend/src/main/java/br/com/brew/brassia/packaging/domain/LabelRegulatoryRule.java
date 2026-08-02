package br.com.brew.brassia.packaging.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Regra regulatória do rótulo (PKG-004): quais campos são obrigatórios para a cervejaria imprimir.
 *
 * <p>Vive <strong>separada do template</strong> de propósito. O template é layout — muda quando o
 * designer quer outra arte. A obrigatoriedade é lei, e não pode sumir porque alguém trocou o
 * desenho. Separando as duas, uma troca de layout que deixe de fora um campo exigido é barrada na
 * prévia em vez de virar um lote inteiro de rótulos irregulares.
 *
 * <p>Quais campos a lei exige depende do país e da categoria da bebida, então a lista é da
 * cervejaria: o sistema não decide regulação por ela.
 */
public record LabelRegulatoryRule(Set<LabelField> requiredFields) {

    public LabelRegulatoryRule {
        Objects.requireNonNull(requiredFields, "campos obrigatórios são obrigatórios");
        if (requiredFields.isEmpty()) {
            throw new IllegalArgumentException("regra sem nenhum campo obrigatório não regula nada");
        }
        requiredFields = EnumSet.copyOf(requiredFields);
    }

    public boolean requires(LabelField field) {
        return requiredFields.contains(field);
    }
}
