# Infraestrutura de referência

`docker-compose.reference.yml` documenta as dependências locais esperadas. Ele é uma referência para a Sprint 00: fixe versões de imagens, health checks, volumes, rede e credenciais locais antes de usá-lo como ambiente oficial. O módulo de segurança é parte do backend e usa PostgreSQL.

Arquivos usam adapter de filesystem apenas no ambiente local e uma porta S3 em produção. MinIO Community não é baseline novo porque seu repositório foi arquivado em 2026. A escolha de serviço S3 compatível permanece externa ao domínio.

Produção deve usar segredos gerenciados, TLS, backup, monitoramento, recursos limitados e serviços adequados ao ambiente. Não publicar portas administrativas nem reutilizar credenciais locais.
