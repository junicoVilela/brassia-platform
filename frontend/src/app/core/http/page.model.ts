/**
 * Envelope de paginação do contrato HTTP.
 *
 * Espelha `br.com.brew.brassia.shared.web.PageResponse`. `totalElements` é o campo que importa para a
 * interface ser honesta: sem ele, uma lista truncada é indistinguível de uma lista completa.
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Teto de itens por página aceito pelo servidor. Pedir mais é reduzido no servidor, não recusado. */
export const MAX_PAGE_SIZE = 100;
