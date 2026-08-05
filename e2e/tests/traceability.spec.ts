import { expect, login, test } from './support';

/**
 * Genealogia (TRC-001).
 *
 * A tela compõe um grafo que vem de cinco módulos diferentes e chega com nós de tipos variados.
 * É o tipo de resposta que um mock de teste de unidade sempre devolve bem-comportada — e que na
 * stack real pode chegar com nó sem rótulo, aresta sem data ou tipo que a tela não conhece.
 */
test.describe('genealogia', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('nó inexistente é recusado sem quebrar a tela', async ({ page }) => {
    const consulta = page.waitForResponse(r => r.url().includes('/api/v1/traceability/genealogy'));
    await page.goto(
      '/traceability/genealogy?nodeType=BATCH&nodeId=00000000-0000-0000-0000-000000000000',
    );
    expect((await consulta).status()).toBe(404);

    // A recusa é comportamento da regra: nó inexistente não é o mesmo que nó sem elos.
    await expect(page.getByText('não existe nesta cervejaria')).toBeVisible();
  });

  test('sem nó, a tela explica por onde começar', async ({ page }) => {
    await page.goto('/traceability/genealogy');

    await expect(page.getByRole('heading', { name: 'Genealogia' })).toBeVisible();
    await expect(page.getByText('Escolha um lote para ver a genealogia.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Ir para lotes de produção' })).toBeVisible();
  });
});
