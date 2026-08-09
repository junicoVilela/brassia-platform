import { expect, test as base, type Page } from '@playwright/test';

/**
 * Admin criado pelo bootstrap do perfil `local` (ver `application-local.yml`).
 * Credenciais descartáveis, só de desenvolvimento — nunca valem fora dele.
 */
export const ADMIN = {
  email: process.env.E2E_ADMIN_EMAIL || 'admin@brassia.local',
  password: process.env.E2E_ADMIN_PASSWORD || 'admin-local-123',
};

/**
 * Mensagens de erro do console que não indicam defeito.
 *
 * 1. `Failed to load resource` é ruído de rede do navegador: 4xx esperado é
 *    comportamento da aplicação — senha errada devolve 401, cervejaria sem regra
 *    de rótulo devolve 400 — e cada teste verifica o que lhe importa com
 *    `waitForResponse`.
 * 2. Assets do tema Fila são pagos e não versionados (ver
 *    `frontend/THEME_SETUP.md`), então na CI eles não existem e o `ng serve`
 *    devolve o `index.html` no lugar. A aplicação funciona sem o visual do tema,
 *    que é justamente o previsto no THEME_SETUP — a jornada não depende dele.
 */
function ruidoEsperado(texto: string): boolean {
  return texto.startsWith('Failed to load resource') || texto.includes('/assets/fila/');
}

/**
 * `test` estendido: erro no console do navegador ou exceção não tratada reprova
 * o teste.
 *
 * Existe porque erro de render não aparece em asserção de conteúdo. Um `@for`
 * sobre valor não-iterável — endpoint paginado tratado como array — quebra a
 * lista em silêncio: a tela ainda mostra o estado vazio e um `toBeVisible` passa
 * sobre uma tela quebrada. Foi assim que o defeito das telas de envase e gases
 * atravessou os testes de unidade.
 */
export const test = base.extend<Record<string, never>>({
  page: async ({ page }, use) => {
    const erros: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error' && !ruidoEsperado(msg.text())) {
        erros.push(msg.text());
      }
    });
    page.on('pageerror', err => erros.push(String(err)));

    await use(page);

    expect(erros, `erros no console do navegador:\n${erros.join('\n')}`).toEqual([]);
  },
});

export { expect };

/**
 * Cabeçalho CSRF que o Angular mandaria.
 *
 * <p>Chamada direta pela `page.request` não passa pelo `HttpClient`, então o interceptor que copia o
 * cookie `XSRF-TOKEN` para o cabeçalho não roda — e o servidor responde 403. É um 403 de proteção CSRF,
 * não de permissão, e a confusão entre os dois custa tempo: parece que falta alçada quando falta
 * cabeçalho.
 */
export async function csrfHeaders(page: Page): Promise<Record<string, string>> {
  const cookies = await page.context().cookies();
  const token = cookies.find(cookie => cookie.name === 'XSRF-TOKEN');
  return token ? { 'X-XSRF-TOKEN': token.value } : {};
}

/** Faz login e espera o shell da aplicação aparecer. */
export async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('E-mail').fill(ADMIN.email);
  await page.getByLabel('Senha').fill(ADMIN.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.locator('#sidebar-area')).toBeVisible();
}
