# Diagnóstico isolado: Bun Android arm64

Prova isolada para o port local do OpenCode. Não é importada pelo Nexus e não muda `app/`, `main` nem `revamp-nexus`; não há chave, servidor, release ou integração do OpenCode.

A CI baixa só o asset oficial [Bun 1.4.4 Android arm64](https://github.com/oven-sh/bun/releases/download/bun-v1.4.4/bun-linux-aarch64-android.zip), confere SHA-256 `d72e42c60e895ccbc16144bb1e0e0ad7ee384841c35b5501a7ec28b4d6d759c9`, extrai `bun` como `jniLibs/arm64-v8a/libbun.so` e gera somente APK debug. O binário não é commitado.

O teste instrumentado exige API 28+ e `arm64-v8a`, executa `bun --version` e JavaScript, limita cada processo a 20 segundos e registra ABI Android, versão, `process.platform` e `process.arch`. `jniLibs` é extraído para testar execução por `ProcessBuilder` a partir de `nativeLibraryDir`.

A CI tenta AVD ARM64 API 35. Runners hospedados x86 podem não oferecer aceleração KVM para imagem ARM64: falha/timeout é limitação salva no log, não confirmação. Só `BUN_ANDROID_SANDBOX_OK` no log instrumentado prova o sandbox. Bun pode informar `process.platform` como `linux` no Android; esse valor cru é preservado.

Não testa Termux, PC nem aparelho físico; não prova OpenCode completo; não publica release. Artefatos expiram em 7 dias.
