using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace NoOne.Plugins.Dotnet.FileManager
{
    public sealed class DotnetFileManagerPlugin
    {
        private const int DefaultChunkLength = 256 * 1024;

        public override bool Equals(object obj)
        {
            IDictionary<string, object> ctx = obj as IDictionary<string, object>;
            if (ctx == null)
            {
                return true;
            }

            Dictionary<string, object> result = new Dictionary<string, object>();
            try
            {
                string op = GetTrimString(ctx, "op");
                if (string.IsNullOrEmpty(op))
                {
                    Fail(result, "INVALID_ARG", "op is required");
                    ctx["result"] = result;
                    return true;
                }

                string cwd = NormalizeCwd(GetTrimString(ctx, "cwd"));
                result["cwd"] = cwd;

                if (op == "list")
                {
                    HandleList(ctx, result, cwd);
                }
                else if (op == "stat")
                {
                    HandleStat(ctx, result, cwd);
                }
                else if (op == "read-all")
                {
                    HandleReadAll(ctx, result, cwd);
                }
                else if (op == "read-chunk")
                {
                    HandleReadChunk(ctx, result, cwd);
                }
                else if (op == "write-all")
                {
                    HandleWriteAll(ctx, result, cwd);
                }
                else if (op == "write-chunk")
                {
                    HandleWriteChunk(ctx, result, cwd);
                }
                else if (op == "mkdir")
                {
                    HandleMkdir(ctx, result, cwd);
                }
                else if (op == "create-file")
                {
                    HandleCreateFile(ctx, result, cwd);
                }
                else if (op == "move")
                {
                    HandleMoveOrCopy(ctx, result, cwd, true);
                }
                else if (op == "copy")
                {
                    HandleMoveOrCopy(ctx, result, cwd, false);
                }
                else if (op == "rename")
                {
                    HandleRename(ctx, result, cwd);
                }
                else if (op == "touch")
                {
                    HandleTouch(ctx, result, cwd);
                }
                else if (op == "delete")
                {
                    HandleDelete(ctx, result, cwd);
                }
                else if (op == "zip")
                {
                    HandleZip(ctx, result, cwd);
                }
                else if (op == "unzip")
                {
                    HandleUnzip(ctx, result, cwd);
                }
                else
                {
                    Fail(result, "INVALID_ARG", "unsupported op: " + op);
                }
            }
            catch (Exception ex)
            {
                Fail(result, "IO_ERROR", "File manager failed: " + SafeMessage(ex));
            }

            ctx["result"] = result;
            return true;
        }

        public override int GetHashCode()
        {
            return 0;
        }

        // ── list ────────────────────────────────────────────────────────

        private static void HandleList(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            string dirPath = ResolvePath(GetTrimString(ctx, "path"), cwd);
            EnsureExists(dirPath);
            EnsureDirectory(dirPath);

            DirectoryInfo dir = new DirectoryInfo(dirPath);
            FileSystemInfo[] entries = dir.GetFileSystemInfos();
            Array.Sort(entries, CompareEntries);

            List<Dictionary<string, object>> list = new List<Dictionary<string, object>>();
            for (int i = 0; i < entries.Length; i++)
            {
                list.Add(ToEntry(entries[i]));
            }
            result["path"] = CanonicalPath(dirPath);
            result["entries"] = list;
        }

        private static int CompareEntries(FileSystemInfo a, FileSystemInfo b)
        {
            bool aIsDir = (a.Attributes & FileAttributes.Directory) != 0;
            bool bIsDir = (b.Attributes & FileAttributes.Directory) != 0;
            if (aIsDir && !bIsDir) return -1;
            if (!aIsDir && bIsDir) return 1;
            return string.Compare(a.Name, b.Name, StringComparison.OrdinalIgnoreCase);
        }

        // ── stat ────────────────────────────────────────────────────────

        private static void HandleStat(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            string target = ResolvePath(GetTrimString(ctx, "path"), cwd);
            EnsureExists(target);
            result["entry"] = ToEntry(GetFileSystemInfo(target));
        }

        // ── read-all ────────────────────────────────────────────────────

        private static void HandleReadAll(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            string filePath = ResolvePath(GetTrimString(ctx, "path"), cwd);
            EnsureFile(filePath);

            FileInfo fi = new FileInfo(filePath);
            long fileSize = fi.Length;
            long maxBytes = GetLong(ctx, "maxBytes", -1L);
            if (maxBytes > -1 && fileSize > maxBytes)
            {
                Fail(result, "FILE_TOO_LARGE", "file exceeds maxBytes");
                result["fileSize"] = fileSize;
                return;
            }
            if (fileSize > int.MaxValue)
            {
                Fail(result, "FILE_TOO_LARGE", "file is too large for read-all");
                result["fileSize"] = fileSize;
                return;
            }

            byte[] bytes = File.ReadAllBytes(filePath);
            result["bytes"] = bytes;
            result["fileSize"] = fileSize;
            result["path"] = CanonicalPath(filePath);
        }

        // ── read-chunk ──────────────────────────────────────────────────

        private static void HandleReadChunk(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            string filePath = ResolvePath(GetTrimString(ctx, "path"), cwd);
            EnsureFile(filePath);

            long offset = GetLong(ctx, "offset", 0L);
            int length = GetInt(ctx, "length", DefaultChunkLength);
            if (offset < 0)
            {
                Fail(result, "INVALID_ARG", "offset must be >= 0");
                return;
            }
            if (length <= 0)
            {
                Fail(result, "INVALID_ARG", "length must be > 0");
                return;
            }

            FileInfo fi = new FileInfo(filePath);
            long fileSize = fi.Length;
            if (offset > fileSize)
            {
                Fail(result, "OFFSET_OUT_OF_RANGE", "offset exceeds file size");
                result["fileSize"] = fileSize;
                return;
            }

            int toRead = (int)Math.Min((long)length, Math.Max(0L, fileSize - offset));
            byte[] bytes = new byte[toRead];
            int read = 0;
            FileStream fs = null;
            try
            {
                fs = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.Read);
                fs.Seek(offset, SeekOrigin.Begin);
                if (toRead > 0)
                {
                    read = fs.Read(bytes, 0, toRead);
                    if (read < 0) read = 0;
                    if (read < toRead)
                    {
                        byte[] smaller = new byte[read];
                        Array.Copy(bytes, 0, smaller, 0, read);
                        bytes = smaller;
                    }
                }
            }
            finally
            {
                if (fs != null) fs.Dispose();
            }

            long nextOffset = offset + read;
            result["bytes"] = bytes;
            result["offset"] = offset;
            result["nextOffset"] = nextOffset;
            result["eof"] = nextOffset >= fileSize;
            result["fileSize"] = fileSize;
            result["path"] = CanonicalPath(filePath);
        }

        // ── write-all ───────────────────────────────────────────────────

        private static void HandleWriteAll(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            string filePath = ResolvePath(GetTrimString(ctx, "path"), cwd);
            byte[] bytes = GetBytes(ctx, "bytes");
            if (bytes == null)
            {
                Fail(result, "INVALID_ARG", "bytes is required");
                return;
            }

            EnsureParent(filePath, GetBool(ctx, "createParent", false));
            File.WriteAllBytes(filePath, bytes);

            FileInfo fi = new FileInfo(filePath);
            result["path"] = CanonicalPath(filePath);
            result["fileSize"] = fi.Length;
            result["modifiedAt"] = IsoTime(fi.LastWriteTimeUtc);
        }

        // ── write-chunk ─────────────────────────────────────────────────

        private static void HandleWriteChunk(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            string filePath = ResolvePath(GetTrimString(ctx, "path"), cwd);
            byte[] bytes = GetBytes(ctx, "bytes");
            if (bytes == null)
            {
                Fail(result, "INVALID_ARG", "bytes is required");
                return;
            }

            long offset = GetLong(ctx, "offset", 0L);
            bool truncate = GetBool(ctx, "truncate", false);
            if (offset < 0)
            {
                Fail(result, "INVALID_ARG", "offset must be >= 0");
                return;
            }

            EnsureParent(filePath, GetBool(ctx, "createParent", false));

            FileStream fs = null;
            try
            {
                fs = new FileStream(filePath, FileMode.OpenOrCreate, FileAccess.Write, FileShare.None);
                if (truncate)
                {
                    fs.SetLength(0L);
                    if (offset != 0L)
                    {
                        Fail(result, "INVALID_ARG", "offset must be 0 when truncate=true");
                        return;
                    }
                }
                long currentLength = fs.Length;
                if (offset > currentLength)
                {
                    Fail(result, "OFFSET_OUT_OF_RANGE", "offset exceeds current file size");
                    result["fileSize"] = currentLength;
                    return;
                }
                fs.Seek(offset, SeekOrigin.Begin);
                fs.Write(bytes, 0, bytes.Length);
                fs.Flush();
                long fileSize = fs.Length;
                long nextOffset = offset + bytes.Length;
                result["written"] = bytes.Length;
                result["nextOffset"] = nextOffset;
                result["fileSize"] = fileSize;
                result["path"] = CanonicalPath(filePath);
                result["modifiedAt"] = IsoTime(new FileInfo(filePath).LastWriteTimeUtc);
            }
            finally
            {
                if (fs != null) fs.Dispose();
            }
        }

        // ── mkdir ───────────────────────────────────────────────────────

        private static void HandleMkdir(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            string dirPath = ResolvePath(GetTrimString(ctx, "path"), cwd);

            if (Directory.Exists(dirPath))
            {
                result["path"] = CanonicalPath(dirPath);
                result["created"] = false;
                return;
            }
            if (File.Exists(dirPath))
            {
                Fail(result, "ALREADY_EXISTS", "path exists and is not a directory");
                return;
            }

            Directory.CreateDirectory(dirPath);
            result["path"] = CanonicalPath(dirPath);
            result["created"] = true;
        }

        // ── create-file ─────────────────────────────────────────────────

        private static void HandleCreateFile(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            string filePath = ResolvePath(GetTrimString(ctx, "path"), cwd);
            bool overwrite = GetBool(ctx, "overwrite", false);
            EnsureParent(filePath, GetBool(ctx, "createParent", false));

            if (File.Exists(filePath) || Directory.Exists(filePath))
            {
                if (Directory.Exists(filePath))
                {
                    Fail(result, "ALREADY_EXISTS", "path exists and is a directory");
                    return;
                }
                if (overwrite)
                {
                    File.WriteAllBytes(filePath, new byte[0]);
                }
                FileInfo existing = new FileInfo(filePath);
                result["path"] = CanonicalPath(filePath);
                result["created"] = false;
                result["fileSize"] = existing.Length;
                return;
            }

            FileStream fs = null;
            try
            {
                fs = new FileStream(filePath, FileMode.CreateNew, FileAccess.Write);
            }
            finally
            {
                if (fs != null) fs.Dispose();
            }
            result["path"] = CanonicalPath(filePath);
            result["created"] = true;
            result["fileSize"] = 0L;
        }

        // ── move / copy ─────────────────────────────────────────────────

        private static void HandleMoveOrCopy(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd, bool move)
        {
            List<string> sources = ParseSourcePaths(ctx, "sourcePaths", "sourcePath", cwd);
            if (sources.Count == 0)
            {
                Fail(result, "INVALID_ARG", "sourcePaths is required");
                return;
            }
            string destination = ResolvePath(GetTrimString(ctx, "destinationPath"), cwd);
            bool overwrite = GetBool(ctx, "overwrite", false);

            bool destinationExists = File.Exists(destination) || Directory.Exists(destination);
            bool destinationIsDir = destinationExists && Directory.Exists(destination);
            if (sources.Count > 1 && !destinationIsDir)
            {
                Fail(result, "INVALID_ARG", "destinationPath must be an existing directory for multiple sources");
                return;
            }

            List<Dictionary<string, string>> mappings = new List<Dictionary<string, string>>();
            for (int i = 0; i < sources.Count; i++)
            {
                string source = sources[i];
                EnsureExists(source);

                string target;
                if (destinationIsDir)
                {
                    target = Path.Combine(destination, Path.GetFileName(source));
                }
                else if (!destinationExists && sources.Count == 1)
                {
                    target = destination;
                }
                else
                {
                    target = Path.Combine(destination, Path.GetFileName(source));
                }

                target = CanonicalPath(target);
                source = CanonicalPath(source);

                if (IsSamePath(source, target))
                {
                    continue;
                }
                if (Directory.Exists(source) && IsDescendantPath(target, source))
                {
                    Fail(result, "INVALID_ARG", "cannot move/copy directory into itself");
                    return;
                }

                if (File.Exists(target) || Directory.Exists(target))
                {
                    if (!overwrite)
                    {
                        Fail(result, "ALREADY_EXISTS", "target already exists: " + target);
                        return;
                    }
                    DeleteRecursively(target);
                }
                else
                {
                    EnsureParent(target, true);
                }

                if (move)
                {
                    MovePath(source, target);
                }
                else
                {
                    CopyPath(source, target);
                }

                Dictionary<string, string> mapping = new Dictionary<string, string>();
                mapping["from"] = source;
                mapping["to"] = target;
                mappings.Add(mapping);
            }

            result["mappings"] = mappings;
        }

        // ── rename ──────────────────────────────────────────────────────

        private static void HandleRename(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            string sourcePath = ResolvePath(GetTrimString(ctx, "path"), cwd);
            EnsureExists(sourcePath);

            string newName = GetTrimString(ctx, "newName");
            if (string.IsNullOrEmpty(newName) || newName.IndexOf('/') >= 0 || newName.IndexOf('\\') >= 0)
            {
                Fail(result, "INVALID_ARG", "invalid newName");
                return;
            }

            string parent = Path.GetDirectoryName(sourcePath);
            if (string.IsNullOrEmpty(parent))
            {
                Fail(result, "INVALID_ARG", "cannot rename root path");
                return;
            }

            string target = CanonicalPath(Path.Combine(parent, newName));
            if ((File.Exists(target) || Directory.Exists(target)) && !IsSamePath(sourcePath, target))
            {
                Fail(result, "ALREADY_EXISTS", "target already exists");
                return;
            }

            string canonicalSource = CanonicalPath(sourcePath);
            MovePath(canonicalSource, target);
            result["from"] = canonicalSource;
            result["to"] = target;
        }

        // ── touch ───────────────────────────────────────────────────────

        private static void HandleTouch(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            List<string> paths = ParseSourcePaths(ctx, "paths", "path", cwd);
            if (paths.Count == 0)
            {
                Fail(result, "INVALID_ARG", "path or paths is required");
                return;
            }

            DateTime modifiedAt = ParseModifiedAt(ctx);
            List<string> updatedPaths = new List<string>();

            for (int i = 0; i < paths.Count; i++)
            {
                string p = paths[i];
                EnsureExists(p);
                if (Directory.Exists(p))
                {
                    Directory.SetLastWriteTimeUtc(p, modifiedAt);
                }
                else
                {
                    File.SetLastWriteTimeUtc(p, modifiedAt);
                }
                updatedPaths.Add(CanonicalPath(p));
            }

            result["updatedPaths"] = updatedPaths;
            result["modifiedAt"] = IsoTime(modifiedAt);
        }

        // ── delete ──────────────────────────────────────────────────────

        private static void HandleDelete(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            List<string> paths = ParseSourcePaths(ctx, "paths", "path", cwd);
            if (paths.Count == 0)
            {
                Fail(result, "INVALID_ARG", "path or paths is required");
                return;
            }
            bool recursive = GetBool(ctx, "recursive", false);

            List<string> deleted = new List<string>();
            for (int i = 0; i < paths.Count; i++)
            {
                string p = paths[i];
                EnsureExists(p);
                if (Directory.Exists(p) && !recursive)
                {
                    Fail(result, "INVALID_ARG", "directory delete requires recursive=true: " + p);
                    return;
                }
                DeleteRecursively(p);
                deleted.Add(CanonicalPath(p));
            }

            result["deletedPaths"] = deleted;
        }

        // ── zip ─────────────────────────────────────────────────────────

        private static void HandleZip(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            List<string> rawSources = ParseSourcePaths(ctx, "sourcePaths", "sourcePath", cwd);
            if (rawSources.Count == 0)
            {
                Fail(result, "INVALID_ARG", "sourcePath or sourcePaths is required");
                return;
            }

            string destinationText = GetTrimString(ctx, "destinationPath");
            if (string.IsNullOrEmpty(destinationText))
            {
                Fail(result, "INVALID_ARG", "destinationPath is required");
                return;
            }

            bool overwrite = GetBool(ctx, "overwrite", false);
            bool createParent = GetBool(ctx, "createParent", true);
            string destinationPath = CanonicalPath(ResolvePath(destinationText, cwd));

            List<string> sources = new List<string>();
            List<string> sourcePaths = new List<string>();
            for (int i = 0; i < rawSources.Count; i++)
            {
                string source = CanonicalPath(rawSources[i]);
                if (!File.Exists(source) && !Directory.Exists(source))
                {
                    Fail(result, "NOT_FOUND", "source path does not exist: " + source);
                    return;
                }
                if (IsSamePath(source, destinationPath))
                {
                    Fail(result, "INVALID_ARG", "destinationPath cannot be the same as source path");
                    return;
                }
                if (Directory.Exists(source) && IsDescendantPath(destinationPath, source))
                {
                    Fail(result, "INVALID_ARG", "destinationPath cannot be inside source directory");
                    return;
                }
                sources.Add(source);
                sourcePaths.Add(source);
            }

            if (File.Exists(destinationPath))
            {
                if (!overwrite)
                {
                    Fail(result, "ALREADY_EXISTS", "destinationPath already exists");
                    return;
                }
                File.Delete(destinationPath);
            }
            if (Directory.Exists(destinationPath))
            {
                Fail(result, "ALREADY_EXISTS", "destinationPath exists and is a directory");
                return;
            }

            EnsureParent(destinationPath, createParent);

            FileStream zipFs = null;
            ZipArchive zipArchive = null;
            int entryCount = 0;
            try
            {
                zipFs = new FileStream(destinationPath, FileMode.Create, FileAccess.Write);
                zipArchive = new ZipArchive(zipFs, ZipArchiveMode.Create, true);

                for (int i = 0; i < sources.Count; i++)
                {
                    string source = sources[i];
                    string baseName = Path.GetFileName(source);
                    if (string.IsNullOrEmpty(baseName)) baseName = "root";

                    if (Directory.Exists(source))
                    {
                        entryCount += WriteZipDirectory(zipArchive, source, baseName + "/");
                    }
                    else
                    {
                        ZipArchiveEntry entry = zipArchive.CreateEntry(baseName, CompressionLevel.Optimal);
                        WriteZipFileEntry(entry, source);
                        entryCount++;
                    }
                }
            }
            finally
            {
                if (zipArchive != null) zipArchive.Dispose();
                if (zipFs != null) zipFs.Dispose();
            }

            FileInfo destInfo = new FileInfo(destinationPath);
            result["archivePath"] = CanonicalPath(destinationPath);
            result["archiveSize"] = destInfo.Length;
            result["entryCount"] = entryCount;
            result["sourcePaths"] = sourcePaths;
        }

        private static int WriteZipDirectory(ZipArchive archive, string dirPath, string entryPrefix)
        {
            int count = 0;
            archive.CreateEntry(entryPrefix);
            count++;

            DirectoryInfo dir = new DirectoryInfo(dirPath);
            FileSystemInfo[] children = dir.GetFileSystemInfos();
            Array.Sort(children, CompareEntries);
            for (int i = 0; i < children.Length; i++)
            {
                FileSystemInfo child = children[i];
                if ((child.Attributes & FileAttributes.Directory) != 0)
                {
                    count += WriteZipDirectory(archive, child.FullName, entryPrefix + child.Name + "/");
                }
                else
                {
                    ZipArchiveEntry entry = archive.CreateEntry(entryPrefix + child.Name, CompressionLevel.Optimal);
                    WriteZipFileEntry(entry, child.FullName);
                    count++;
                }
            }
            return count;
        }

        private static void WriteZipFileEntry(ZipArchiveEntry entry, string filePath)
        {
            Stream entryStream = null;
            FileStream fileStream = null;
            try
            {
                entryStream = entry.Open();
                fileStream = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.Read);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fileStream.Read(buffer, 0, buffer.Length)) > 0)
                {
                    entryStream.Write(buffer, 0, len);
                }
            }
            finally
            {
                if (fileStream != null) fileStream.Dispose();
                if (entryStream != null) entryStream.Dispose();
            }
        }

        // ── unzip ───────────────────────────────────────────────────────

        private static void HandleUnzip(IDictionary<string, object> ctx, Dictionary<string, object> result, string cwd)
        {
            string archivePath = ResolvePath(GetTrimString(ctx, "path"), cwd);
            archivePath = CanonicalPath(archivePath);
            if (!File.Exists(archivePath))
            {
                Fail(result, "NOT_FOUND", "path does not exist: " + archivePath);
                return;
            }

            string destinationText = GetTrimString(ctx, "destinationPath");
            if (string.IsNullOrEmpty(destinationText))
            {
                Fail(result, "INVALID_ARG", "destinationPath is required");
                return;
            }

            bool overwrite = GetBool(ctx, "overwrite", false);
            bool createParent = GetBool(ctx, "createParent", true);
            string destinationPath = CanonicalPath(ResolvePath(destinationText, cwd));

            if (File.Exists(destinationPath))
            {
                Fail(result, "INVALID_ARG", "destinationPath exists and is not a directory");
                return;
            }
            if (!Directory.Exists(destinationPath))
            {
                EnsureParent(destinationPath, createParent);
                Directory.CreateDirectory(destinationPath);
            }

            int fileCount = 0;
            int dirCount = 0;
            long writtenBytes = 0L;

            FileStream zipFs = null;
            ZipArchive zipArchive = null;
            try
            {
                zipFs = new FileStream(archivePath, FileMode.Open, FileAccess.Read, FileShare.Read);
                zipArchive = new ZipArchive(zipFs, ZipArchiveMode.Read);

                for (int i = 0; i < zipArchive.Entries.Count; i++)
                {
                    ZipArchiveEntry entry = zipArchive.Entries[i];
                    string entryName = SanitizeZipEntryName(entry.FullName);
                    if (string.IsNullOrEmpty(entryName))
                    {
                        continue;
                    }

                    string targetPath = CanonicalPath(Path.Combine(destinationPath, entryName));
                    if (!targetPath.StartsWith(destinationPath, StringComparison.OrdinalIgnoreCase))
                    {
                        throw new InvalidOperationException("zip entry resolves outside destination: " + entry.FullName);
                    }

                    bool isDirectory = entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\");
                    if (isDirectory)
                    {
                        if (!Directory.Exists(targetPath))
                        {
                            Directory.CreateDirectory(targetPath);
                        }
                        dirCount++;
                        continue;
                    }

                    string parentDir = Path.GetDirectoryName(targetPath);
                    if (!string.IsNullOrEmpty(parentDir) && !Directory.Exists(parentDir))
                    {
                        Directory.CreateDirectory(parentDir);
                    }

                    if (File.Exists(targetPath))
                    {
                        if (!overwrite)
                        {
                            throw new ArgumentException("target already exists: " + targetPath);
                        }
                        File.Delete(targetPath);
                    }

                    Stream entryStream = null;
                    FileStream outFs = null;
                    try
                    {
                        entryStream = entry.Open();
                        outFs = new FileStream(targetPath, FileMode.Create, FileAccess.Write);
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = entryStream.Read(buffer, 0, buffer.Length)) > 0)
                        {
                            outFs.Write(buffer, 0, len);
                            writtenBytes += len;
                        }
                    }
                    finally
                    {
                        if (outFs != null) outFs.Dispose();
                        if (entryStream != null) entryStream.Dispose();
                    }

                    if (entry.LastWriteTime.Year > 1)
                    {
                        File.SetLastWriteTimeUtc(targetPath, entry.LastWriteTime.UtcDateTime);
                    }
                    fileCount++;
                }
            }
            finally
            {
                if (zipArchive != null) zipArchive.Dispose();
                if (zipFs != null) zipFs.Dispose();
            }

            result["archivePath"] = CanonicalPath(archivePath);
            result["destinationPath"] = CanonicalPath(destinationPath);
            result["fileCount"] = fileCount;
            result["dirCount"] = dirCount;
            result["writtenBytes"] = writtenBytes;
        }

        private static string SanitizeZipEntryName(string rawName)
        {
            if (string.IsNullOrEmpty(rawName))
            {
                return "";
            }
            string normalized = rawName.Replace('\\', '/');
            while (normalized.StartsWith("/"))
            {
                normalized = normalized.Substring(1);
            }
            if (normalized.Contains(".."))
            {
                throw new InvalidOperationException("zip entry cannot escape destination: " + rawName);
            }
            if (normalized.Contains(":"))
            {
                throw new InvalidOperationException("zip entry cannot contain drive letter: " + rawName);
            }
            return normalized;
        }

        // ── file system helpers ─────────────────────────────────────────

        private static void MovePath(string source, string target)
        {
            if (Directory.Exists(source))
            {
                Directory.Move(source, target);
            }
            else
            {
                File.Move(source, target);
            }
        }

        private static void CopyPath(string source, string target)
        {
            if (Directory.Exists(source))
            {
                CopyDirectory(source, target);
            }
            else
            {
                File.Copy(source, target, true);
            }
        }

        private static void CopyDirectory(string sourceDir, string targetDir)
        {
            Directory.CreateDirectory(targetDir);
            DirectoryInfo dir = new DirectoryInfo(sourceDir);
            FileInfo[] files = dir.GetFiles();
            for (int i = 0; i < files.Length; i++)
            {
                files[i].CopyTo(Path.Combine(targetDir, files[i].Name), true);
            }
            DirectoryInfo[] subDirs = dir.GetDirectories();
            for (int i = 0; i < subDirs.Length; i++)
            {
                CopyDirectory(subDirs[i].FullName, Path.Combine(targetDir, subDirs[i].Name));
            }
        }

        private static void DeleteRecursively(string path)
        {
            if (Directory.Exists(path))
            {
                Directory.Delete(path, true);
            }
            else if (File.Exists(path))
            {
                File.Delete(path);
            }
        }

        private static Dictionary<string, object> ToEntry(FileSystemInfo info)
        {
            Dictionary<string, object> entry = new Dictionary<string, object>();
            bool isDir = (info.Attributes & FileAttributes.Directory) != 0;
            string fullPath = CanonicalPath(info.FullName);
            string parentPath = Path.GetDirectoryName(info.FullName);

            entry["name"] = string.IsNullOrEmpty(info.Name) ? fullPath : info.Name;
            entry["path"] = fullPath;
            entry["parentPath"] = string.IsNullOrEmpty(parentPath) ? null : CanonicalPath(parentPath);
            entry["entryType"] = isDir ? "directory" : "file";
            entry["sizeBytes"] = isDir ? 0L : ((FileInfo)info).Length;
            entry["createdAt"] = IsoTime(info.CreationTimeUtc);
            entry["modifiedAt"] = IsoTime(info.LastWriteTimeUtc);
            entry["permissions"] = GetPermissions(info);
            entry["fileType"] = InferFileType(info);
            return entry;
        }

        private static string InferFileType(FileSystemInfo info)
        {
            if ((info.Attributes & FileAttributes.Directory) != 0)
            {
                return "Folder";
            }
            string name = info.Name.ToLowerInvariant();
            int dot = name.LastIndexOf('.');
            string ext = (dot > 0 && dot < name.Length - 1) ? name.Substring(dot + 1) : "";

            if (Contains(ext, "md", "txt", "pdf", "doc", "docx")) return "Document";
            if (Contains(ext, "ts", "tsx", "js", "jsx", "java", "go", "py", "rs", "json", "yaml", "yml")) return "Code";
            if (Contains(ext, "png", "jpg", "jpeg", "gif", "svg", "webp")) return "Image";
            if (Contains(ext, "zip", "tar", "gz", "rar", "7z")) return "Archive";
            if (Contains(ext, "mp3", "wav", "flac")) return "Audio";
            if (Contains(ext, "mp4", "avi", "mov", "mkv")) return "Video";
            return "File";
        }

        private static bool Contains(string value, params string[] candidates)
        {
            for (int i = 0; i < candidates.Length; i++)
            {
                if (candidates[i] == value) return true;
            }
            return false;
        }

        private static string GetPermissions(FileSystemInfo info)
        {
            bool isDir = (info.Attributes & FileAttributes.Directory) != 0;
            bool readOnly = (info.Attributes & FileAttributes.ReadOnly) != 0;
            char type = isDir ? 'd' : '-';
            char r = 'r';
            char w = readOnly ? '-' : 'w';
            char x = isDir ? 'x' : '-';
            string triad = "" + r + w + x;
            return type + triad + triad + triad;
        }

        // ── path helpers ────────────────────────────────────────────────

        private static string NormalizeCwd(string rawCwd)
        {
            if (string.IsNullOrEmpty(rawCwd))
            {
                return Directory.GetCurrentDirectory();
            }
            rawCwd = ExpandHome(rawCwd);
            if (!Path.IsPathRooted(rawCwd))
            {
                rawCwd = Path.Combine(Directory.GetCurrentDirectory(), rawCwd);
            }
            return CanonicalPath(rawCwd);
        }

        private static string ResolvePath(string rawPath, string cwd)
        {
            if (string.IsNullOrEmpty(rawPath))
            {
                throw new ArgumentException("path is required");
            }
            rawPath = ExpandHome(rawPath);
            if (Path.IsPathRooted(rawPath))
            {
                return CanonicalPath(rawPath);
            }
            return CanonicalPath(Path.Combine(cwd, rawPath));
        }

        private static string ExpandHome(string path)
        {
            if (string.IsNullOrEmpty(path)) return path;
            if (path == "~") return GetUserHome();
            if (path.StartsWith("~/") || path.StartsWith("~\\"))
            {
                return Path.Combine(GetUserHome(), path.Substring(2));
            }
            return path;
        }

        private static string GetUserHome()
        {
            string path = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            if (!string.IsNullOrEmpty(path)) return path;
            string home = Environment.GetEnvironmentVariable("HOME");
            if (!string.IsNullOrEmpty(home)) return home;
            string userProfile = Environment.GetEnvironmentVariable("USERPROFILE");
            return string.IsNullOrEmpty(userProfile) ? Directory.GetCurrentDirectory() : userProfile;
        }

        private static string CanonicalPath(string path)
        {
            try
            {
                return Path.GetFullPath(path);
            }
            catch
            {
                return path;
            }
        }

        private static FileSystemInfo GetFileSystemInfo(string path)
        {
            if (Directory.Exists(path))
            {
                return new DirectoryInfo(path);
            }
            return new FileInfo(path);
        }

        private static bool IsSamePath(string a, string b)
        {
            return string.Equals(CanonicalPath(a), CanonicalPath(b), StringComparison.OrdinalIgnoreCase);
        }

        private static bool IsDescendantPath(string candidate, string ancestor)
        {
            string c = CanonicalPath(candidate);
            string a = CanonicalPath(ancestor);
            if (!a.EndsWith(Path.DirectorySeparatorChar.ToString()))
            {
                a = a + Path.DirectorySeparatorChar;
            }
            return c.StartsWith(a, StringComparison.OrdinalIgnoreCase);
        }

        // ── validation helpers ──────────────────────────────────────────

        private static void EnsureExists(string path)
        {
            if (!File.Exists(path) && !Directory.Exists(path))
            {
                throw new FileNotFoundException("path does not exist: " + path);
            }
        }

        private static void EnsureDirectory(string path)
        {
            if (!Directory.Exists(path))
            {
                throw new ArgumentException("not a directory: " + path);
            }
        }

        private static void EnsureFile(string path)
        {
            EnsureExists(path);
            if (Directory.Exists(path))
            {
                throw new ArgumentException("not a file: " + path);
            }
        }

        private static void EnsureParent(string path, bool createParent)
        {
            string parent = Path.GetDirectoryName(path);
            if (string.IsNullOrEmpty(parent)) return;
            if (Directory.Exists(parent)) return;
            if (File.Exists(parent))
            {
                throw new ArgumentException("parent exists but is not a directory: " + parent);
            }
            if (!createParent)
            {
                throw new ArgumentException("parent directory does not exist: " + parent);
            }
            Directory.CreateDirectory(parent);
        }

        // ── context reading helpers ─────────────────────────────────────

        private static string GetTrimString(IDictionary<string, object> ctx, string key)
        {
            object value;
            if (!ctx.TryGetValue(key, out value) || value == null)
            {
                return null;
            }
            string text = value.ToString().Trim();
            return text.Length == 0 ? null : text;
        }

        private static long GetLong(IDictionary<string, object> ctx, string key, long defaultValue)
        {
            object value;
            if (!ctx.TryGetValue(key, out value) || value == null)
            {
                return defaultValue;
            }
            if (value is long) return (long)value;
            if (value is int) return (int)value;
            long parsed;
            if (long.TryParse(value.ToString(), out parsed))
            {
                return parsed;
            }
            return defaultValue;
        }

        private static int GetInt(IDictionary<string, object> ctx, string key, int defaultValue)
        {
            object value;
            if (!ctx.TryGetValue(key, out value) || value == null)
            {
                return defaultValue;
            }
            if (value is int) return (int)value;
            if (value is long) return (int)(long)value;
            int parsed;
            if (int.TryParse(value.ToString(), out parsed))
            {
                return parsed;
            }
            return defaultValue;
        }

        private static bool GetBool(IDictionary<string, object> ctx, string key, bool defaultValue)
        {
            object value;
            if (!ctx.TryGetValue(key, out value) || value == null)
            {
                return defaultValue;
            }
            if (value is bool) return (bool)value;
            bool parsed;
            if (bool.TryParse(value.ToString(), out parsed))
            {
                return parsed;
            }
            return defaultValue;
        }

        private static byte[] GetBytes(IDictionary<string, object> ctx, string key)
        {
            object value;
            if (!ctx.TryGetValue(key, out value) || value == null)
            {
                return null;
            }
            if (value is byte[]) return (byte[])value;
            if (value is string) return Encoding.UTF8.GetBytes((string)value);
            return null;
        }

        private static List<string> ParseSourcePaths(IDictionary<string, object> ctx, string listKey, string singleKey, string cwd)
        {
            List<string> result = new List<string>();
            object listValue;
            if (ctx.TryGetValue(listKey, out listValue) && listValue != null)
            {
                IEnumerable enumerable = listValue as IEnumerable;
                if (enumerable != null && !(listValue is string))
                {
                    foreach (object item in enumerable)
                    {
                        if (item == null) continue;
                        string trimmed = item.ToString().Trim();
                        if (trimmed.Length > 0)
                        {
                            result.Add(ResolvePath(trimmed, cwd));
                        }
                    }
                    return result;
                }
                string single = listValue.ToString().Trim();
                if (single.Length > 0)
                {
                    result.Add(ResolvePath(single, cwd));
                }
                return result;
            }

            object singleValue;
            if (ctx.TryGetValue(singleKey, out singleValue) && singleValue != null)
            {
                string single = singleValue.ToString().Trim();
                if (single.Length > 0)
                {
                    result.Add(ResolvePath(single, cwd));
                }
            }
            return result;
        }

        private static DateTime ParseModifiedAt(IDictionary<string, object> ctx)
        {
            object epochObj;
            if (ctx.TryGetValue("modifiedAtEpochMs", out epochObj) && epochObj != null)
            {
                long epochMs;
                if (epochObj is long)
                {
                    epochMs = (long)epochObj;
                }
                else if (epochObj is int)
                {
                    epochMs = (int)epochObj;
                }
                else if (long.TryParse(epochObj.ToString(), out epochMs))
                {
                    // parsed
                }
                else
                {
                    epochMs = -1;
                }
                if (epochMs > 0)
                {
                    return DateTimeOffset.FromUnixTimeMilliseconds(epochMs).UtcDateTime;
                }
            }
            return DateTime.UtcNow;
        }

        // ── error / format helpers ──────────────────────────────────────

        private static void Fail(Dictionary<string, object> result, string errorCode, string message)
        {
            result["errorCode"] = errorCode;
            result["error"] = message;
        }

        private static string IsoTime(DateTime utc)
        {
            return utc.ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'");
        }

        private static string SafeMessage(Exception ex)
        {
            if (ex == null) return "unknown error";
            string message = ex.Message;
            if (string.IsNullOrEmpty(message)) return ex.GetType().Name;
            return message;
        }
    }
}
