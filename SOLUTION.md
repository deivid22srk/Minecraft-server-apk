# Solução para Exit Code 126

## 🔍 Diagnóstico do Problema

### Erro Original
```
[13:56:42] ✗ Processo PHP morreu imediatamente após start()
[13:56:42] ✗ Exit code: 126
```

### Causa Raiz
O **exit code 126** em sistemas Unix/Linux significa:
- "Command invoked cannot execute" (Comando invocado não pode ser executado)

No contexto do app, isso ocorreu porque:
1. **Binários PHP ausentes**: A pasta `assets/php/arm64-v8a/` estava vazia (apenas .gitkeep)
2. **PocketMine-MP.phar ausente**: A pasta `assets/pocketmine/` estava vazia (apenas .gitkeep)
3. O código tentava executar um arquivo que não existia

## ✅ Solução Implementada

### 1. Download Automático de Binários

Modificamos `AssetExtractor.kt` para baixar automaticamente os binários na primeira execução:

```kotlin
// URLs dos binários oficiais
private const val PHP_BINARY_URL = 
    "https://github.com/pmmp/PHP-Binaries/releases/download/php-8.2.19-pmmp/PHP-8.2.19-Linux-aarch64.tar.gz"
private const val POCKETMINE_PHAR_URL = 
    "https://github.com/pmmp/PocketMine-MP/releases/download/5.11.2/PocketMine-MP.phar"
```

### 2. Funções Adicionadas

#### `downloadAndExtractPhpBinary(baseDir: File)`
- Baixa o tar.gz do PHP (~50MB)
- Mostra progresso do download (10% increments)
- Extrai usando comando `tar`
- Aplica permissões executáveis aos binários e bibliotecas .so
- Tratamento de erros robusto

#### `downloadPocketMinePhar(pharFile: File)`
- Baixa o PocketMine-MP.phar (~8MB)
- Mostra progresso do download
- Salva diretamente sem necessidade de extração

#### `extractTarGz(tarGzFile: File, destDir: File)`
- Usa comando `tar` nativo do Android/Linux
- Extrai todos os arquivos mantendo estrutura de diretórios
- Verifica exit code e lê stderr em caso de erro

### 3. Melhorias na UX

#### MinecraftServer.kt
```kotlin
// Mensagens informativas durante primeira execução
addConsoleLog("Preparando servidor pela primeira vez...")
addConsoleLog("Isso pode levar alguns minutos para baixar os arquivos necessários")
```

### 4. Sistema de Versionamento

```kotlin
private const val EXTRACTION_VERSION = "1.4"  // Atualizado de 1.3
```

Quando a versão muda, o app:
1. Verifica se os binários já existem
2. Se não existirem, baixa automaticamente
3. Salva versão no SharedPreferences
4. Não baixa novamente em futuras execuções

## 📊 Fluxo de Execução

```
Primeira Execução:
├─ AssetExtractor.extractIfNeeded()
│  ├─ Verifica versão salva != 1.4
│  ├─ extract()
│  │  ├─ PHP não encontrado?
│  │  │  └─ downloadAndExtractPhpBinary()
│  │  │     ├─ Download PHP (50MB) com progresso
│  │  │     ├─ Extrai tar.gz
│  │  │     └─ Aplica permissões 755
│  │  ├─ PocketMine.phar não encontrado?
│  │  │  └─ downloadPocketMinePhar()
│  │  │     └─ Download .phar (8MB) com progresso
│  │  └─ Cria diretórios (worlds, plugins, etc)
│  └─ Salva versão "1.4"
└─ Servidor pronto para iniciar

Execuções Subsequentes:
├─ extractIfNeeded()
│  ├─ Versão salva == 1.4
│  └─ Binários existem
└─ Retorna diretório (sem downloads)
```

## 🔒 Permissões

### AndroidManifest.xml
O app já tem as permissões necessárias:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Permissões de Arquivo
Aplicadas automaticamente durante extração:
```kotlin
Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath)).waitFor()
file.setExecutable(true, false)
file.setReadable(true, false)
```

## 🧪 Como Testar

1. **Limpe os dados do app** (para simular primeira execução)
2. **Abra o app** com internet conectada
3. **Observe o console**:
   ```
   [HH:mm:ss] Verificando binários do servidor...
   [HH:mm:ss] Aguarde, baixando binário PHP (aproximadamente 50MB)...
   [HH:mm:ss] Download progress: 10%
   [HH:mm:ss] Download progress: 20%
   ...
   [HH:mm:ss] Download concluído! Extraindo PHP binary...
   [HH:mm:ss] Baixando PocketMine-MP (aproximadamente 8MB)...
   [HH:mm:ss] ✓ Binários extraídos com sucesso
   ```
4. **Inicie o servidor**
5. **Deve iniciar sem exit code 126**

## ⚠️ Requisitos Importantes

### Para Usuários
- **Internet obrigatória na primeira execução**
- ~100MB de espaço livre
- Pode levar 2-5 minutos dependendo da conexão
- Após download inicial, funciona offline

### Para Desenvolvedores
- Os assets não precisam mais ser populados manualmente
- Binários são baixados automaticamente
- CI/CD não precisa mais fazer download/bundling de binários
- APK final será ~2MB menor

## 📝 Arquivos Modificados

1. **AssetExtractor.kt**
   - Adicionados imports: `java.net.URL`, `java.util.zip.ZipInputStream`
   - Adicionadas constantes de URL
   - Adicionadas 3 novas funções
   - Versão atualizada para 1.4
   - Melhor tratamento de erros

2. **MinecraftServer.kt**
   - Mensagens de feedback melhoradas
   - Logs mais descritivos durante inicialização

3. **README.md**
   - Atualizado com informações sobre download automático
   - Requisitos atualizados

4. **CHANGELOG.md** (novo)
   - Documentação completa das mudanças

## 🎯 Resultado Esperado

### Antes (Exit Code 126)
```
[13:56:41] Executando: /data/user/0/.../php
[13:56:42] ✗ Processo PHP morreu imediatamente após start()
[13:56:42] ✗ Exit code: 126
```

### Depois (Sucesso)
```
[HH:mm:ss] Verificando binários do servidor...
[HH:mm:ss] ✓ Binários extraídos com sucesso
[HH:mm:ss] Iniciando Minecraft Bedrock Server v1.21.120.4...
[HH:mm:ss] ✓ Processo PHP iniciado
[HH:mm:ss] ✓ Aguardando output do PocketMine-MP...
[HH:mm:ss] ✓ Servidor iniciado com sucesso!
```

## 🔧 Troubleshooting

### Se ainda ocorrer Exit Code 126:
1. Verifique se tem internet
2. Verifique espaço disponível (>100MB)
3. Limpe dados do app e tente novamente
4. Verifique logs para erros de download
5. Confirme que dispositivo é ARM64

### Se download falhar:
- App mostrará mensagem de erro específica
- Pode tentar novamente limpando dados do app
- Verifique se URLs estão acessíveis

### Se servidor não iniciar após download:
- Verifique logs do console
- Confirme que arquivos foram extraídos corretamente:
  - `/data/user/0/.../files/bedrock_server/bin/php7/bin/php` deve existir
  - `/data/user/0/.../files/bedrock_server/pocketmine/PocketMine-MP.phar` deve existir
