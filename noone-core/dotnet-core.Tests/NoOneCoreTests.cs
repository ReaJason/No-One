using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using Xunit;

namespace dotnet_core.Tests;

public class NoOneCoreTests
{
    private readonly NoOneCore _core = new NoOneCore();

    public NoOneCoreTests()
    {
        NoOneCore.loadedPluginCache.Clear();
        NoOneCore.globalCaches.Clear();
    }

    [Fact]
    public void Serialize_EmptyMap_MatchesJavaVector()
    {
        Dictionary<string, object> map = new Dictionary<string, object>();
        byte[] bytes = _core.Serialize(map);
        Assert.Equal("1000000000", ToHex(bytes));
    }

    [Fact]
    public void Serialize_MixedMap_MatchesJavaVector()
    {
        Dictionary<string, object> map = new Dictionary<string, object>();
        map["bytes"] = new byte[] { 1, 2, 3 };

        Dictionary<string, object> sub = new Dictionary<string, object>();
        sub["v"] = 1L;
        map["sub"] = sub;
        map["arr"] = new object[] { "z", false };

        byte[] bytes = _core.Serialize(map);
        Assert.Equal(
            "100000000300056279746573060000000301020300037375621000000001000176030000000000000001000361727208000000020100017a0500",
            ToHex(bytes)
        );
    }

    [Fact]
    public void Serialize_ComplexMap_MatchesJavaVector()
    {
        Dictionary<string, object> map = new Dictionary<string, object>();
        map["action"] = "status";
        map["n"] = 7;
        map["ok"] = true;
        map["txt"] = "A\0B你";
        map["list"] = new List<object> { "x", 2L };

        byte[] bytes = _core.Serialize(map);
        Assert.Equal(
            "10000000050006616374696f6e01000673746174757300016e020000000700026f6b0501000374787401000741c08042e4bda000046c697374070000000201000178030000000000000002",
            ToHex(bytes)
        );
    }

    [Fact]
    public void Deserialize_WhenRootIsNotMap_Throws()
    {
        Assert.Throws<IOException>(() => _core.Deserialize(new byte[] { 0x01, 0x00, 0x00 }));
    }

    [Fact]
    public void Equals_LoadAndRun_WithPluginBytesOnly_Succeeds()
    {
        byte[] pluginBytes = PluginCompilationHelper.CompileSingleTypePlugin("sample.plugin", "ok");

        Dictionary<string, object> loadRequest = new Dictionary<string, object>();
        loadRequest["action"] = "load";
        loadRequest["plugin"] = "demo";
        loadRequest["pluginBytes"] = pluginBytes;
        loadRequest["assemblyName"] = "sample.plugin";

        Dictionary<string, object> loadResponse = InvokeEquals(loadRequest);
        Assert.Equal(0, loadResponse["code"]);
        Assert.Equal(true, loadResponse["data"]);
        Assert.Equal(true, loadResponse["classDefine"]);

        Dictionary<string, object> runRequest = new Dictionary<string, object>();
        runRequest["action"] = "run";
        runRequest["plugin"] = "demo";
        runRequest["pluginBytes"] = pluginBytes;
        runRequest["args"] = new Dictionary<string, object>();

        Dictionary<string, object> runResponse = InvokeEquals(runRequest);
        Assert.Equal(0, runResponse["code"]);
        Assert.Equal("ok", runResponse["data"]);
        Assert.Equal(true, runResponse["classRun"]);
    }

