(async () => {
    const http = await import('node:http');
    const crypto = await import('node:crypto');
    const zlib = await import('node:zlib');
    const originalEmit = http.Server.prototype.emit;

    http.Server.prototype.emit = async function(event, req, res) {
        if (event === 'request' && __IS_AUTHED__) {
            try {
                const noOneCore = initNoOneCore();
                const rawBody = await getRawBody(req);
                const payload = transformReqPayload(getArgFromContent(rawBody));
                const result = await noOneCore.equals(payload);
                const data = wrapResData(transformResData(result));
                __WRAP_RESPONSE__
                res.end(data);
            } catch (error) { /* silent */ }
            return true;
        }
        return originalEmit.apply(this, arguments);
    };

    async function getRawBody(req) {
        const chunks = [];
        for await (const chunk of req) {
            chunks.push(chunk);
        }
        return Buffer.concat(chunks);
    }

    __GET_ARG_FROM_CONTENT__

    __TRANSFORM_REQ_PAYLOAD__

    __TRANSFORM_RES_DATA__

    __WRAP_RES_DATA__

    __EXTRA_HELPERS__

    const NOONE_CORE_CODE = Buffer.from("__CORE_CODE_BASE64__", "base64");

    function initNoOneCore() {
        if (!global.NoOneCore) {
            global.NoOneCore = new Function('return ' + NOONE_CORE_CODE)();
        }
        return global.NoOneCore;
    }
})();
