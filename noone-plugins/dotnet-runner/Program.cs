using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Text.Json;

/// <summary>
/// .NET Plugin Runner — loads a plugin DLL via reflection, instantiates the
/// first public class, invokes Equals(Dictionary), and writes the result to
/// a JSON file.
///
/// Usage: dotnet dotnet-runner.dll <plugin.dll> <className> <input.json> <output.json>
/// </summary>
class Program
{
    static int Main(string[] args)
    {
        if (args.Length < 4)
        {
            Console.Error.WriteLine("Usage: dotnet dotnet-runner.dll <plugin.dll> <className> <input.json> <output.json>");
            return 1;
        }

        string pluginDll = args[0];
        string className = args[1];
        string inputJson = args[2];
        string outputJson = args[3];

        try
        {
            Console.WriteLine($"[Runner] Loading assembly: {pluginDll}");
            Assembly assembly = Assembly.LoadFrom(pluginDll);

            Type pluginType = assembly.GetType(className);
            if (pluginType == null)
            {
                // Try searching all types
                foreach (Type t in assembly.GetExportedTypes())
                {
                    if (t.Name == className || t.FullName == className)
                    {
                        pluginType = t;
                        break;
                    }
                }
            }

            if (pluginType == null)
            {
                Console.Error.WriteLine($"[Runner] Class not found: {className}");
                Console.Error.WriteLine("[Runner] Available types:");
                foreach (Type t in assembly.GetExportedTypes())
                {
                    Console.Error.WriteLine($"  - {t.FullName}");
                }
                return 1;
            }

            Console.WriteLine($"[Runner] Instantiating: {pluginType.FullName}");
            object plugin = Activator.CreateInstance(pluginType);

            // Read input context
            string inputText = File.ReadAllText(inputJson);
            Dictionary<string, object> ctx = JsonSerializer.Deserialize<Dictionary<string, object>>(inputText)
                ?? new Dictionary<string, object>();

            Console.WriteLine($"[Runner] Calling Equals() with {ctx.Count} context keys");
            plugin.Equals(ctx);

            // Extract result
            object result;
            ctx.TryGetValue("result", out result);

            string resultJson = result != null
                ? JsonSerializer.Serialize(result, new JsonSerializerOptions { WriteIndented = false })
                : "{}";

            File.WriteAllText(outputJson, resultJson);
            Console.WriteLine("[Runner] Done. Output written to " + outputJson);
            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"[Runner] FAILED: {ex.Message}");
            Console.Error.WriteLine(ex.StackTrace);
            try
            {
                string errorJson = JsonSerializer.Serialize(new Dictionary<string, string>
                {
                    { "error", ex.GetType().Name + ": " + ex.Message }
                });
                File.WriteAllText(outputJson, errorJson);
            }
            catch { }
            return 1;
        }
    }
}
