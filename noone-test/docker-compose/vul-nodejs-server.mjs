(async () => {
    const http = await import('node:http');
    const fs = await import('node:fs');
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
        const text = content.toString('utf8').trim();
        try {
            const parsed = JSON.parse(text);
            if (parsed != null && typeof parsed.hello === 'string') {
                return parsed.hello;
            }
        } catch (e) {
            // fall through to template slicing
        }
        return text.slice(11, text.length - 2);
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

    const NOONE_CORE_CODE = fs.readFileSync(
        new URL('./nodejs-core.mjs', import.meta.url),
        'utf8'
    );

    function initNoOneCore() {
        if (!global.NoOneCore) {
            global.NoOneCore = new Function('return ' + NOONE_CORE_CODE)();
        }
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
