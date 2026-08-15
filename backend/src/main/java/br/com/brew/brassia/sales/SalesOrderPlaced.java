package br.com.brew.brassia.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um pedido foi confirmado (INT-008).
 *
 * <p><strong>Evento publicado, e não porta:</strong> quem reage a uma venda — fiscal para emitir a nota,
 * POS e e-commerce para acertar o estoque deles — não é assunto de vendas, e o pedido não pode depender de
 * nenhum deles estar de pé. É a diferença que o critério da sprint exige: "integração externa falha sem
 * corromper pedido".
 *
 * <p><strong>Não carrega dado pessoal.</strong> {@code customerId} é a organização compradora, que é dado
 * de negócio; contato, e-mail e telefone ficam de fora. Mandá-los para um provedor externo furaria a
 * regra que a CRM-001 existe para sustentar — consentimento é por finalidade, e "integrar com o POS" não
 * é finalidade que alguém consentiu. Quem precisar do contato pede pela API, com alçada.
 *
 * @param total valor com moeda explícita ao lado, como todo dinheiro nesta sprint
 */
public record SalesOrderPlaced(UUID breweryId, UUID orderId, String code, UUID customerId,
        UUID channelId, BigDecimal total, String currency, LocalDate placedOn, LocalDate promisedFor,
        Instant occurredAt) {}
