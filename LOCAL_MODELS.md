# Modelos locais e Claude Code

## Modelos via endpoint local
O Nexus pode conversar com endpoints compatíveis com a API OpenAI, incluindo servidores como Ollama ou LM Studio. Configure a URL acessível pelo telefone e o ID exato do modelo instalado. O endereço `10.0.2.2` é reservado para o emulador Android; em um telefone físico, use o IP do computador na mesma rede e permita conexões na rede local.

Isso não inclui pesos de modelos dentro do APK. Para inferência 100% offline no próprio telefone ainda é necessário integrar um runtime nativo (por exemplo, llama.cpp/ExecuTorch) e baixar modelos compatíveis separadamente.

## Claude e outros provedores
Adicione a chave API do provedor nas configurações. O app usa o endpoint Anthropic Messages para Claude e endpoints OpenAI-compatible para provedores como Groq, OpenAI e servidores locais. Disponibilidade e IDs de modelos dependem da conta/provedor.

## Claude Code
Claude Code é uma ferramenta agentic que executa no terminal/servidor, não um modelo open-source embutido no Android. O app permite configurar a URL de um backend seu que aceite `POST /api/claude-code` com JSON `{"prompt":"...","model":"..." }` e retorne JSON com `result` ou `content`. O backend precisa autenticar a aplicação, limitar acesso aos arquivos/comandos e solicitar aprovação para ações sensíveis. Não exponha o endpoint publicamente sem autenticação.

## Segurança
As chaves são guardadas com AndroidX Security Crypto no armazenamento privado do app. A comunicação com APIs externas exige internet. O endpoint local HTTP só deve ser usado em rede confiável.
