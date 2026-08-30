package com.slyph.cloverbadges.storage;

import com.slyph.cloverbadges.CloverBadges;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AsyncYamlWriter<T> {
    private final CloverBadges plugin;
    private final String fileName;
    private final SnapshotWriter<T> writer;
    private final ExecutorService executor;
    private final AtomicReference<T> pending = new AtomicReference<>();
    private final AtomicBoolean workerScheduled = new AtomicBoolean();
    private volatile boolean closed;

    public AsyncYamlWriter(CloverBadges plugin, String threadName, String fileName, SnapshotWriter<T> writer) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.writer = writer;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    public void submit(T snapshot) {
        if (closed) {
            return;
        }
        pending.set(snapshot);
        scheduleWorker();
    }

    public void close(T finalSnapshot) {
        if (closed) {
            return;
        }
        closed = true;
        pending.set(finalSnapshot);
        scheduleWorker();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30L, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Не удалось завершить сохранение " + fileName + " за 30 секунд.");
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            plugin.getLogger().severe("Сохранение " + fileName + " было прервано.");
        }
    }

    private void scheduleWorker() {
        if (workerScheduled.compareAndSet(false, true)) {
            executor.execute(this::drain);
        }
    }

    private void drain() {
        while (true) {
            T snapshot = pending.getAndSet(null);
            if (snapshot != null) {
                try {
                    writer.write(snapshot);
                } catch (IOException | RuntimeException exception) {
                    plugin.getLogger().severe("Не удалось сохранить " + fileName + ": " + exception.getMessage());
                }
                continue;
            }

            workerScheduled.set(false);
            if (pending.get() == null || !workerScheduled.compareAndSet(false, true)) {
                return;
            }
        }
    }

    @FunctionalInterface
    public interface SnapshotWriter<T> {
        void write(T snapshot) throws IOException;
    }
}
