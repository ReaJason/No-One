using System.Collections.Generic;
using System.IO;
using System.Runtime.InteropServices;
using System.Text.Json;
using NoOne.Plugins.Dotnet.CommandExecute;
using Xunit;
using Xunit.Abstractions;

namespace dotnet_plugins.Tests;

public class DotnetCommandExecutePluginTests
{
    private readonly ITestOutputHelper _output;

    public DotnetCommandExecutePluginTests(ITestOutputHelper output)
    {
        _output = output;
    }

    [Fact]
    public void Execute_WithoutOp_ShouldThrow()
    {
        var plugin = new DotnetCommandExecutePlugin();
        var ctx = new Dictionary<string, object>();

        var error = Assert.Throws<System.ArgumentException>(() => plugin.Execute(ctx));
        Assert.Equal("op is required", error.Message);
    }

    [Fact]
    public void Execute_CdToChildDirectory_ShouldUpdateCwd()
    {
        var plugin = new DotnetCommandExecutePlugin();
        string baseDir = Path.Combine(Path.GetTempPath(), "cmd-exec-test-" + Path.GetRandomFileName());
        string childDir = Path.Combine(baseDir, "child");
        Directory.CreateDirectory(childDir);

        try
        {
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "cd";
            ctx["cdTarget"] = "child";
            ctx["cwd"] = baseDir;

            var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
            Assert.Equal(0, result["exitCode"]);
            Assert.Equal(Path.GetFullPath(childDir), result["cwd"]);
        }
        finally
        {
            Directory.Delete(baseDir, true);
        }
    }

    [Fact]
    public void Execute_CdToNonExistent_ShouldThrow()
    {
        var plugin = new DotnetCommandExecutePlugin();
        string baseDir = Path.Combine(Path.GetTempPath(), "cmd-exec-test-" + Path.GetRandomFileName());
        Directory.CreateDirectory(baseDir);

        try
        {
            var ctx = new Dictionary<string, object>();
            ctx["op"] = "cd";
            ctx["cdTarget"] = "not-exists";
            ctx["cwd"] = baseDir;

            var error = Assert.Throws<System.ArgumentException>(() => plugin.Execute(ctx));
            Assert.Contains("Directory does not exist", error.Message);
        }
        finally
        {
            Directory.Delete(baseDir, true);
        }
    }

    [Fact]
    public void Execute_ExecCommand_ShouldReturnOutput()
    {
        var plugin = new DotnetCommandExecutePlugin();
        string workDir = Path.GetTempPath();

        var ctx = new Dictionary<string, object>();
        ctx["op"] = "exec";
        ctx["cwd"] = workDir;

        if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
        {
            ctx["executable"] = "cmd.exe";
            ctx["argv"] = new List<object> { "/c", "echo hello" };
        }
        else
        {
            ctx["executable"] = "echo";
            ctx["argv"] = new List<object> { "hello" };
        }

        var result = Assert.IsType<Dictionary<string, object>>(plugin.Execute(ctx));
        string json = JsonSerializer.Serialize(result, new JsonSerializerOptions { WriteIndented = true });
        _output.WriteLine(json);

        Assert.Equal(0, result["exitCode"]);
        string stdout = Assert.IsType<string>(result["stdout"]);
        Assert.Contains("hello", stdout);
        Assert.NotNull(result["charsetUsed"]);
    }

    [Fact]
    public void Execute_UnsupportedOp_ShouldThrow()
    {
        var plugin = new DotnetCommandExecutePlugin();
        var ctx = new Dictionary<string, object>();
        ctx["op"] = "invalid";

        var error = Assert.Throws<System.ArgumentException>(() => plugin.Execute(ctx));
        Assert.Equal("unsupported op: invalid", error.Message);
    }
}
