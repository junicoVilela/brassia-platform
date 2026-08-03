import { defineConfig, devices } from '@playwright/test';

/**
 * E2E roda contra a aplicação de verdade: `ng serve` (com proxy para a API) na
 * frente, backend + PostgreSQL atrás. O backend NÃO é iniciado aqui — ele
 * precisa de banco e migrations, então é pré-requisito do ambiente (compose
 * local ou job da CI). Ver README.md.
 */
const baseURL = process.env.E2E_BASE_URL || 'http://localhost:4200';

export default defineConfig({
  testDir: './tests',
  // Jornada de ponta a ponta é lenta por natureza; o default de 30s estoura no
  // primeiro carregamento da SPA em máquina fria.
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // Sem paralelismo: as jornadas compartilham o mesmo banco e o mesmo usuário de
  // bootstrap, então rodar junto criaria interferência entre testes.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL,
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run serve:app',
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    stdout: 'ignore',
    stderr: 'pipe',
  },
});
