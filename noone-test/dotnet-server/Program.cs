using System;
using System.IO;
using System.Net;
using System.Reflection;
using System.Text;
using System.Text.Json;

/// <summary>
/// Minimal .NET web shell host used by the integration tests.
///
/// It mirrors the HTTP handling of the generated ASPX/ASHX shell:
///   request:  {"hello": "&lt;base64 TLV&gt;"}
///   response: {"hello": "&lt;base64 TLV&gt;"}
///
/// The core DLL is loaded from disk so the test always exercises the freshly
/// built core, and plugins are loaded by the core from the bytes sent over the wire.
///
/// Usage: dotnet-server &lt;dotnet-core.dll&gt; &lt;port&gt;
/// </summary>
sealed class Program
{
    private const string CoreTypeName = "dotnet_core.NoOneCore";

    static int Main(string[] args)
    {
        if (args.Length < 2)
        {
            Console.Error.WriteLine("Usage: dotnet-server <dotnet-core.dll> <port>");
            return 2;
        }

        string corePath = args[0];
        int port = int.Parse(args[1]);

        byte[] coreBytes = File.ReadAllBytes(corePath);
        Assembly assembly = Assembly.Load(coreBytes);
        Type coreType = assembly.GetType(CoreTypeName, true);
        object core = Activator.CreateInstance(coreType);
        MethodInfo equals = coreType.GetMethod("Equals", new[] { typeof(object) });

        HttpListener listener = new HttpListener();
        listener.Prefixes.Add("http://127.0.0.1:" + port + "/");
        listener.Start();
        Console.WriteLine("dotnet shell host listening on " + port);
        Console.Out.Flush();

        while (true)
        {
            HttpListenerContext context;
            try
            {
                context = listener.GetContext();
            }
            catch (HttpListenerException)
            {
                break;
            }

            try
            {
                Handle(context, core, equals);
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
                try
                {
                    context.Response.StatusCode = 500;
                    context.Response.Close();
                }
                catch
                {
                    // ignored
                }
            }
        }

        return 0;
    }

    private static void Handle(HttpListenerContext context, object core, MethodInfo equals)
    {
        using (MemoryStream input = new MemoryStream())
        {
            context.Request.InputStream.CopyTo(input);
            string body = Encoding.UTF8.GetString(input.ToArray());
            byte[] payload = Convert.FromBase64String(ExtractPayload(body));

            byte[] result;
            using (MemoryStream output = new MemoryStream())
            {
                equals.Invoke(core, new object[] { new object[] { payload, output } });
                result = output.ToArray();
            }

            byte[] responseBytes = Encoding.UTF8.GetBytes(
                "{\"hello\": \"" + Convert.ToBase64String(result) + "\"}");

            context.Response.StatusCode = 200;
            context.Response.ContentType = "application/json; charset=utf-8";
            context.Response.ContentLength64 = responseBytes.Length;
            context.Response.OutputStream.Write(responseBytes, 0, responseBytes.Length);
            context.Response.OutputStream.Close();
        }
    }

    private static string ExtractPayload(string body)
    {
        try
        {
            using (JsonDocument document = JsonDocument.Parse(body))
            {
                JsonElement value;
                if (document.RootElement.TryGetProperty("hello", out value))
                {
                    return value.GetString();
                }
            }
        }
        catch (JsonException)
        {
            // fall through to the template window below
        }

        return body.Substring(11, body.Length - 11 - 2);
    }
}
