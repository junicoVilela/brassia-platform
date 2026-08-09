import { csrfHeaders, expect, login, test } from './support';

/**
 * Webhooks (INT-002).
 *
 * <p>O que só aparece aqui é a jornada contra a stack real: criar um webhook pela tela, ver o segredo
 * aparecer uma única vez, e confirmar que ele não volta em nenhuma leitura posterior. A promessa "exibido
 * uma vez" é fácil de escrever e fácil de quebrar sem ninguém notar — só um teste que recarrega a tela e
 * relê a API prova que ela vale.
 */
test.describe('webhooks', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre com os webhooks vindos da stack real', async ({ page }) => {
    const subscriptions = page.waitForResponse(r =>
      r.url().endsWith('/api/v1/integration/webhooks'),
    );
    await page.goto('/integration/webhooks');

    expect((await subscriptions).status()).toBe(200);
    // `level: 1` porque "Webhooks" nomeia tanto a página quanto o card da lista.
    await expect(page.getByRole('heading', { name: 'Webhooks', level: 1 })).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('criar revela o segredo uma única vez e ele nunca mais volta', async ({ page }) => {
    const name = `E2E-ERP-${Date.now()}`;
    await page.goto('/integration/webhooks');

    await page.getByLabel('Nome').fill(name);
    await page.getByLabel('Destino').fill('https://erp.example.com/hooks/e2e');
    await page.getByLabel(/Ordem de produção liberada/).check();

    await page.getByRole('button', { name: 'Criar webhook' }).click();

    // O segredo é lido da TELA, não do corpo da resposta. Ler a resposta depois do clique é frágil — a
    // aplicação recarrega a lista em seguida, e o corpo de uma resposta cujo contexto navegou deixa de
    // estar disponível. Além disso, é o que a pessoa vê que importa: a promessa "exibido uma vez" é sobre
    // a tela.
    await expect(page.getByText('Guarde este segredo agora')).toBeVisible();
    const secret = (await page.locator('code.user-select-all').innerText()).trim();

    expect(secret.length).toBeGreaterThanOrEqual(40);

    // Dispensado, some da tela.
    await page.getByRole('button', { name: 'Já copiei' }).click();
    await expect(page.getByText('Guarde este segredo agora')).toBeHidden();

    // E não volta: nem recarregando a tela, nem lendo a API direto.
    await page.reload();
    await expect(page.getByText(secret, { exact: true })).toHaveCount(0);

    const listed = await page.request.get('/api/v1/integration/webhooks');
    expect(await listed.text()).not.toContain(secret);
  });

  test('destino http:// é recusado pela API', async ({ page }) => {
    // A assinatura protege integridade, não sigilo: em http o que acontece na cervejaria trafega em claro.
    await page.goto('/integration/webhooks');

    const response = await page.request.post('/api/v1/integration/webhooks', {
      headers: await csrfHeaders(page),
      data: {
        name: `E2E-INSEGURO-${Date.now()}`,
        endpoint: 'http://erp.example.com/hooks',
        events: ['brew_order.released'],
      },
    });

    expect(response.status()).toBe(400);
  });

  test('pausar e revogar pela tela, com o efeito explicado', async ({ page }) => {
    const name = `E2E-PAUSA-${Date.now()}`;
    await page.goto('/integration/webhooks');

    await page.getByLabel('Nome').fill(name);
    await page.getByLabel('Destino').fill('https://erp.example.com/hooks/e2e');
    await page.getByLabel(/Receita publicada/).check();
    const created = page.waitForResponse(
      r => r.url().endsWith('/api/v1/integration/webhooks') && r.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Criar webhook' }).click();
    await created;
    await page.getByRole('button', { name: 'Já copiei' }).click();

    await page.getByRole('button', { name: new RegExp(name) }).click();

    const paused = page.waitForResponse(r => r.url().includes('/status'));
    await page.getByRole('button', { name: 'Pausar' }).click();
    expect((await paused).status()).toBe(200);
    // A tela diz o que pausar significa: a fila continua.
    await expect(page.getByText(/O que já está na fila continua sendo entregue/)).toBeVisible();

    page.once('dialog', dialog => dialog.accept());
    const revoked = page.waitForResponse(r => r.url().includes('/status'));
    await page.getByRole('button', { name: 'Revogar' }).click();
    expect((await revoked).status()).toBe(200);
    await expect(page.getByText(/Para retomar, crie outro webhook/)).toBeVisible();
  });

  test('os tipos de evento vêm do servidor como allowlist fechada', async ({ page }) => {
    await page.goto('/integration/webhooks');

    const types = await (await page.request.get('/api/v1/integration/webhooks/event-types')).json();

    expect(types).toContain('brew_order.released');
    expect(types).toContain('recipe.published');
    expect(types).not.toContain('inventado.demais');
  });
});
