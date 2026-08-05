import { expect, login, test } from './support';

/**
 * Recall e expedição (FDS-003 / TRC-001-D).
 *
 * <p>O dossiê é a tela mais composta da rastreabilidade: metade dela vem de uma lista guardada e a
 * outra metade é derivada do grafo na hora. Aqui as duas chegam da stack real.
 */
test.describe('recall', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a lista de recalls carrega e explica por onde começar', async ({ page }) => {
    const lista = page.waitForResponse(r => r.url().includes('/api/v1/traceability/recalls'));
    await page.goto('/traceability/recalls');
    expect((await lista).status()).toBe(200);

    await expect(page.getByRole('heading', { name: 'Recalls' })).toBeVisible();
  });

  test('produto acabado cruza lotes e expedições numa leitura só', async ({ page }) => {
    // Duas chamadas em paralelo: é a forma de defeito que os testes de unidade não pegam.
    const lots = page.waitForResponse(r => r.url().includes('/api/v1/packaging/finished-lots'));
    const shipments = page.waitForResponse(r => r.url().includes('/api/v1/packaging/shipments'));
    await page.goto('/packaging/finished-lots');

    expect((await lots).status()).toBe(200);
    expect((await shipments).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Produto acabado' })).toBeVisible();
  });

  test('abrir recall exige um nó: sem lote, a genealogia não oferece o botão', async ({ page }) => {
    await page.goto('/traceability/genealogy');

    await expect(page.getByText('Escolha um lote para ver a genealogia.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Abrir recall' })).toHaveCount(0);
  });
});
