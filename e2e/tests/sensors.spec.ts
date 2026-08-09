import { csrfHeaders, expect, login, test } from './support';

/**
 * Sensores (INT-001).
 *
 * <p>O que só aparece aqui é a jornada contra a stack real: cadastrar um dispositivo pela tela, mandar
 * leituras pela API como um gateway mandaria, e ver a série aparecer com qualidade e atraso marcados. A
 * idempotência é exercitada pelo caminho de verdade — o mesmo `messageId` duas vezes, contra o PostgreSQL
 * que decide.
 */
test.describe('sensores', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('a tela abre com os dispositivos vindos da stack real', async ({ page }) => {
    const devices = page.waitForResponse(r => r.url().includes('/api/v1/sensors/devices'));
    await page.goto('/sensors');

    expect((await devices).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Sensores' })).toBeVisible();
    await expect(page.locator('.alert-danger')).toHaveCount(0);
  });

  test('cadastrar dispositivo e ver as leituras com qualidade e atraso', async ({ page }) => {
    const code = `E2E-TANK-${Date.now()}`;
    await page.goto('/sensors');

    await page.getByLabel('Código').fill(code);
    await page.getByLabel('Nome').fill('Termômetro E2E');
    await page.getByLabel('Grandeza').selectOption('TEMPERATURE');
    await page.getByLabel('Unidade').selectOption('C');
    await page.getByLabel('Frequência esperada (segundos)').fill('300');

    const created = page.waitForResponse(
      r => r.url().includes('/api/v1/sensors/devices') && r.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Cadastrar' }).click();
    expect((await created).status()).toBe(201);

    // Uma leitura boa e uma fora da faixa, enviadas como um gateway enviaria.
    const measuredAt = new Date(Date.now() - 30_000).toISOString();
    const boa = await page.request.post('/api/v1/sensors/readings', {
      headers: await csrfHeaders(page),
      data: {
        deviceCode: code,
        messageId: 'e2e-boa',
        measure: 'TEMPERATURE',
        value: 18.5,
        unit: 'C',
        measuredAt,
      },
    });
    expect(boa.status()).toBe(201);
    expect((await boa.json()).reading.quality).toBe('GOOD');

    const ruim = await page.request.post('/api/v1/sensors/readings', {
      headers: await csrfHeaders(page),
      data: {
        deviceCode: code,
        messageId: 'e2e-ruim',
        measure: 'TEMPERATURE',
        value: 85,
        unit: 'C',
        measuredAt,
      },
    });
    // Fora da faixa é GRAVADA e sinalizada — recusar deixaria um buraco na curva.
    expect(ruim.status()).toBe(201);
    expect((await ruim.json()).reading.quality).toBe('OUT_OF_RANGE');

    // A tela mostra as duas, com a sinalizada marcada. O seletor é o badge da linha, não o texto solto:
    // "fora da faixa" aparece também no aviso do topo e no motivo da leitura, e um `getByText` amplo
    // passaria mesmo se a linha da tabela não tivesse renderizado.
    await page.reload();
    await page.getByRole('button', { name: new RegExp(code) }).click();
    await expect(page.locator('td .badge', { hasText: 'Fora da faixa' })).toBeVisible();
    await expect(page.getByRole('status')).toContainText('1 de 2 leituras estão sinalizadas');
    // A leitura boa continua na série, e o motivo só acompanha a ruim.
    await expect(page.locator('tbody tr')).toHaveCount(2);
    await expect(page.getByText('fora da faixa plausível [-10, 45]')).toBeVisible();
  });

  test('reenvio da mesma mensagem responde 200 e não cria segunda leitura', async ({ page }) => {
    const code = `E2E-DUP-${Date.now()}`;
    await page.goto('/sensors');

    await page.getByLabel('Código').fill(code);
    await page.getByLabel('Nome').fill('Densímetro E2E');
    await page.getByLabel('Grandeza').selectOption('DENSITY');
    await page.getByLabel('Unidade').selectOption('SG');
    const created = page.waitForResponse(
      r => r.url().includes('/api/v1/sensors/devices') && r.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Cadastrar' }).click();
    const deviceId = (await (await created).json()).id;

    const message = {
      deviceCode: code,
      messageId: 'e2e-reenvio',
      measure: 'DENSITY',
      value: 1.048,
      unit: 'SG',
      measuredAt: new Date(Date.now() - 10_000).toISOString(),
    };

    const primeira = await page.request.post('/api/v1/sensors/readings', {
      headers: await csrfHeaders(page),
      data: message,
    });
    expect(primeira.status()).toBe(201);
    expect((await primeira.json()).duplicate).toBe(false);

    // O gateway não recebeu o ACK e reenviou. Fez a coisa certa — a resposta diz "já está registrado".
    const segunda = await page.request.post('/api/v1/sensors/readings', {
      headers: await csrfHeaders(page),
      data: message,
    });
    expect(segunda.status()).toBe(200);
    const corpo = await segunda.json();
    expect(corpo.duplicate).toBe(true);
    expect(corpo.reading.id).toBe((await primeira.json()).reading.id);

    const readings = await page.request.get(`/api/v1/sensors/devices/${deviceId}/readings`);
    expect((await readings.json()).length).toBe(1);
  });

  test('dispositivo pausado recusa leitura com 409', async ({ page }) => {
    const code = `E2E-PAUSA-${Date.now()}`;
    await page.goto('/sensors');

    await page.getByLabel('Código').fill(code);
    await page.getByLabel('Nome').fill('Manômetro E2E');
    await page.getByLabel('Grandeza').selectOption('PRESSURE');
    await page.getByLabel('Unidade').selectOption('PSI');
    const created = page.waitForResponse(
      r => r.url().includes('/api/v1/sensors/devices') && r.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Cadastrar' }).click();
    await created;

    await page.getByRole('button', { name: new RegExp(code) }).click();
    const paused = page.waitForResponse(r => r.url().includes('/status'));
    await page.getByRole('button', { name: 'Pausar' }).click();
    expect((await paused).status()).toBe(200);
    await expect(page.getByText(/Pausado — leituras enviadas agora são recusadas/)).toBeVisible();

    const recusada = await page.request.post('/api/v1/sensors/readings', {
      headers: await csrfHeaders(page),
      data: {
        deviceCode: code,
        messageId: 'e2e-pausado',
        measure: 'PRESSURE',
        value: 12,
        unit: 'PSI',
        measuredAt: new Date(Date.now() - 10_000).toISOString(),
      },
    });
    expect(recusada.status()).toBe(409);
    expect((await recusada.json()).code).toBe('sensor_device_inactive');
  });
});
