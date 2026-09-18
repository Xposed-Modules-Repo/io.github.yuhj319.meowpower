package io.github.yuhj319.meowpower;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * root 命令执行封装。
 *
 * <p>所有需要特权才能读写的节点（sysfs、persist 属性、secure settings）都走这里。
 * 必须在线程外调用，{@link #exec} 会阻塞。</p>
 */
public final class RootShell {

    private static final int TIMEOUT_SECONDS = 10;

    private static volatile Boolean sRootAvailable;

    private RootShell() {
    }

    public static final class Result {
        public final boolean ok;
        public final String output;
        public final String error;

        Result(boolean ok, String output, String error) {
            this.ok = ok;
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
        }

        public boolean isEmpty() {
            return output.trim().isEmpty();
        }
    }

    /** 检测 root 可用性，结果缓存。 */
    public static boolean isRootAvailable() {
        Boolean cached = sRootAvailable;
        if (cached != null) {
            return cached;
        }
        boolean ok = false;
        try {
            Result result = exec("id");
            ok = result.ok && result.output.contains("uid=0");
        } catch (Throwable ignored) {
            // su 不存在或被拒绝
        }
        sRootAvailable = ok;
        return ok;
    }

    /** 用户手动重试时清掉缓存。 */
    public static void resetRootCache() {
        sRootAvailable = null;
    }

    /** 以 root 身份执行一段 shell 脚本。 */
    public static Result exec(String script) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("su", "-c", script);
            builder.redirectErrorStream(false);
            process = builder.start();

            // 关闭 stdin，避免 su 等待输入而卡住
            try {
                process.getOutputStream().close();
            } catch (Throwable ignored) {
            }

            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread drainOut = drain(process.getInputStream(), out);
            Thread drainErr = drain(process.getErrorStream(), err);
            drainOut.start();
            drainErr.start();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy();
                return new Result(false, out.toString(), "执行超时，请确认已授予 root 权限");
            }
            drainOut.join(1500);
            drainErr.join(1500);
            return new Result(process.exitValue() == 0, out.toString(), err.toString());
        } catch (Throwable t) {
            if (process != null) {
                process.destroy();
            }
            String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            return new Result(false, "", message);
        }
    }

    private static Thread drain(InputStream in, StringBuilder sink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sink.append(line).append('\n');
                }
            } catch (Throwable ignored) {
                // 流关闭，忽略
            }
        });
        thread.setDaemon(true);
        return thread;
    }
}
