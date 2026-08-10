import { expect, login, test, seedBatch } from './support';

/**
 * Propostas de comando (AIA-003).
 *
 * <p>A stack de desenvolvimento não tem provedor de IA, então nenhuma proposta nasce por aqui. O que este
 * teste exercita é o que existe independentemente do modelo: a rota, o guard, os três blocos da tela, e o
 * pedido de proposta chegando ao servidor e voltando como recusa explicada em vez de tela quebrada.
 *
 * <p>A regra que a história de fato produz — confirmar exige a alçada do comando, conferida no aceite — é
 * verificada em {@code CommandProposalIT} com princípios de permissões controladas, que é o único lugar onde
 * dá para escolher exatamente que alçada quem confirma tem. Aqui só há o admin de bootstrap, e ele tem todas.
 */
test.describe('propostas do copiloto', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await seedBatch(page);
  });

  test('a tela abre com as propostas vindas da stack real', async ({ page }) => {
    const proposals = page.waitForResponse(r => r.url().includes('/api/v1/ai/proposals'));
    await page.goto('/ai/proposals');

    expect((await proposals).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Propostas do copiloto' })).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('sem proposta pendente a tela diz o que fazer, em vez de ficar vazia', async ({ page }) => {
    await page.goto('/ai/proposals');

    // Num banco sem provedor não há proposta. O estado vazio precisa ensinar o próximo passo.
    const empty = page.getByText('Nenhuma proposta esperando decisão.');
    const pending = page.getByRole('button', { name: 'Confirmar' });
    await expect(empty.or(pending.first())).toBeVisible();

    if (await empty.isVisible()) {
      await expect(page.getByText('Peça uma proposta para um lote abaixo.')).toBeVisible();
    }
  });

  test('o subtítulo diz quem decide, porque é a regra da tela', async ({ page }) => {
    await page.goto('/ai/proposals');

    await expect(page.getByText('Nada acontece sem essa decisão.', { exact: false })).toBeVisible();
    await expect(page.getByText('Pedir não dá direito de confirmar.', { exact: false })).toBeVisible();
  });

  test('com lote, pedir proposta vai ao servidor e a recusa chega como recusa', async ({ page }) => {
    await page.goto('/ai/proposals');

    const empty = page.getByText('Nenhum lote de produção ainda.');
    const buttons = page.getByRole('button', { name: 'Pedir proposta' });
    await expect(empty.or(buttons.first())).toBeVisible();
    // Semeado no beforeEach: o vazio aqui seria defeito, não ambiente.
    await expect(empty).toBeHidden();

    const proposed = page.waitForResponse(r => r.url().includes('/api/v1/ai/proposals/batches/'));
    await buttons.first().click();

    const response = await proposed;
    // Sem provedor nesta stack: 501. Com provedor, 200 — as duas são desfechos legítimos.
    expect([200, 501]).toContain(response.status());
    if (response.status() === 501) {
      await expect(page.locator('.alert-danger')).toContainText('não tem copiloto de IA habilitado');
    }
  });
});
