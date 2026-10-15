# Nexus AI

Aplicativo Android nativo com interface minimalista, preto AMOLED puro e destaque vermelho suave.

## Recursos

- **Histórico local de conversas:** chats separados, guardados no banco privado do próprio celular.
- **Seletor de modelos:** pesquisa por nome, cartões de seleção e indicação do modelo ativo.
- **Modelos locais:** presets de Llama, Qwen, DeepSeek, Mistral, Gemma, Phi, CodeLlama, StarCoder e Granite.
- **Tema:** fundo `#000000`, texto branco e vermelho discreto.
- **Interface adaptada ao Android:** respeita as barras do sistema e mantém os diálogos roláveis.
- **Releases automáticas:** GitHub Actions compila o APK e publica a versão em Releases após alterações relevantes.

## Baixar

**[Abrir a versão mais recente em Releases](https://github.com/F1N0ZENIX/Nexus/releases/latest)**

O build também pode ser acompanhado em [GitHub Actions](https://github.com/F1N0ZENIX/Nexus/actions/workflows/android-apk.yml).

## Modelos locais

O Nexus conecta-se a um servidor compatível com OpenAI, como Ollama ou LM Studio. Os nomes no seletor são presets; o modelo precisa estar instalado e o servidor ligado.

- Emulador Android: `http://10.0.2.2:11434/v1`.
- Telefone físico: configure o IP local do computador nas Preferências, por exemplo `http://192.168.1.10:11434/v1`.
- O telefone e o computador devem estar na mesma rede, e o servidor precisa aceitar conexões da rede local.

**Importante:** os pesos dos modelos não vêm no APK. Esta versão ainda não executa inferência totalmente offline no próprio telefone; utiliza um runtime local acessível pela rede.

## Histórico e privacidade

As conversas ficam no banco privado do app e não são sincronizadas para a nuvem. Apagar os dados do aplicativo ou desinstalá-lo pode remover o histórico. Na tela de histórico, você pode abrir conversas antigas, criar novas ou excluir conversas.

## Instalação e atualização

1. Abra [Releases mais recentes](https://github.com/F1N0ZENIX/Nexus/releases/latest).
2. Baixe o APK e permita a instalação da fonte usada.
3. Para instalar por cima, a versão nova deve manter o mesmo ID de pacote e usar a mesma chave de assinatura.

**Atenção:** o workflow suporta assinatura persistente, mas ela só é ativada depois que você configurar os Secrets abaixo. Sem isso, o APK usa uma chave temporária do runner e pode não instalar por cima de versões anteriores.

Para habilitar atualizações por cima:

1. Gere uma chave de assinatura uma única vez em um computador com JDK:
   `keytool -genkeypair -v -keystore nexus-release.jks -alias nexus -keyalg RSA -keysize 2048 -validity 10000`
2. Converta o arquivo para Base64. No Linux: `base64 -w 0 nexus-release.jks`. No macOS, se `-w` não existir: `base64 nexus-release.jks | tr -d '\n'`.
3. No GitHub, abra **Settings → Secrets and variables → Actions → New repository secret** e crie:
   - `NEXUS_KEYSTORE_BASE64`: conteúdo Base64 do arquivo `nexus-release.jks`
   - `NEXUS_KEYSTORE_PASSWORD`: senha do keystore
   - `NEXUS_KEY_ALIAS`: `nexus`
   - `NEXUS_KEY_PASSWORD`: senha da chave
4. Guarde uma cópia segura do keystore e das senhas; não as publique no repositório.

Se a versão instalada no celular foi assinada por uma chave temporária diferente, pode ser necessária uma desinstalação única para migrar para a assinatura persistente. Depois disso, mantenha a mesma chave em todas as releases.

## Build local

Requisitos: JDK 17 e Gradle 8.9.

```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Limitações

- Modelos precisam estar instalados no runtime local; não vêm embutidos no APK.
- `10.0.2.2` é para emuladores, não para o Nothing Phone 1.
- O APK precisa ser compilado e testado em dispositivo real antes de considerar a versão estável.
