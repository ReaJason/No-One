using System;
using System.Collections;
using System.Collections.Generic;
using System.Text.Json;
using NoOne.Plugins.Dotnet.SystemInfo;
using Xunit;
using Xunit.Abstractions;

namespace dotnet_plugins.Tests;

public class DotnetSystemInfoPluginTests
{
    private readonly ITestOutputHelper _output;

    public DotnetSystemInfoPluginTests(ITestOutputHelper output)
    {
        _output = output;
    }

    [Fact]
    public void Equals_WithNonMapInput_ReturnsTrue()
    {
        var plugin = new DotnetSystemInfoPlugin();

        bool ok = plugin.Equals("invalid");

        Assert.True(ok);
    }

    [Fact]
    public void Equals_ShouldPopulateResultWithCoreSections()
    {
        var plugin = new DotnetSystemInfoPlugin();
        var ctx = new Dictionary<string, object>();

        bool ok = plugin.Equals(ctx);
        string json = JsonSerializer.Serialize(ctx["result"], new JsonSerializerOptions { WriteIndented = true });
        _output.WriteLine(json);
        Assert.True(ok);
        Assert.True(ctx.ContainsKey("result"));

        var result = Assert.IsType<Dictionary<string, object>>(ctx["result"]);
        Assert.True(result.ContainsKey("os"));
        Assert.True(result.ContainsKey("runtime"));
        Assert.True(result.ContainsKey("env"));
        Assert.True(result.ContainsKey("process"));
        Assert.True(result.ContainsKey("network"));
        Assert.True(result.ContainsKey("file_systems"));
    }

    [Fact]
    public void Equals_RuntimeAndOsFields_ShouldMatchContract()
    {
        var plugin = new DotnetSystemInfoPlugin();
        var ctx = new Dictionary<string, object>();
        plugin.Equals(ctx);

        var result = Assert.IsType<Dictionary<string, object>>(ctx["result"]);
        var runtime = Assert.IsType<Dictionary<string, object>>(result["runtime"]);
        var os = Assert.IsType<Dictionary<string, object>>(result["os"]);
        var process = Assert.IsType<Dictionary<string, object>>(result["process"]);
        var env = Assert.IsAssignableFrom<IDictionary>(result["env"]);
        var fileSystems = Assert.IsAssignableFrom<IEnumerable>(result["file_systems"]);

        Assert.Equal("dotnet", Assert.IsType<string>(runtime["type"]));
        Assert.False(string.IsNullOrWhiteSpace(Assert.IsType<string>(runtime["version"])));
        Assert.False(string.IsNullOrWhiteSpace(Assert.IsType<string>(os["platform_type"])));
        Assert.False(string.IsNullOrWhiteSpace(Assert.IsType<string>(process["pid"])));
        Assert.NotNull(env);
        Assert.NotNull(fileSystems);
    }
}
