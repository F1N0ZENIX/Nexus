package com.f1n0.nexus.opencodediagnostic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class BunSandboxInstrumentedTest {
  private static final long TIMEOUT_SECONDS = 20;
  @Test public void bunRunsInsideAndroidAppSandbox() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue("diagnostic needs API 28+", Build.VERSION.SDK_INT >= 28);
    assertTrue("test must run on arm64-v8a Android", Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a"));
    File bun = new File(context.getApplicationInfo().nativeLibraryDir, "libbun.so");
    assertTrue("verified Bun must be extracted from jniLibs", bun.isFile());
    CommandResult version = run(bun, "--version");
    assertEquals("bun --version failed: " + version.output, 0, version.exitCode);
    assertTrue("empty Bun version", !version.output.trim().isEmpty());
    CommandResult javascript = run(bun, "-e", "console.log(JSON.stringify({bunPlatform:process.platform,bunArch:process.arch}))");
    assertEquals("Bun JavaScript failed: " + javascript.output, 0, javascript.exitCode);
    assertTrue("Bun must report arm64: " + javascript.output, javascript.output.contains("arm64"));
    assertTrue("Bun platform tag missing: " + javascript.output, javascript.output.contains("bunPlatform"));
    System.out.println("BUN_ANDROID_SANDBOX_OK androidAbi=arm64-v8a sdk=" + Build.VERSION.SDK_INT + " bunVersion=" + version.output.trim() + " bunRuntime=" + javascript.output.trim());
  }
  private static CommandResult run(File executable, String... args) throws Exception {
    String[] command = new String[args.length + 1]; command[0] = executable.getAbsolutePath();
    System.arraycopy(args, 0, command, 1, args.length);
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream stream = process.getInputStream()) { byte[] buffer = new byte[4096]; int count; while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count); }
    assertTrue("Bun timed out after " + TIMEOUT_SECONDS + " seconds", process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    return new CommandResult(process.exitValue(), bytes.toString(StandardCharsets.UTF_8.name()));
  }
  private static final class CommandResult { final int exitCode; final String output; CommandResult(int code, String text) { exitCode = code; output = text; } }
}
