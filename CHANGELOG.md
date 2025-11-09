# Changelog

## [v1.4] - 2025-11-09

### 🔧 Correções

- **Corrigido erro Exit Code 126** ao iniciar servidor
  - **Problema**: Binários PHP e PocketMine-MP.phar não estavam incluídos nos assets
  - **Solução**: Implementado sistema de download automático na primeira execução
  
### ✨ Melhorias

- **Download Automático de Binários**
  - PHP 8.2.19 (~50MB) baixado automaticamente do repositório oficial PMMP
  - PocketMine-MP 5.11.2 (~8MB) baixado automaticamente
  - Progresso do download mostrado nos logs
  - Downloads acontecem apenas uma vez

- **Melhor Feedback ao Usuário**
  - Mensagens de progresso durante download e extração
  - Indicadores de porcentagem de download
  - Mensagens de erro mais descritivas

### 📝 Detalhes Técnicos

#### Problema Original
O exit code 126 indicava que o binário PHP não podia ser executado devido a:
- Arquivo não existente (principal causa)
- Falta de permissões de execução
- Incompatibilidade de arquitetura

#### Solução Implementada
1. **AssetExtractor.kt**:
   - Adicionado `downloadAndExtractPhpBinary()` para baixar PHP binaries do PMMP
   - Adicionado `downloadPocketMinePhar()` para baixar PocketMine-MP.phar
   - Implementado sistema de progresso de download
   - Melhor tratamento de erros com mensagens em português

2. **MinecraftServer.kt**:
   - Mensagens de feedback durante preparação inicial
   - Informação ao usuário sobre tempo de espera no primeiro uso
   - Logs mais detalhados para diagnóstico

#### URLs dos Binários
- PHP: `https://github.com/pmmp/PHP-Binaries/releases/download/php-8.2.19-pmmp/PHP-8.2.19-Linux-aarch64.tar.gz`
- PocketMine-MP: `https://github.com/pmmp/PocketMine-MP/releases/download/5.11.2/PocketMine-MP.phar`

### 📋 Requisitos Atualizados
- Conexão com internet **obrigatória na primeira execução**
- Aproximadamente 100MB de espaço livre para downloads
- Após download inicial, servidor pode funcionar offline

---

## Versões Anteriores

### [v1.3] - Initial Release
- Servidor Minecraft Bedrock 1.21.120.4
- Interface Material You
- Configurações completas
- Console em tempo real
- Suporte ARM64
