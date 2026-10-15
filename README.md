# Nexus AI

Uma interface de IA responsiva, em português, com visual minimalista escuro e destaque verde-lima. A versão atual do repositório é uma aplicação web estática; ela **ainda não é um APK Android nativo**.

## O que existe nesta versão

- Chat com histórico local e múltiplas conversas.
- Seleção de modelo e cadastro de provedores.
- Provedores predefinidos: Anthropic Claude, Groq e OpenAI.
- Possibilidade de adicionar endpoints compatíveis com a API OpenAI ou Anthropic.
- Configuração de chaves por provedor.
- Renderização de Markdown sanitizada, sugestões iniciais e interface adaptável a celular e desktop.
- Área de configuração para conectar um backend de Claude Code.

## Modelos predefinidos

A lista inclui modelos de Claude, GPT-OSS, Qwen, Kimi, DeepSeek, Gemma e GPT-4o mini. A disponibilidade depende do provedor, da conta, do acesso ao modelo e do ID de modelo vigente. Se um ID não estiver disponível na sua conta, cadastre o ID suportado pelo seu provedor em **Modelos e provedores**.

Os modelos exibidos não são executados localmente nesta versão. Para execução offline, é necessário desenvolver um runtime Android (por exemplo, LiteRT ou ONNX Runtime) e disponibilizar modelos compatíveis com o dispositivo.

## Configurar

1. Abra a aplicação pelo GitHub Pages ou sirva os arquivos em um servidor HTTPS.
2. Abra **Modelos e provedores**.
3. Configure a chave do provedor escolhido e confirme o endpoint.
4. Selecione o modelo no topo do chat e envie uma mensagem.

### Chaves API e segurança

Nesta versão, as chaves são armazenadas no armazenamento local do navegador (`localStorage`). Isso é adequado apenas para uso pessoal e testes: qualquer pessoa com acesso ao navegador ou a scripts executados na mesma origem pode potencialmente obtê-las. Não coloque chaves em um site público ou dispositivo compartilhado.

Para produção, mova as chamadas a provedores para um backend autenticado, guarde segredos no servidor e aplique limites de uso por usuário. Chamadas diretas do navegador também podem ser bloqueadas por CORS. Não desative proteções de segurança para contornar isso.

## Claude Code

Projeto oficial: https://github.com/anthropics/claude-code

Claude Code é um agente de programação que roda em um ambiente de terminal. Não é um modelo de pesos abertos nem uma biblioteca Android que possa ser incluída diretamente num HTML ou APK. A interface permite registrar a URL de um backend que você controla, mas **a integração só funcionará depois que esse backend for implementado e implantado**.

Um backend seguro deverá, no mínimo:

- executar Claude Code em ambiente isolado, com diretórios de trabalho explicitamente permitidos;
- autenticar chamadas e proteger credenciais;
- pedir aprovação antes de executar comandos ou gravar alterações;
- restringir ferramentas e operações de shell;
- impor limites de tempo, tamanho, uso e custos;
- nunca expor chaves ou permitir acesso anônimo ao agente.

Documentação oficial do SDK: https://docs.anthropic.com/pt/docs/claude-code/sdk

## Limitações conhecidas

- O histórico e as configurações ficam no navegador, não em Room Database.
- A interface não inclui ainda um runtime de modelos locais.
- Claude Code exige um backend separado; configurar a URL não cria esse servidor.
- O teste e as chamadas diretas de API dependem de CORS e das políticas do provedor.
- Esta versão não deve ser tratada como cliente seguro para distribuição pública com chaves pessoais.

## Licença

Consulte os termos e licenças dos serviços e modelos utilizados. Claude Code é um projeto separado mantido pela Anthropic: https://github.com/anthropics/claude-code
