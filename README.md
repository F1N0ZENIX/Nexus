# Nexus AI

Aplicativo Android nativo de chat para servidores locais e APIs compatíveis com OpenAI.

## Recursos

- **Histórico local de conversas:** chats separados no banco privado do próprio celular.
- **Interface de chat mobile:** composição adaptada ao teclado, conversas legíveis e acesso rápido a histórico, nova conversa e provedor.
- **Local / OpenAI-compatible:** para Ollama, LM Studio ou outro servidor local compatível.
- **NVIDIA NIM:** usa a URL oficial `https://integrate.api.nvidia.com/v1` e o ID de modelo informado pela pessoa usuária.
- **Provedor personalizado:** endpoint e nome de modelo livres para APIs OpenAI-compatible.
- **Anthropic:** protocolo Messages com `x-api-key`, `anthropic-version` e streaming SSE.
- **Claude Code:** integração com o bridge local incluído em `backend/`, usando URL do bridge e token próprios.
- **Chaves protegidas:** guardadas apenas nas preferências criptografadas do Android.

## Privacidade e rede

O histórico fica no armazenamento privado do aplicativo e não há sincronização automática em nuvem.

Ao enviar uma mensagem, seu conteúdo é transmitido somente ao endpoint configurado para o provedor atual. O Nexus mostra isso antes de salvar a configuração. Endpoints com chave precisam usar HTTPS; redirecionamentos HTTP/HTTPS são bloqueados para a chave não seguir para outro destino. A configuração local pode usar HTTP sem chave em sua rede privada. O bridge Claude Code mantém a compatibilidade com HTTP em rede local, como nas versões anteriores; use-o apenas em uma rede confiável.

### Servidor local

- Emulador Android: `http://10.0.2.2:11434/v1`.
- Telefone físico: configure o IP local do computador, por exemplo `http://192.168.1.10:11434/v1`.
- O telefone e o computador devem estar na mesma rede, e o servidor precisa aceitar conexões externas.

> `10.0.2.2` é específico de emuladores; não funciona em telefone físico.

### NVIDIA NIM

Crie uma chave em [NVIDIA API Catalog](https://build.nvidia.com/settings), escolha um modelo no catálogo e informe o ID no Nexus. A integração usa Chat Completions compatível com OpenAI no endpoint oficial `https://integrate.api.nvidia.com/v1`.

### Endpoint personalizado

Informe a URL base OpenAI-compatible (por exemplo, terminada em `/v1`), o nome exato do modelo e, se necessário, sua própria chave API. Não inclua `/chat/completions`: o aplicativo completa esse caminho automaticamente.

## Histórico

Na tela de histórico você pode abrir conversas antigas, criar uma nova ou excluir uma conversa. Apagar dados do aplicativo ou desinstalá-lo pode remover o histórico local.

## Build local

Requisitos: JDK 17, Android SDK e Gradle 8.9.

```bash
gradle assembleDebug
```

O APK de debug fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Limitações

- O Nexus não inclui pesos de modelos nem executa inferência offline por conta própria.
- Cada provedor define disponibilidade, modelos, limites e custos.
- O APK deve ser testado em dispositivo real antes de ser considerado estável.
