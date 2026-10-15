# Nexus AI — Android

O Nexus AI tem uma interface Android nativa em Kotlin + Jetpack Compose, com tema AMOLED escuro e destaque verde-lima, além da versão web mantida em `index.html`.

## Android

- Chat nativo com histórico persistido localmente em Room.
- API keys armazenadas com AndroidX Security Crypto.
- Respostas em streaming SSE para APIs compatíveis.
- Provedores configuráveis: Anthropic Claude, Groq, OpenAI, endpoints OpenAI-compatible e endpoints locais (por exemplo, Ollama/LM Studio).
- Modelos predefinidos e campos para adicionar modelos/endpoints próprios.
- Configuração opcional de um backend do Claude Code.
- GitHub Actions compila um APK de depuração a cada alteração relevante e publica o artefato `Nexus-AI-debug`.

### Baixar o APK

Abra **Actions → Nexus Android APK → execução mais recente → Artifacts → Nexus-AI-debug** no GitHub:
https://github.com/F1N0ZENIX/Nexus/actions/workflows/android-apk.yml

O artefato é um APK debug instalável, não uma versão de lançamento assinada para distribuição. O primeiro build pode levar alguns minutos.

## Configurar modelos

1. Instale o APK e abra Configurações.
2. Escolha o provedor, informe a chave API quando necessária, URL base e ID exato do modelo.
3. Salve e selecione o modelo no seletor do chat.

Os modelos e seus IDs disponíveis podem mudar por provedor e plano. O app não inclui chaves pagas. O uso das APIs pode gerar custos.

## Modelos locais

O app pode conectar-se a servidores compatíveis com OpenAI rodando na rede local. Para um celular físico, use o IP local do computador e certifique-se de que o servidor aceite conexões da rede. `10.0.2.2` só é o endereço usual do host no emulador Android.

**Limitação:** os pesos do modelo não estão embutidos no APK; inferência totalmente offline no próprio telefone ainda não está integrada. Claude também não é um modelo open-source que possa ser empacotado localmente.

Mais detalhes: [LOCAL_MODELS.md](LOCAL_MODELS.md).

## Claude Code

Claude Code é um agente/CLI executado em computador ou servidor, não um modelo Android embutido. Há um bridge Node.js opcional em [backend/](backend/README.md). Ele exige que Claude Code já esteja instalado e autenticado no computador, e que você configure um token. O modo padrão do bridge é `plan`, para reduzir ações de escrita. Não exponha o servidor à internet sem controles fortes de segurança.

## Build local

Requisitos: JDK 17 e Gradle 8.9.

```bash
gradle assembleDebug
```

APK gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Segurança e limitações

- As chaves são cifradas no armazenamento privado do Android; não compartilhe o dispositivo desbloqueado nem o APK com dados pessoais exportados.
- Os endpoints locais em HTTP devem ser usados apenas em uma rede confiável.
- A conexão a provedores externos requer internet.
- A compatibilidade depende do formato de streaming e do endpoint do provedor.
- O app foi preparado para build automatizado; o APK precisa ser testado em dispositivo real antes de considerar a versão estável.
