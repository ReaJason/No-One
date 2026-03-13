(function() {
    const ACTION = "action";
    const PLUGIN = "plugin";
    const CLASS_BYTES = "pluginCode";
    const VERSION = "version";
    const ARGS = "args";

    const REFRESH = "refresh";
    const PLUGIN_CACHES = "pluginCaches";
    const GLOBAL_CACHES = "globalCaches";

    const ACTION_STATUS = "status";
    const ACTION_RUN = "run";
    const ACTION_LOAD = "load";
    const ACTION_CLEAN = "clean";

    const CODE = "code";
    const ERROR = "error";
    const DATA = "data";
    const SUCCESS = 0;
    const FAILURE = 1;

    const NULL = 0x00;
    const STRING = 0x01;
    const INTEGER = 0x02;
    const LONG = 0x03;
    const DOUBLE = 0x04;
    const BOOLEAN = 0x05;
    const BYTE_ARRAY = 0x06;
    const LIST = 0x07;
    const OBJECT_ARRAY = 0x08;
    const SET = 0x09;
    const MAP = 0x10;

    const loadedPluginCache = new Map();
    const loadedPluginVersionCache = new Map();
    const globalCaches = new Map();

    function serialize(obj) {
        const writer = createWriter();
        writeMap(writer, obj);
        return writer.toBuffer();
    }

    function deserialize(buffer) {
        const reader = createReader(buffer);
        const type = reader.readByte();
        if (type !== MAP) {
            throw new Error("Root object is not a Map.");
        }
        return readMap(reader);
    }

    function createWriter() {
        const chunks = [];
        return {
            writeByte(value) {
                const buf = Buffer.allocUnsafe(1);
                buf.writeInt8(value, 0);
                chunks.push(buf);
            },
            writeBoolean(value) {
                this.writeByte(value ? 1 : 0);
            },
            writeInt(value) {
                const buf = Buffer.allocUnsafe(4);
                buf.writeInt32BE(value, 0);
                chunks.push(buf);
            },
            writeLong(value) {
                const normalized = typeof value === "bigint"
                    ? value
                    : BigInt(Math.trunc(Number(value)));
                const buf = Buffer.allocUnsafe(8);
                buf.writeBigInt64BE(normalized, 0);
                chunks.push(buf);
            },
            writeDouble(value) {
                const buf = Buffer.allocUnsafe(8);
                buf.writeDoubleBE(value, 0);
                chunks.push(buf);
            },
            writeRaw(value) {
                chunks.push(Buffer.from(value));
            },
            writeUTF(value) {
                const utfBytes = encodeModifiedUtf8(String(value));
                if (utfBytes.length > 0xffff) {
                    throw new Error("Encoded string too long for modified UTF-8.");
                }
                const len = Buffer.allocUnsafe(2);
                len.writeUInt16BE(utfBytes.length, 0);
                chunks.push(len, utfBytes);
            },
            toBuffer() {
                return Buffer.concat(chunks);
            }
        };
    }

    function createReader(input) {
        const buffer = Buffer.from(input);
        let offset = 0;

        function requireLength(length) {
            if (offset + length > buffer.length) {
                throw new Error("Unexpected end of buffer.");
            }
        }

        return {
            readByte() {
                requireLength(1);
                const value = buffer.readInt8(offset);
                offset += 1;
                return value;
            },
            readBoolean() {
                return this.readByte() !== 0;
            },
            readInt() {
                requireLength(4);
                const value = buffer.readInt32BE(offset);
                offset += 4;
                return value;
            },
            readLong() {
                requireLength(8);
                const value = buffer.readBigInt64BE(offset);
                offset += 8;
                return Number(value);
            },
            readDouble() {
                requireLength(8);
                const value = buffer.readDoubleBE(offset);
                offset += 8;
                return value;
            },
            readRaw(length) {
                if (length < 0) {
                    throw new Error("Negative length found in stream.");
                }
                requireLength(length);
                const value = Buffer.from(buffer.subarray(offset, offset + length));
                offset += length;
                return value;
            },
            readUTF() {
                requireLength(2);
                const length = buffer.readUInt16BE(offset);
                offset += 2;
                const bytes = this.readRaw(length);
                return decodeModifiedUtf8(bytes);
            }
        };
    }

    function writeMap(writer, map) {
        writer.writeByte(MAP);
        if (map == null) {
            writer.writeInt(0);
            return;
        }
        const entries = toMapEntries(map);
        writer.writeInt(entries.length);
        for (const [key, value] of entries) {
            writer.writeUTF(String(key));
            writeObject(writer, value);
        }
    }

    function writeObject(writer, obj) {
        if (obj == null) {
            writer.writeByte(NULL);
            return;
        }
        if (typeof obj === "string") {
            writer.writeByte(STRING);
            writer.writeUTF(obj);
            return;
        }
        if (typeof obj === "number") {
            if (Number.isInteger(obj) && obj >= -2147483648 && obj <= 2147483647) {
                writer.writeByte(INTEGER);
                writer.writeInt(obj);
            } else if (Number.isInteger(obj)) {
                writer.writeByte(LONG);
                writer.writeLong(obj);
            } else {
                writer.writeByte(DOUBLE);
                writer.writeDouble(obj);
            }
            return;
        }
        if (typeof obj === "bigint") {
            writer.writeByte(LONG);
            writer.writeLong(obj);
            return;
        }
        if (typeof obj === "boolean") {
            writer.writeByte(BOOLEAN);
            writer.writeBoolean(obj);
            return;
        }
        if (Buffer.isBuffer(obj) || obj instanceof Uint8Array) {
            const bytes = Buffer.from(obj);
            writer.writeByte(BYTE_ARRAY);
            writer.writeInt(bytes.length);
            writer.writeRaw(bytes);
            return;
        }
        if (obj instanceof Set) {
            writer.writeByte(SET);
            writer.writeInt(obj.size);
            for (const item of obj) {
                writeObject(writer, item);
            }
            return;
        }
        if (Array.isArray(obj)) {
            writer.writeByte(LIST);
            writer.writeInt(obj.length);
            for (const item of obj) {
                writeObject(writer, item);
            }
            return;
        }
        if (obj instanceof Map || isPlainObject(obj)) {
            writeMap(writer, obj);
            return;
        }
        throw new Error(`Unsupported type for serialization: ${obj.constructor?.name ?? typeof obj}`);
    }

    function readMap(reader) {
        const size = reader.readInt();
        if (size < 0) {
            throw new Error(`Negative map size found in stream: ${size}`);
        }
        const map = {};
        for (let i = 0; i < size; i++) {
            const key = reader.readUTF();
            map[key] = readObject(reader);
        }
        return map;
    }

    function readObject(reader) {
        const type = reader.readByte();
        switch (type) {
            case NULL:
                return null;
            case STRING:
                return reader.readUTF();
            case INTEGER:
                return reader.readInt();
            case LONG:
                return reader.readLong();
            case DOUBLE:
                return reader.readDouble();
            case BOOLEAN:
                return reader.readBoolean();
            case BYTE_ARRAY: {
                const len = reader.readInt();
                return reader.readRaw(len);
            }
            case SET: {
                const size = reader.readInt();
                if (size < 0) {
                    throw new Error(`Negative set size found in stream: ${size}`);
                }
                const set = new Set();
                for (let i = 0; i < size; i++) {
                    set.add(readObject(reader));
                }
                return set;
            }
            case LIST:
            case OBJECT_ARRAY: {
                const size = reader.readInt();
                if (size < 0) {
                    throw new Error(`Negative array size found in stream: ${size}`);
                }
                const list = new Array(size);
                for (let i = 0; i < size; i++) {
                    list[i] = readObject(reader);
                }
                return list;
            }
            case MAP:
                return readMap(reader);
            default:
                throw new Error(`Unknown data type found in stream: ${type}`);
        }
    }

    function toMapEntries(value) {
        if (value instanceof Map) {
            return Array.from(value.entries());
        }
        if (isPlainObject(value)) {
            return Object.entries(value);
        }
        throw new Error(`Unsupported map type: ${value?.constructor?.name ?? typeof value}`);
    }

    function isPlainObject(value) {
        return value != null
            && typeof value === "object"
            && !Array.isArray(value)
            && !Buffer.isBuffer(value)
            && !(value instanceof Uint8Array)
            && !(value instanceof Map)
            && !(value instanceof Set);
    }

    function encodeModifiedUtf8(value) {
        const bytes = [];
        for (let i = 0; i < value.length; i++) {
            const c = value.charCodeAt(i);
            if (c >= 0x0001 && c <= 0x007f) {
                bytes.push(c);
            } else if (c <= 0x07ff) {
                bytes.push(0xc0 | ((c >> 6) & 0x1f));
                bytes.push(0x80 | (c & 0x3f));
            } else {
                bytes.push(0xe0 | ((c >> 12) & 0x0f));
                bytes.push(0x80 | ((c >> 6) & 0x3f));
                bytes.push(0x80 | (c & 0x3f));
            }
        }
        return Buffer.from(bytes);
    }

    function decodeModifiedUtf8(buffer) {
        let out = "";
        let i = 0;
        while (i < buffer.length) {
            const b1 = buffer[i++] & 0xff;
            if ((b1 & 0x80) === 0) {
                if (b1 === 0) {
                    throw new Error("Invalid modified UTF-8 byte 0x00.");
                }
                out += String.fromCharCode(b1);
                continue;
            }
            if ((b1 & 0xe0) === 0xc0) {
                if (i >= buffer.length) {
                    throw new Error("Malformed modified UTF-8 sequence.");
                }
                const b2 = buffer[i++] & 0xff;
                if ((b2 & 0xc0) !== 0x80) {
                    throw new Error("Malformed modified UTF-8 continuation byte.");
                }
                const c = ((b1 & 0x1f) << 6) | (b2 & 0x3f);
                out += String.fromCharCode(c);
                continue;
            }
            if ((b1 & 0xf0) === 0xe0) {
                if (i + 1 >= buffer.length) {
                    throw new Error("Malformed modified UTF-8 sequence.");
                }
                const b2 = buffer[i++] & 0xff;
                const b3 = buffer[i++] & 0xff;
                if ((b2 & 0xc0) !== 0x80 || (b3 & 0xc0) !== 0x80) {
                    throw new Error("Malformed modified UTF-8 continuation byte.");
                }
                const c = ((b1 & 0x0f) << 12) | ((b2 & 0x3f) << 6) | (b3 & 0x3f);
                out += String.fromCharCode(c);
                continue;
            }
            throw new Error(`Invalid modified UTF-8 leading byte: ${b1}`);
        }
        return out;
    }

    function getStatus() {
        const result = {};
        result[PLUGIN_CACHES] = Object.fromEntries(loadedPluginVersionCache);
        return result;
    }

    function load(args, result) {
        const plugin = args[PLUGIN];
        const pluginCode = args[CLASS_BYTES];
        const version = args[VERSION] == null ? null : String(args[VERSION]);
        const refresh = args[REFRESH] === true || String(args[REFRESH]).toLowerCase() === "true";
        let pluginObj = refresh ? null : loadedPluginCache.get(plugin);
        if (pluginObj == null) {
            if (plugin == null) {
                throw new Error("plugin is required");
            }
            if (pluginCode == null) {
                throw new Error("pluginCode are required for class loading");
            }

            try {
                const pluginFunc = new Function(pluginCode)();
                if (typeof pluginFunc === 'function') {
                    pluginObj = pluginFunc;
                } else {
                    pluginObj = pluginFunc;
                }
                loadedPluginCache.set(plugin, pluginObj);
                loadedPluginVersionCache.set(plugin, version);
            } catch (error) {
                console.error(error);
                throw new Error(`Failed to load class: ${error.message}\n${error.stack}`);
            }
        }
        return pluginObj;
    }

    async function run(args) {
        const plugin = args[PLUGIN];
        const pluginObj = loadedPluginCache.get(plugin);
        console.log("pluginObj: " + pluginObj);
        let map = args[ARGS];
        if (map == null) {
            map = {};
        }
        map[PLUGIN_CACHES] = Object.fromEntries(loadedPluginCache);
        map[GLOBAL_CACHES] = Object.fromEntries(globalCaches);
        await pluginObj.equals(map);
        console.log("plugin result: ", map.result);
        const result = {};
        result[DATA] = map.result;
        return result;
    }

    async function equals(inputBytes) {
        const result = {};
        result[CODE] = SUCCESS;

        let args = {};
        try {
            args = deserialize(inputBytes);
        } catch (e) {
            result[CODE] = FAILURE;
            result[ERROR] = `args parsed failed: ${e.message}\n${e.stack}`;
            return serialize(result);
        }
        const action = args[ACTION];
        if (action != null) {
            try {
                switch (action) {
                    case ACTION_STATUS:
                        Object.assign(result, getStatus());
                        break;
                    case ACTION_RUN:
                        Object.assign(result, await run(args));
                        break;
                    case ACTION_LOAD:
                        const loaded = load(args, result);
                        result[DATA] = loaded != null;
                        break;
                    case ACTION_CLEAN:
                        loadedPluginCache.clear();
                        loadedPluginVersionCache.clear();
                        globalCaches.clear();
                        result[DATA] = true;
                        break;
                    default:
                        result[CODE] = FAILURE;
                        result[ERROR] = `action [${action}] not supported`;
                        break;
                }
            } catch (e) {
                result[CODE] = FAILURE;
                result[ERROR] = `action [${action}] run failed: ${e.message}\n${e.stack}`;
            }
        } else {
            result[CODE] = FAILURE;
            result[ERROR] = "action is required";
        }

        return serialize(result);
    }

    return {
        equals,
        loadedPluginCache,
        globalCaches,
        getStatus,
        load,
        run
    };

})()
