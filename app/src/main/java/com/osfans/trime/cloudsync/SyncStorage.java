// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

/** Operates only on the application's dedicated synchronization cache and backups. */
public final class SyncStorage {
    private SyncStorage() {}
    public static void pruneObjects(File directory, Set<String> keep) throws IOException {
        for (File file : children(directory)) {
            String name = file.getName();
            if (file.isFile() && ((name.matches("[0-9a-f]{64}") && !keep.contains(name))
                    || name.matches("s3-.*\\.tmp") || name.matches("capture-.*\\.txt"))) {
                Files.deleteIfExists(file.toPath());
            }
        }
    }
    /** Two most recent generations, with a combined ceiling of 128 MiB. */
    public static void pruneBackups(File directory) throws IOException {
        File[] generations = Arrays.stream(children(directory))
                .filter(f -> f.isDirectory() && f.getName().matches("[0-9]+"))
                .sorted(Comparator.comparing(File::getName).reversed()).toArray(File[]::new);
        long bytes = 0;
        int count = 0;
        for (File generation : generations) {
            long size = size(generation);
            if (count < 2 && bytes + size <= 128L * 1024 * 1024) {
                bytes += size;
                count++;
            } else {
                remove(generation);
            }
        }
    }
    /** Call only after native merge, coverage verification and successful publication. */
    public static void prunePeers(File directory, String ownId, String currentPeer) throws IOException {
        for (File file : children(directory)) {
            if (file.isDirectory() && file.getName().startsWith("cloud-")
                    && !file.getName().equals(ownId) && !file.getName().equals(currentPeer)) remove(file);
        }
    }
    private static File[] children(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null && directory.exists()) throw new IOException("Cannot inspect sync storage");
        return files == null ? new File[0] : files;
    }
    private static long size(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        for (File child : children(file)) total += size(child);
        return total;
    }
    private static void remove(File file) throws IOException {
        if (!Files.isSymbolicLink(file.toPath()) && file.isDirectory()) {
            for (File child : children(file)) remove(child);
        }
        Files.deleteIfExists(file.toPath());
    }
}
