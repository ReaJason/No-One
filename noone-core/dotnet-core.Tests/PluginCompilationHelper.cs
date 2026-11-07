using System;
using System.IO;
using System.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.Emit;

namespace dotnet_core.Tests;

internal static class PluginCompilationHelper
{
    public static byte[] CompileSingleTypePlugin(string assemblyName, string resultValue)
    {
        string source = @"
using System.Collections.Generic;

public class TestPlugin
{
    public object Execute(IDictionary<string, object> ctx)
    {
        return """ + EscapeString(resultValue) + @""";
    }
}
";
        return CompileAssembly(assemblyName, source);
    }

    public static byte[] CompileTwoTypePlugin(string assemblyName)
    {
        string source = @"
using System.Collections.Generic;

public class FirstPlugin
{
    public object Execute(IDictionary<string, object> ctx)
    {
        return ""first"";
    }
}

public class SecondPlugin
{
    public object Execute(IDictionary<string, object> ctx)
    {
        return ""second"";
    }
}
";
        return CompileAssembly(assemblyName, source);
    }

    private static byte[] CompileAssembly(string assemblyName, string source)
    {
        SyntaxTree syntaxTree = CSharpSyntaxTree.ParseText(source);

        string trustedPlatformAssemblies = (string?)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")
            ?? throw new InvalidOperationException("TRUSTED_PLATFORM_ASSEMBLIES not available.");

        MetadataReference[] references = trustedPlatformAssemblies
            .Split(Path.PathSeparator)
            .Select(path => MetadataReference.CreateFromFile(path))
            .ToArray();

        CSharpCompilation compilation = CSharpCompilation.Create(
            assemblyName,
            new[] { syntaxTree },
            references,
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary, optimizationLevel: OptimizationLevel.Release)
        );

        using MemoryStream output = new MemoryStream();
        EmitResult emit = compilation.Emit(output);
        if (!emit.Success)
        {
            string errors = string.Join(Environment.NewLine, emit.Diagnostics.Where(d => d.Severity == DiagnosticSeverity.Error));
            throw new InvalidOperationException("Plugin compilation failed:" + Environment.NewLine + errors);
        }

        return output.ToArray();
    }

    private static string EscapeString(string value)
    {
        return value.Replace("\\", "\\\\").Replace("\"", "\\\"");
    }
}
