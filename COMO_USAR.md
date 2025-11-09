# Como Instalar e Usar - Passo a Passo

## 📱 1. INSTALAÇÃO DO APP

1. Baixe o APK em: [GitHub Releases](https://github.com/deivid22srk/Minecraft-server-apk/releases)
2. Instale o APK no seu Android
3. Abra o app

## ⚙️ 2. CONFIGURAÇÃO INICIAL

### Opção A: Download Automático (Recomendado se funcionar)

1. Na tela inicial, clique em **"Baixar e Instalar PHP"**
2. Aguarde o download (15-20MB, pode levar 2-5 minutos)
3. Se der erro, use a Opção B

### Opção B: Usar Termux (100% Confiável) ⭐

1. **Instale o Termux:**
   - Baixe em: https://f-droid.org/packages/com.termux/
   - **NÃO use a versão do Google Play** (desatualizada)

2. **Abra o Termux e execute:**
   ```bash
   pkg update && pkg upgrade -y
   pkg install php -y
   ```

3. **Aguarde a instalação** (1-2 minutos)

4. **Volte no app Minecraft Server**
   - O app irá detectar o PHP do Termux automaticamente!
   - Clique em "Pular" se ainda estiver na tela de setup

## 🚀 3. INICIAR O SERVIDOR

1. Na tela principal, clique em **"Iniciar Servidor"**
2. Aguarde a mensagem: **"🎉 SERVIDOR PRONTO!"**
3. Anote o endereço IP mostrado

## 🎮 4. CONECTAR NO MINECRAFT

### Na Mesma WiFi (Fácil):

1. Abra **Minecraft Bedrock** no celular/PC/console
2. Vá em **Servidores** → **Adicionar Servidor**
3. Preencha:
   - **Nome:** Qualquer nome
   - **Endereço:** O IP mostrado no app (ex: 192.168.1.100)
   - **Porta:** 19132
4. Salve e conecte!

### WiFi Diferente (Precisa de Túnel):

#### Método 1: Playit.gg (Recomendado)

1. **No PC ou outro Android:**
   - Baixe Playit.gg em: https://playit.gg
   - Execute o programa
   - Clique em "Add Tunnel"
   - Escolha "Minecraft Bedrock"
   - Configure:
     - Tipo: UDP
     - Porta Local: 19132
   - Anote o endereço fornecido (ex: `xyz.playit.gg:12345`)

2. **No Minecraft:**
   - Endereço: `xyz.playit.gg`
   - Porta: `12345` (a porta que o Playit forneceu)

#### Método 2: Radmin VPN

1. **Todos os jogadores instalam:** https://www.radmin-vpn.com
2. **Crie uma rede** ou entre em uma existente
3. **Conecte** todos os jogadores na mesma rede
4. **Use o IP da rede virtual** + porta 19132

## 🔧 5. CONFIGURAÇÕES AVANÇADAS

### Ativar Coordenadas:

1. Vá em **Configurações**
2. Ative **"Mostrar Coordenadas"**
3. Salve e reinicie o servidor

### Keep Inventory (Não Perder Itens):

1. Vá em **Configurações**
2. Ative **"Manter Inventário"**
3. Salve e reinicie o servidor

### Comandos no Console:

Acesse a aba **Console** e digite:

```
list                           # Ver jogadores online
gamerule keepInventory true    # Não perder itens
gamerule showcoordinates true  # Mostrar coordenadas
gamemode creative @a           # Modo criativo para todos
difficulty hard                # Dificuldade difícil
stop                          # Parar servidor
```

## ❓ PROBLEMAS COMUNS

### ❌ "Falha no download do PHP"

**Solução:** Use o Termux (Opção B acima)
- Mais confiável
- Sempre atualizado
- Funciona 100%

### ❌ "Não consigo conectar"

**Verifique:**
- ✅ Servidor está rodando (status verde)
- ✅ IP correto (192.168.x.x para mesma WiFi)
- ✅ Porta 19132
- ✅ Firewall não está bloqueando

**Na mesma WiFi:**
- Use o IP Local mostrado no app

**WiFi diferente:**
- Precisa usar túnel (Playit.gg, Radmin VPN, etc)

### ❌ "Servidor desconecta rapidamente"

**Causas:**
- Pouca memória RAM no dispositivo
- Servidor não iniciou completamente
- Espere aparecer "🎉 SERVIDOR PRONTO!" antes de conectar

## 📊 REQUISITOS

- Android 7.0+ (API 24)
- 2GB RAM mínimo (4GB recomendado)
- 500MB espaço livre
- Arquitetura ARM64 ou ARMv7

## 🎯 RESUMO RÁPIDO

```
1. Instale APP
2. Instale Termux → pkg install php
3. Inicie servidor no APP
4. Conecte: [IP_LOCAL]:19132
```

Para acesso público: Use Playit.gg

---

**Dúvidas?** Abra uma [Issue no GitHub](https://github.com/deivid22srk/Minecraft-server-apk/issues)
