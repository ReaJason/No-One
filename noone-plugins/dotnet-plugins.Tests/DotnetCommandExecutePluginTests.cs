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
    public void Equals_WithNonMapInput_ReturnsTrue()
    {
        var plugin = new DotnetCommandExecutePlugin();
        bool ok = plugin.Equals("invalid");
        Assert.True(ok);
    }

    [Fact]
    public void Equals_WithoutOp_ShouldReturnError()
    {
        var plugin = new DotnetCommandExecutePlugin();
        var ctx = new Dictionary<string, object>();

        plugin.Equals(ctx);

        var result = Assert.IsType<Dictionary<string, object>>(ctx["result"]);
        Assert.Equal("op is required", result["error"]);
    }

    [Fact]
    public void Equals_CdToChildDirectory_ShouldUpdateCwd()
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

            plugin.Equals(ctx);

            var result = Assert.IsType<Dictionary<string, object>>(ctx["result"]);
            Assert.Equal(0, result["exitCode"]);
            Assert.Equal(Path.GetFullPath(childDir), result["cwd"]);
        }
        finally
        {
            Directory.Delete(baseDir, true);
        }
    }

    [Fact]
    public void Equals_CdToNonExistent_ShouldReturnError()
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

            plugin.Equals(ctx);

            var result = Assert.IsType<Dictionary<string, object>>(ctx["result"]);
            string error = Assert.IsType<string>(result["error"]);
            Assert.Contains("Directory does not exist", error);
        }
        finally
        {
            Directory.Delete(baseDir, true);
        }
    }

    [Fact]
    public void Equals_ExecCommand_ShouldReturnOutput()
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

        plugin.Equals(ctx);

        var result = Assert.IsType<Dictionary<string, object>>(ctx["result"]);
        string json = JsonSerializer.Serialize(result, new JsonSerializerOptions { WriteIndented = true });
        _output.WriteLine(json);

        Assert.Null(result.ContainsKey("error") ? result["error"] : null);
        Assert.Equal(0, result["exitCode"]);
        string stdout = Assert.IsType<string>(result["stdout"]);
        Assert.Contains("hello", stdout);
        Assert.NotNull(result["charsetUsed"]);
    }

    [Fact]
    public void Equals_UnsupportedOp_ShouldReturnError()
    {
        var plugin = new DotnetCommandExecutePlugin();
        var ctx = new Dictionary<string, object>();
        ctx["op"] = "invalid";

        plugin.Equals(ctx);

        var result = Assert.IsType<Dictionary<string, object>>(ctx["result"]);
        Assert.Equal("unsupported op: invalid", result["error"]);
    }
}
