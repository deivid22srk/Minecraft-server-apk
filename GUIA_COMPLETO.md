# Minecraft Bedrock Server APK - Guia Completo

<div align="center">
  <h3>🎮 Servidor Minecraft Bedrock 1.21.120.4 para Android</h3>
  <p>Execute um servidor Minecraft Bedrock no seu dispositivo Android</p>
</div>

## ⚠️ IMPORTANTE - Como Funciona

Este app cria um **servidor proxy** que permite que jogadores se conectem. Para um servidor completo:

### 🔧 Opções de Implementação

#### **Opção 1: Servidor Proxy (Atual)** ✅
- ✅ Fácil de usar
- ✅ Aceita conexões na rede local
- ⚠️ Limitado - é um proxy, não um mundo completo
- 📱 Bom para testes e LAN parties

#### **Opção 2: Servidor Real via Termux** (Recomendado)
```bash
# Instale Termux e execute:
pkg install wget phar php
wget https://github.com/pmmp/PocketMine-MP/releases/latest/download/PocketMine-MP.phar
php PocketMine-MP.phar
```

## 🌐 Como Conectar SEM Port Forwarding

### **Método 1: Playit.gg** (⭐ Recomendado)

O **Playit.gg** cria um túnel gratuito para o seu servidor!

1. **No PC/Outro Android:**
   ```bash
   # Baixe em: https://playit.gg/download
   # Após instalar:
   playit
   ```

2. **Configure o túnel:**
   - Tipo: **UDP**
   - Porta local: **19132**
   - O Playit vai gerar um endereço tipo: `123.playit.gg:54321`

3. **No Minecraft:**
   - Use o endereço fornecido pelo Playit
   - Porta: a fornecida pelo Playit

**Vantagens:**
- ✅ Gratuito
- ✅ Não precisa configurar roteador
- ✅ Funciona em qualquer rede
- ✅ Baixa latência

### **Método 2: Ngrok**

```bash
# Instale ngrok
ngrok tcp 19132

# Use o endereço fornecido (ex: 0.tcp.ngrok.io:12345)
```

**Desvantagens:**
- ⚠️ Versão gratuita tem limitações
- ⚠️ TCP pode ter mais lag que UDP

### **Método 3: Radmin VPN / Hamachi**

Cria uma rede privada virtual entre você e seus amigos.

1. Todos instalam Radmin VPN ou Hamachi
2. Criam/entram na mesma rede
3. Use o IP da rede virtual + porta 19132

**Vantagens:**
- ✅ Gratuito
- ✅ Fácil de usar
- ✅ Baixa latência

**Desvantagens:**
- ⚠️ Todos precisam instalar
- ⚠️ Limite de usuários gratuitos

### **Método 4: ZeroTier**

Similar ao Hamachi, mas mais moderno.

```bash
# Instale ZeroTier
# Crie uma rede em my.zerotier.com
# Conecte todos os dispositivos à mesma rede
```

## 📱 Como Usar o App

### 1. **Configurar Servidor**

- Abra o app
- Vá em **Configurações**
- Configure:
  - Nome do servidor
  - Porta (19132 padrão)
  - Keep Inventory: ✅
  - Show Coordinates: ✅
  - Ativar Servidor Público: ✅

### 2. **Iniciar Servidor**

- Tela inicial → **Iniciar Servidor**
- Anote o endereço local (ex: 192.168.1.100:19132)
- Se for usar túnel, configure conforme acima

### 3. **Conectar no Minecraft**

#### **Na mesma WiFi:**
```
Endereço: [IP_LOCAL_DO_ANDROID]
Porta: 19132
```

#### **WiFi diferente (com túnel):**
```
Endereço: [ENDERECO_DO_TUNEL]
Porta: [PORTA_DO_TUNEL]
```

### 4. **Console**

- Tela Console para ver logs
- Comandos disponíveis:
  - `list` - Ver jogadores online
  - `gamerule keepInventory true`
  - `stop` - Parar servidor

## 🔧 Troubleshooting

### ❌ "Não consigo conectar"

**Problema:** Servidor não responde

**Soluções:**
1. ✅ Verifique se o servidor está rodando (LED verde)
2. ✅ Use o IP correto (192.168.x.x na mesma WiFi)
3. ✅ Porta correta (padrão: 19132)
4. ✅ Desative VPN no celular/PC
5. ✅ Firewall/Antivírus pode estar bloqueando

### ❌ "Servidor desconecta"

**Problema:** Conecta mas desconecta rapidamente

**Causa:** O servidor atual é um proxy básico

**Solução:**
- Use PocketMine-MP via Termux (veja acima)
- Ou use servidor dedicado em PC

### ❌ "Preciso de servidor real"

**Opções:**

1. **Termux + PocketMine-MP** (no Android)
   - Servidor PHP real
   - Suporta plugins
   - Mundos completos

2. **PC com Bedrock Dedicated Server**
   - Servidor oficial
   - Melhor performance
   - Mais recursos

3. **Serviço na nuvem** (gratuito)
   - Aternos.org
   - Minehut.com
   - Server.pro

## 🎮 Conectar de Qualquer Lugar

### **Passo a Passo Completo:**

1. **No Android (Servidor):**
   - Inicie o app
   - Ligue o servidor
   - Anote o IP local

2. **No PC/Outro dispositivo (Túnel):**
   ```bash
   # Opção Playit.gg (Recomendado)
   playit
   # Configure túnel UDP → porta 19132
   # Anote o endereço: xyz.playit.gg:12345
   ```

3. **No Minecraft (Jogadores):**
   - Servers → Add Server
   - Nome: Qualquer
   - Endereço: `xyz.playit.gg`
   - Porta: `12345` (a do playit)
   - Conectar!

## 📊 Comparação de Métodos

| Método | Gratuito | Fácil | Port Forwarding | Latência |
|--------|----------|-------|-----------------|----------|
| **Playit.gg** | ✅ | ✅ | ❌ Não precisa | Baixa |
| **Ngrok** | ⚠️ Limitado | ✅ | ❌ Não precisa | Média |
| **Radmin VPN** | ✅ | ✅ | ❌ Não precisa | Baixa |
| **Port Forward** | ✅ | ❌ | ✅ Precisa | Muito Baixa |
| **ZeroTier** | ✅ | ⚠️ | ❌ Não precisa | Baixa |

## 🔗 Links Úteis

- **Playit.gg:** https://playit.gg
- **Ngrok:** https://ngrok.com
- **Radmin VPN:** https://www.radmin-vpn.com
- **ZeroTier:** https://www.zerotier.com
- **PocketMine-MP:** https://github.com/pmmp/PocketMine-MP
- **Termux:** https://f-droid.org/en/packages/com.termux

## 🤝 Contribuindo

PRs são bem-vindos! Para mudanças maiores, abra uma issue primeiro.

## 📧 Suporte

- GitHub Issues: [Abrir Issue](https://github.com/deivid22srk/Minecraft-server-apk/issues)
- Discord: Em breve

## ⚖️ Licença

MIT License - Veja [LICENSE](LICENSE)

---

<div align="center">
  <p>Feito com ❤️ para a comunidade Minecraft</p>
  <p><strong>⚠️ Respeite os Termos de Serviço da Mojang/Microsoft</strong></p>
</div>
