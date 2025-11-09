# Minecraft Bedrock Server APK

<div align="center">
  <h3>🎮 Servidor Minecraft Bedrock 1.21.120.4 para Android</h3>
  <p>Execute um servidor Minecraft Bedrock completo no seu dispositivo Android com interface Material You</p>
</div>

## ✨ Funcionalidades

- 🚀 **Servidor Bedrock 1.21.120.4** completo rodando nativamente no Android
- 🌐 **Servidor Público** - Acessível de qualquer rede WiFi (não apenas localhost)
- 🎨 **Interface Material You** - Design moderno com Jetpack Compose e Material3
- ⚙️ **Configurações Completas**:
  - ✅ Ativar/desativar coordenadas no jogo
  - ✅ Keep Inventory (não perder itens ao morrer)
  - ✅ Modos de jogo (Survival, Creative, Adventure)
  - ✅ Níveis de dificuldade
  - ✅ PvP on/off
  - ✅ Configuração de porta e jogadores máximos
- 📥 **Importação do Aternos** - Traga seu mundo do Aternos para o servidor local
- 📱 **Console em Tempo Real** - Visualize logs e execute comandos
- 🔔 **Notificação Persistente** - Servidor roda em segundo plano
- 🎯 **Suporte ARM** - Otimizado para dispositivos ARM64 e ARMv7

## 📋 Requisitos

- Android 7.0 (API 24) ou superior
- Mínimo 2GB de RAM recomendado
- Conexão com internet para servidor público
- Permissões de notificação (Android 13+)

## 🔧 Tecnologias

- **Kotlin** - Linguagem de programação
- **Jetpack Compose** - UI moderna e declarativa
- **Material3** - Design System do Material You
- **Coroutines** - Programação assíncrona
- **PocketMine-MP** - Engine do servidor Minecraft Bedrock
- **Gradle** - Build system

## 📦 Instalação

### Download APK

Baixe o APK mais recente na seção [Releases](https://github.com/deivid22srk/Minecraft-server-apk/releases) ou nas [GitHub Actions](https://github.com/deivid22srk/Minecraft-server-apk/actions).

### Build Manual

```bash
git clone https://github.com/deivid22srk/Minecraft-server-apk.git
cd Minecraft-server-apk
chmod +x gradlew
./gradlew assembleDebug
```

O APK será gerado em: `app/build/outputs/apk/debug/app-debug.apk`

## 🎮 Como Usar

1. **Instale o APK** no seu dispositivo Android
2. **Abra o aplicativo** e conceda as permissões necessárias
3. **Configure o servidor** nas configurações:
   - Nome do servidor
   - Porta (padrão: 19132)
   - Número máximo de jogadores
   - Ativar servidor público
   - Keep Inventory e Show Coordinates
4. **Inicie o servidor** na tela principal
5. **Conecte-se** usando o endereço IP mostrado no app

### Servidor Público (Acesso de Qualquer WiFi)

Para permitir que jogadores se conectem de qualquer rede:

1. Ative "**Servidor Público**" nas configurações
2. Configure **Port Forwarding** no seu roteador:
   - Porta externa: 19132 (ou a porta que você configurou)
   - Porta interna: 19132
   - Protocolo: UDP
   - IP: O IP local do seu dispositivo Android
3. Use o **Endereço Público** mostrado no app para compartilhar com os jogadores

### Importar do Aternos

1. Vá em **Configurações** → **Importar do Aternos**
2. Cole a URL do seu servidor Aternos: `https://aternos.org/server/...`
3. Clique em **Importar**
4. Aguarde o download e reinicie o servidor

## 🏗️ Estrutura do Projeto

```
app/
├── src/main/
│   ├── java/com/minecraft/bedrockserver/
│   │   ├── data/
│   │   │   ├── ServerConfig.kt       # Configurações do servidor
│   │   │   └── ServerState.kt        # Estado do servidor
│   │   ├── server/
│   │   │   └── MinecraftServer.kt    # Lógica principal do servidor
│   │   ├── service/
│   │   │   └── MinecraftServerService.kt  # Serviço em background
│   │   ├── viewmodel/
│   │   │   └── ServerViewModel.kt    # ViewModel principal
│   │   ├── ui/
│   │   │   ├── screens/
│   │   │   │   ├── HomeScreen.kt     # Tela inicial
│   │   │   │   ├── SettingsScreen.kt # Configurações
│   │   │   │   └── ConsoleScreen.kt  # Console
│   │   │   └── theme/
│   │   │       └── Theme.kt          # Tema Material You
│   │   └── MainActivity.kt           # Activity principal
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## 🔄 CI/CD

O projeto usa GitHub Actions para build automático:

- ✅ Build automático em push para `main` e branches `capy/**`
- ✅ Build em Pull Requests
- ✅ Upload de APK debug e release como artifacts
- ✅ Suporte a workflow manual

## 🤝 Contribuindo

Contribuições são bem-vindas! Sinta-se à vontade para:

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/MinhaFeature`)
3. Commit suas mudanças (`git commit -m 'Adiciona MinhaFeature'`)
4. Push para a branch (`git push origin feature/MinhaFeature`)
5. Abra um Pull Request

## 📝 Licença

Este projeto é open source e está disponível sob a licença MIT.

## ⚠️ Aviso

Este é um projeto educacional. Certifique-se de ter permissão para executar servidores Minecraft e respeite os Termos de Serviço da Mojang/Microsoft.

## 📧 Contato

Para dúvidas ou sugestões, abra uma [Issue](https://github.com/deivid22srk/Minecraft-server-apk/issues).

---

<div align="center">
  <p>Desenvolvido com ❤️ usando Kotlin e Jetpack Compose</p>
</div>
