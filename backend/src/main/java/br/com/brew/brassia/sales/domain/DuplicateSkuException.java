package br.com.brew.brassia.sales.domain;

/**
 * Já existe produto com este SKU na cervejaria (SAL-001).
 *
 * <p>A garantia é o índice único {@code ux_sales_product_sku} — checagem prévia não é garantia, porque
 * duas requisições simultâneas passam as duas por ela. Esta exceção existe para o caso comum devolver
 * 409 dizendo qual código colidiu, em vez de um erro de banco.
 */
public class DuplicateSkuException extends RuntimeException {

    private final String code;

    public DuplicateSkuException(String what, String code) {
        super("já existe " + what + " com o código " + code + " nesta cervejaria");
        this.code = code;
    }

    public String code() {
        return code;
    }
}
