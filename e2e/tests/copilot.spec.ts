import { expect, login, test } from './support';

/**
 * Perguntar ao copiloto (RAG-002).
 *
 * <p>Na stack de desenvolvimento o provedor está desligado, que é o default do produto. Isso limita o que o
 * E2E pode afirmar, e o limite é honesto: o que ele exercita é a jornada até a fronteira do modelo — rota,
 * guard de permissão, formulário, chamada, e a recusa chegando à tela como recusa e não como tela quebrada.
 * A resposta sustentada, com citação conferida, é exercitada no `CopilotIT` com um provedor programável, que
 * é onde ela pode ser estudada de forma reprodutível.
 */
test.describe('perguntar ao copiloto', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre e o botão só libera com pergunta de tamanho razoável', async ({ page }) => {
    await page.goto('/ai/copilot');

    await expect(page.getByRole('heading', { name: 'Perguntar ao copiloto' })).toBeVisible();
    // Pergunta de duas letras não é pergunta: o formulário barra antes de gastar uma chamada.
    await page.getByLabel('Pergunta').fill('ab');
    await expect(page.getByRole('button', { name: 'Perguntar' })).toBeDisabled();

    await page.getByLabel('Pergunta').fill('qual a concentração de peracético para sanitizar');
    await expect(page.getByRole('button', { name: 'Perguntar' })).toBeEnabled();
  });

  test('sem provedor, a recusa chega à tela como recusa', async ({ page }) => {
    await page.goto('/ai/copilot');
    await page.getByLabel('Pergunta').fill('qual a concentração de peracético para sanitizar');

    const asked = page.waitForResponse(r => r.url().includes('/api/v1/ai/copilot/ask'));
    await page.getByRole('button', { name: 'Perguntar' }).click();

    const response = await asked;
    // Ou 501 (sem provedor, o caso desta stack) ou 200 com resposta sem fonte — as duas são desfechos
    // legítimos e nenhuma delas pode quebrar a tela.
    expect([200, 501]).toContain(response.status());

    if (response.status() === 501) {
      await expect(page.locator('.alert-danger')).toContainText('não tem copiloto de IA habilitado');
    } else {
      await expect(page.getByText('Não há fonte indexada sobre isso.')).toBeVisible();
    }
  });

  test('a data de vigência das fontes viaja na pergunta', async ({ page }) => {
    await page.goto('/ai/copilot');
    await page.getByLabel('Pergunta').fill('qual a concentração de peracético em maio');
    await page.getByLabel('Fontes vigentes em').fill('2026-05-01');

    const asked = page.waitForResponse(r => r.url().includes('/api/v1/ai/copilot/ask'));
    await page.getByRole('button', { name: 'Perguntar' }).click();

    const request = (await asked).request();
    expect(request.postDataJSON()).toMatchObject({ onDate: '2026-05-01' });
  });
});