    [Fact]
    public void Equals_Load_WhenPluginHasMultipleConcreteTypes_Fails()
    {
        byte[] pluginBytes = PluginCompilationHelper.CompileTwoTypePlugin("multi.plugin");

        Dictionary<string, object> request = new Dictionary<string, object>();
        request["action"] = "load";
        request["plugin"] = "demo";
        request["pluginBytes"] = pluginBytes;

        Dictionary<string, object> response = InvokeEquals(request);
        Assert.Equal(1, response["code"]);
        string error = (string)response["error"];
        Assert.Contains("exactly one concrete public type", error, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Equals_StatusAndClean_ReportAndClearPluginCache()
    {
        byte[] pluginBytes = PluginCompilationHelper.CompileSingleTypePlugin("sample.plugin", "ok");
        Dictionary<string, object> loadRequest = new Dictionary<string, object>();
        loadRequest["action"] = "load";
        loadRequest["plugin"] = "demo";
        loadRequest["pluginBytes"] = pluginBytes;
        InvokeEquals(loadRequest);

        Dictionary<string, object> statusRequest = new Dictionary<string, object>();
        statusRequest["action"] = "status";
        Dictionary<string, object> statusResponse = InvokeEquals(statusRequest);
        Assert.Equal(0, statusResponse["code"]);
        List<string> pluginNames = AsStringList(statusResponse["pluginCaches"]);
        Assert.Contains("demo", pluginNames);

        Dictionary<string, object> cleanRequest = new Dictionary<string, object>();
        cleanRequest["action"] = "clean";
        Dictionary<string, object> cleanResponse = InvokeEquals(cleanRequest);
        Assert.Equal(0, cleanResponse["code"]);

        Dictionary<string, object> statusResponseAfterClean = InvokeEquals(statusRequest);
        Assert.Equal(0, statusResponseAfterClean["code"]);
        List<string> pluginNamesAfterClean = AsStringList(statusResponseAfterClean["pluginCaches"]);
        Assert.Empty(pluginNamesAfterClean);
    }

    [Fact]
    public void ToByteArray_ReadsAndClosesInput()
    {
        TrackingStream stream = new TrackingStream(new byte[] { 9, 8, 7 });
        byte[] bytes = NoOneCore.ToByteArray(stream);

        Assert.Equal(new byte[] { 9, 8, 7 }, bytes);
        Assert.True(stream.IsClosed);
    }

    [Fact]
    public void ReflectionHelpers_CanReadFieldAndInvokeMethods()
    {
        DerivedSample sample = new DerivedSample();

        object fieldValue = NoOneCore.GetFieldValue(sample, "_secret");
        Assert.Equal("base", fieldValue);

        object hidden = NoOneCore.InvokeMethod(sample, "Hidden");
        Assert.Equal("hidden", hidden);

        object plus = NoOneCore.InvokeMethod(typeof(BaseSample), "Plus", new[] { typeof(int) }, new object[] { 2 });
        Assert.Equal(3, plus);
    }

    private Dictionary<string, object> InvokeEquals(Dictionary<string, object> request)
    {
        byte[] requestBytes = _core.Serialize(request);
        MemoryStream output = new MemoryStream();
        _core.Equals(new object[] { requestBytes, output });
        return _core.Deserialize(output.ToArray());
    }

    private static string ToHex(byte[] bytes)
    {
        char[] chars = new char[bytes.Length * 2];
        int index = 0;
        foreach (byte b in bytes)
        {
            string s = b.ToString("x2");
            chars[index++] = s[0];
            chars[index++] = s[1];
        }
        return new string(chars);
    }

    private static List<string> AsStringList(object values)
    {
        IEnumerable enumerable = values as IEnumerable
            ?? throw new InvalidOperationException("Expected enumerable.");
        List<string> list = new List<string>();
        foreach (object value in enumerable)
        {
            list.Add((string)value);
        }
        return list;
    }

    private class BaseSample
    {
        private readonly string _secret = "base";

        private string Hidden()
        {
            return _secret == "base" ? "hidden" : "unknown";
        }

        private static int Plus(int value)
        {
            return value + 1;
        }
    }

    private class DerivedSample : BaseSample
    {
    }

    private sealed class TrackingStream : MemoryStream
    {
        public bool IsClosed { get; private set; }

        public TrackingStream(byte[] bytes)
            : base(bytes)
        {
        }

        protected override void Dispose(bool disposing)
        {
            IsClosed = true;
            base.Dispose(disposing);
        }
    }
}
