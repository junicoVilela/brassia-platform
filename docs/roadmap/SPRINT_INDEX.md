# Índice de sprints

## Base existente

- Sprint 00 — [Bootstrap, fundação e qualidade](../../sprints/sprint-00-foundation/README.md): Criar o repositório remoto, gerar os projetos, configurar o ambiente e entregar todos os quality gates verdes.
- Sprint 01 — [Segurança interna, cervejaria e acesso](../../sprints/sprint-01-security-brewery/README.md): Autenticar, autorizar e isolar operações sem provedor externo de identidade.
- Sprint 02 — [Catálogo, equipamentos e água](../../sprints/sprint-02-catalog-equipment-water/README.md): Cadastrar os dados técnicos usados pelas receitas.
- Sprint 03 — [Receitas e motor de cálculos](../../sprints/sprint-03-recipe-engine/README.md): Criar, calcular, versionar e publicar receitas.

O mantenedor informou que as Sprints 00–03 já foram desenvolvidas. A execução deste kit começa na Sprint 04 e deve validar a implementação real antes de adaptar contratos.

## Débito de frontend de segurança (inserções aditivas)

As capacidades de segurança da Sprint 01 foram entregues como "fatia 1" (só backend). As telas correspondentes ficaram sem dono no roadmap e são recuperadas em duas sprints aditivas, sem renumerar as demais:

- Sprint 01-B — [Segurança: autoatendimento (frontend)](../../sprints/sprint-01b-security-frontend/README.md): MFA no login, troca/recuperação de senha, minha conta (sessões/histórico) e gate de navegação por permissão. Executar **antes da Sprint 05**.
- Sprint 01-C — [Segurança: administração e governança (frontend)](../../sprints/sprint-01c-security-admin-frontend/README.md): memberships, acesso temporário, revisão/segregação, alertas, auditoria, contas de serviço/API keys e federação/SCIM. Executar **antes da Sprint 17**; SEC-F09/F10 podem migrar para a Sprint 15.
- Sprint 01-D — [Segurança: leituras e administração (backend)](../../sprints/sprint-01d-security-backend-gaps/README.md): endpoints de leitura/administração (SEC-B01–B06) que faltavam ao entregar o frontend de segurança, removendo os workarounds de cliente. O login SSO no browser (SEC-B07) fica na Sprint 15.

## Próxima execução

- Sprint 04 — [Dados de referência e interoperabilidade](../../sprints/sprint-04-reference-data-interoperability/README.md): Versionar estilos, ingredientes e água, adotar BeerJSON/BeerXML e ampliar calculadoras.
- Sprint 05 — [Planejamento e ordens](../../sprints/sprint-05-planning-orders/README.md): Transformar receita publicada em produção liberada.
- Sprint 06 — [Estoque, lotes e compras](../../sprints/sprint-06-inventory-purchasing/README.md): Rastrear insumo e reservar/consumir por lote.
- Sprint 07 — [Brassagem assistida](../../sprints/sprint-07-brew-day/README.md): Executar água, mostura, fervura e transferência com correções.
- Sprint 08 — [Limpeza, sanitização e manutenção](../../sprints/sprint-08-sanitation/README.md): Liberar equipamentos por procedimento validado.
- Sprint 09 — [Fermentação, adega e levedura](../../sprints/sprint-09-fermentation-yeast/README.md): Acompanhar curvas, estabilidade e reutilização de levedura.
- Sprint 10 — [Envase, CO₂, oxigênio e vida útil](../../sprints/sprint-10-packaging-gas/README.md): Envasar com rastreio e controle de frescor.
- Sprint 11 — [Qualidade, metrologia e sensorial](../../sprints/sprint-11-quality-metrology/README.md): Tornar medições e liberações confiáveis.
- Sprint 12 — [Rastreabilidade, segurança e recall](../../sprints/sprint-12-traceability-food-safety/README.md): Localizar e conter qualquer lote afetado.
- Sprint 13 — [Custos, relatórios e sustentabilidade](../../sprints/sprint-13-cost-reporting-utilities/README.md): Fechar lote e transformar histórico em indicadores.
- Sprint 14 — [Copiloto de IA e RAG](../../sprints/sprint-14-ai-rag/README.md): Responder e recomendar com fontes e guardrails.
- Sprint 15 — [Integrações, sensores e PWA offline](../../sprints/sprint-15-integrations-pwa/README.md): Operar em campo e receber dados externos com segurança. Os conectores de terceiro (INT-004, INT-005, INT-007) saíram para a Sprint 21.
- Sprint 16 — [Inteligência, experimentos e blend](../../sprints/sprint-16-advanced-intelligence/README.md): Aprender com histórico e testar melhorias controladas.
- Sprint 17 — [Hardening e primeira produção](../../sprints/sprint-17-hardening-release/README.md): Preparar operação real, restauração e suporte. **Encerrada em 2026-08-15 sem declarar o release pronto**: REL-001 (restauração medida) ficou fora de escopo e o ciclo em homologação da REL-005 segue aberto — os dois dependem de ambiente e de quem opera. Fechou também catorze débitos herdados das sprints 08 a 16.

## Edições pós-release

- Sprint 18 — [Comunidade e colaboração](../../sprints/sprint-18-community-collaboration/README.md): Compartilhar e evoluir receitas com privacidade, autoria e moderação.
- Sprint 19 — [Operação comercial e CRM](../../sprints/sprint-19-commercial-operations/README.md): Ligar produção, pedidos, clientes, preços e previsão de demanda.
- Sprint 20 — [Contêineres e distribuição](../../sprints/sprint-20-containers-distribution/README.md): Rastrear kegs, retornáveis, rotas, entregas e prova de entrega.
- Sprint 21 — [Conectores externos](../../sprints/sprint-21-external-connectors/README.md): Importar receitas de Brewfather e Brewer's Friend sob credencial do usuário, com sincronização e conflito visíveis. Bloqueada até haver credencial de teste dos provedores (INT-004, INT-005 e INT-007, vindas da Sprint 15 por DEC-INT-001).
