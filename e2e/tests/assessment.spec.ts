import { expect, login, test } from './support';

/**
 * Avaliar lote (AIA-002).
 *
 * <p>A stack de desenvolvimento não tem lote nem provedor, e o que este teste exercita é honesto sobre isso: a
 * rota, o guard de permissão, o estado vazio explicando por que ele está vazio, e — quando houver lote — a
 * chamada indo ao servidor e a recusa chegando como recusa em vez de tela quebrada.
 *
 * <p>A conferência de números é exercitada em {@code FactGroundingTest} e
 * {@code BatchAssessmentHandlerTest}, onde é possível programar exatamente a resposta que se quer estudar. Um
 * modelo de verdade responderia diferente a cada execução, o que é o oposto do que um teste precisa.
 */
test.describe('avaliar lote', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre com os lotes vindos da stack real', async ({ page }) => {
    const batches = page.waitForResponse(r => r.url().includes('/api/v1/production/batches'));
    await page.goto('/ai/assessments');

    expect((await batches).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Avaliar lote' })).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('a lista de lotes resolve num dos dois desfechos, e nenhum deles é tela quebrada', async ({
    page,
  }) => {
    // Contar antes de a lista renderizar daria zero por corrida, não por ausência de lote — foi o que este
    // teste fez na primeira versão. Esperar por um dos dois desfechos possíveis remove a corrida.
    await page.goto('/ai/assessments');

    const empty = page.getByText('Nenhum lote de produção ainda.');
    const buttons = page.getByRole('button', { name: 'Avaliar' });
    await expect(empty.or(buttons.first())).toBeVisible();

    if (await empty.isVisible()) {
      await expect(page.getByText('precisa de um lote para ler', { exact: false })).toBeVisible();
    } else {
      await expect(buttons.first()).toBeEnabled();
    }
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('com lote, a avaliação vai ao servidor e a recusa chega como recusa', async ({ page }) => {
    await page.goto('/ai/assessments');

    const empty = page.getByText('Nenhum lote de produção ainda.');
    const buttons = page.getByRole('button', { name: 'Avaliar' });
    await expect(empty.or(buttons.first())).toBeVisible();
    test.skip(await empty.isVisible(), 'a stack local não tem lote de produção');

    const assessed = page.waitForResponse(r => r.url().includes('/assessment'));
    await buttons.first().click();

    const response = await assessed;
    // Sem provedor nesta stack: 501. Com provedor, 200 — as duas são desfechos legítimos.
    expect([200, 501]).toContain(response.status());
    if (response.status() === 501) {
      await expect(page.locator('.alert-danger')).toContainText('não tem copiloto de IA habilitado');
    }
  });
});
