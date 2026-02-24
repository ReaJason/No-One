(async function() {
    const fs = await import("fs")
    let code = fs.readFileSync("system-info.mjs");
    const x = new Function("return " + code)();
    const result = {}
    console.log(x)
    await x.equals(result)
    console.log(JSON.stringify(result));
})()