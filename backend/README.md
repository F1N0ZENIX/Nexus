# Nexus · Claude Code Bridge

O APK Android não executa o CLI do Claude Code dentro do processo Android. Este serviço Node.js encaminha prompts para a instalação local do comando `claude`.

## Requisitos

- Node.js 18+
- Claude Code instalado e autenticado no computador/servidor
- Acesso de rede do Android ao computador, quando usar o telefone

## Iniciar

Linux/macOS:
```bash
export NEXUS_BACKEND_TOKEN='gere-um-token-longo-e-aleatorio'
export CLAUDE_CODE_WORKDIR="$HOME/projetos"
node backend/claude-code-server.mjs
```

Windows PowerShell:
```powershell
$env:NEXUS_BACKEND_TOKEN = "gere-um-token-longo-e-aleatorio"
$env:CLAUDE_CODE_WORKDIR = "$HOME\projetos"
node backend/claude-code-server.mjs
```

Por padrão, o servidor usa a porta 8787 e modo de permissões `plan` (mais restrito). A variável `CLAUDE_CODE_PERMISSION_MODE` pode ajustar o modo conforme a documentação do Claude Code; faça isso somente em ambiente confiável. O processo fica limitado ao diretório definido em `CLAUDE_CODE_WORKDIR`, mas as permissões reais também dependem da configuração do Claude Code e do sistema operacional.

No Nexus, selecione **Claude Code**, informe a URL base do computador (ex.: `http://192.168.1.20:8787`) e coloque o mesmo token no campo de chave API. O app envia `Authorization: Bearer ...`.

Não publique esta porta na internet. Use firewall, token forte e uma rede confiável. A rota `/health` é apenas um teste básico de disponibilidade.
