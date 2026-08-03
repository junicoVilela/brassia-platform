/**
 * Proxy do `ng serve` durante os testes E2E. Igual ao `frontend/proxy.conf.json`,
 * mas com o alvo configurável: a CI sobe o backend numa porta própria e a máquina
 * local pode ter outro serviço ocupando a 8080.
 */
export default {
  '/api': {
    target: process.env.E2E_API_URL || 'http://localhost:8080',
    secure: false,
    changeOrigin: true,
  },
};
