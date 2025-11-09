# Informações de Download do PHP

O app baixa automaticamente o PHP na primeira execução.

## Fontes de PHP para Android ARM:

### Opção 1: Build estático próprio
- Compilar PHP como binário estático
- Incluir todas as extensões necessárias
- Tamanho: ~12-15MB

### Opção 2: Termux PHP
- Usar binários do repositório Termux
- Vantagem: Sempre atualizado
- URL: https://packages.termux.dev/apt/termux-main/

### Opção 3: PHP Android Builds
- https://github.com/lz233/php-build
- Builds para ARM64 e ARMv7

## Implementação Atual:

O PhpManager tenta baixar de:
1. GitHub releases de builds PHP para Android
2. Fallback para Termux se disponível
3. Mensagem clara se falhar

## Arquivos incluídos:

- PocketMine-MP.phar (3.1MB) → /app/src/main/assets/
- PHP será baixado automaticamente na primeira execução
