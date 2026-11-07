using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.Json;
using NoOne.Plugins.Dotnet.FileManager;
using Xunit;
using Xunit.Abstractions;

namespace dotnet_plugins.Tests;

public class DotnetFileManagerPluginTests
{
    private readonly ITestOutputHelper _output;

    public DotnetFileManagerPluginTests(ITestOutputHelper output)
    {
        _output = output;
    }

    private void PrintResult(object result)
    {
        string json = JsonSerializer.Serialize(result,
            new JsonSerializerOptions { WriteIndented = true });
        _output.WriteLine(json);
    }

    private string CreateTempDir()
    {
        string dir = Path.Combine(Path.GetTempPath(), "fm-test-" + Path.GetRandomFileName());
        Directory.CreateDirectory(dir);
        return dir;
    }

    [Fact]
    public void Execute_WithoutOp_ShouldThrow()
    {
        var plugin = new DotnetFileManagerPlugin();
        var ctx = new Dictionary<string, object>();

        var error = Assert.Throws<System.ArgumentException>(() => plugin.Execute(ctx));
        Assert.Equal("op is required", error.Message);
    }

    [Fact]
    public void List_ShouldReturnEntries()
    {
        string dir = CreateTempDir();
        try
        {
            File.WriteAllText(Path.Combine(dir, "a.txt"), "hello");
            Directory.CreateDirectory(Path.Combine(dir, "subdir"));

            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "list";
            ctx["path"] = dir;

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.True(result.ContainsKey("entries"));
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void Stat_ShouldReturnEntry()
    {
        string dir = CreateTempDir();
        string file = Path.Combine(dir, "test.txt");
        try
        {
            File.WriteAllText(file, "content");

            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "stat";
            ctx["path"] = file;

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.True(result.ContainsKey("entry"));
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void ReadAll_ShouldReturnBytes()
    {
        string dir = CreateTempDir();
        string file = Path.Combine(dir, "read.txt");
        try
        {
            File.WriteAllText(file, "hello world");

            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "read-all";
            ctx["path"] = file;

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.True(result.ContainsKey("bytes"));
            Assert.Equal(11L, result["fileSize"]);
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void ReadChunk_ShouldReturnChunkWithOffset()
    {
        string dir = CreateTempDir();
        string file = Path.Combine(dir, "chunk.txt");
        try
        {
            File.WriteAllBytes(file, new byte[1024]);

            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "read-chunk";
            ctx["path"] = file;
            ctx["offset"] = 100L;
            ctx["length"] = 200;

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.Equal(100L, result["offset"]);
            Assert.Equal(300L, result["nextOffset"]);
            Assert.Equal(1024L, result["fileSize"]);
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void WriteAll_ShouldWriteFile()
    {
        string dir = CreateTempDir();
        string file = Path.Combine(dir, "write.txt");
        try
        {
            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "write-all";
            ctx["path"] = file;
            ctx["bytes"] = Encoding.UTF8.GetBytes("written content");

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.Equal("written content", File.ReadAllText(file));
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void Mkdir_ShouldCreateDirectory()
    {
        string dir = CreateTempDir();
        string newDir = Path.Combine(dir, "newdir");
        try
        {
            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "mkdir";
            ctx["path"] = newDir;

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.True((bool)result["created"]);
            Assert.True(Directory.Exists(newDir));
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void CreateFile_ShouldCreateEmptyFile()
    {
        string dir = CreateTempDir();
        string file = Path.Combine(dir, "new.txt");
        try
        {
            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "create-file";
            ctx["path"] = file;

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.True((bool)result["created"]);
            Assert.True(File.Exists(file));
            Assert.Equal(0L, result["fileSize"]);
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void Rename_ShouldRenameFile()
    {
        string dir = CreateTempDir();
        string file = Path.Combine(dir, "old.txt");
        try
        {
            File.WriteAllText(file, "data");

            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "rename";
            ctx["path"] = file;
            ctx["newName"] = "new.txt";

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.False(File.Exists(file));
            Assert.True(File.Exists(Path.Combine(dir, "new.txt")));
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void Delete_ShouldDeleteFile()
    {
        string dir = CreateTempDir();
        string file = Path.Combine(dir, "todelete.txt");
        try
        {
            File.WriteAllText(file, "delete me");

            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "delete";
            ctx["path"] = file;

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.False(File.Exists(file));
        }
        finally
        {
            if (Directory.Exists(dir)) Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void Delete_DirectoryWithoutRecursive_ShouldFail()
    {
        string dir = CreateTempDir();
        string subDir = Path.Combine(dir, "sub");
        try
        {
            Directory.CreateDirectory(subDir);

            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "delete";
            ctx["path"] = subDir;

            var error = Assert.Throws<System.ArgumentException>(() => plugin.Execute(ctx));
            Assert.Contains("recursive=true", error.Message);
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void Copy_ShouldCopyFile()
    {
        string dir = CreateTempDir();
        string src = Path.Combine(dir, "src.txt");
        string dest = Path.Combine(dir, "dest.txt");
        try
        {
            File.WriteAllText(src, "copy me");

            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "copy";
            ctx["sourcePath"] = src;
            ctx["destinationPath"] = dest;

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.True(File.Exists(src));
            Assert.True(File.Exists(dest));
            Assert.Equal("copy me", File.ReadAllText(dest));
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void Move_ShouldMoveFile()
    {
        string dir = CreateTempDir();
        string src = Path.Combine(dir, "moveme.txt");
        string dest = Path.Combine(dir, "moved.txt");
        try
        {
            File.WriteAllText(src, "move me");

            var plugin = new DotnetFileManagerPlugin();
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "move";
            ctx["sourcePath"] = src;
            ctx["destinationPath"] = dest;

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            PrintResult(result);
            Assert.False(File.Exists(src));
            Assert.True(File.Exists(dest));
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void Zip_And_Unzip_ShouldWork()
    {
        string dir = CreateTempDir();
        string srcDir = Path.Combine(dir, "to-zip");
        string zipFile = Path.Combine(dir, "archive.zip");
        string unzipDir = Path.Combine(dir, "unzipped");
        try
        {
            Directory.CreateDirectory(srcDir);
            File.WriteAllText(Path.Combine(srcDir, "a.txt"), "aaa");
            File.WriteAllText(Path.Combine(srcDir, "b.txt"), "bbb");

            var plugin = new DotnetFileManagerPlugin();

            // zip
            var zipCtx = new Dictionary<string, object>();
            zipCtx["op"] = "zip";
            zipCtx["sourcePath"] = srcDir;
            zipCtx["destinationPath"] = zipFile;

            var zipResult = Assert.IsType<Dictionary<string, object>>(plugin.Execute(zipCtx));
            PrintResult(zipResult);
            Assert.True(File.Exists(zipFile));

            // unzip
            var unzipCtx = new Dictionary<string, object>();
            unzipCtx["op"] = "unzip";
            unzipCtx["path"] = zipFile;
            unzipCtx["destinationPath"] = unzipDir;

            var unzipResult = Assert.IsType<Dictionary<string, object>>(plugin.Execute(unzipCtx));
            PrintResult(unzipResult);
            Assert.True(Directory.Exists(unzipDir));
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }
}
