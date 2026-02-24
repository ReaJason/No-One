(function () {
    return {
        equals: async function (ctx) {
            const result = {};
            try {

            } catch (e) {
                result.error = 'Failed: ' + e.message;
            }
            ctx.result = result;
        }
    };
})();
