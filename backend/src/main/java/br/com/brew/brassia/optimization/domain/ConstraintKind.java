package br.com.brew.brassia.optimization.domain;

/** O tipo de restrição que limita o espaço de busca (OPT-001). */
public enum ConstraintKind {
    /** Teto de custo por litro. */
    MAX_COST_PER_LITER,
    /** Faixa aceitável de IBU. */
    IBU_RANGE,
    /** Faixa aceitável de cor (EBC). */
    COLOR_RANGE,
    /** Ingrediente que não pode sair da receita. */
    KEEP_INGREDIENT,
    /** Ingrediente que não pode entrar — alergênico, contrato, preferência. */
    EXCLUDE_INGREDIENT,
    /** Só o que está em estoque na quantidade necessária. */
    STOCK_ONLY
}
