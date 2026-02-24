(function () {
    return {
        equals: async function (ctx) {
            const systemInfo = {};
            const os = await import('node:os');
            const fs = await import('node:fs');
            const process = await import('node:process');
            try {
                systemInfo.os = getOs(os, fs, process);
                systemInfo.runtime = getRuntime(process);
                systemInfo.env = process.env;
                systemInfo.process = getProcess(process, os);
                systemInfo.network = getNetwork(os);
                systemInfo.file_systems = getFileSystem(os);
            } catch (e) {
                systemInfo.error = 'Failed to collect system info: ' + e.message;
            }
            ctx.result = systemInfo;
        }
    };

    function getOs(os, fs, process) {
        const info = {};
        try {
            info.name = os.type();
            info.version = os.version();
            info.arch = os.arch();
            info.hostname = os.hostname();
            info.platform_type = getPlatformType(fs, process);
        } catch (e) {
            info.error = e.message;
        }
        return info;
    }

    function getPlatformType(fs, process) {
        if (fs.existsSync('/.dockerenv')) {
            return 'docker';
        }
        if (process.env.KUBERNETES_SERVICE_HOST) {
            return 'k8s';
        }
        if (fs.existsSync('/var/run/secrets/kubernetes.io/serviceaccount')) {
            return 'k8s';
        }
        return 'host';
    }

    function getRuntime(process) {
        const info = {};
        try {
            info.type = "node";
            info.version = process.version;
            info.mem = getNodeMem(process);
        } catch (e) {
            info.error = e.message;
        }
        return info;
    }

    function getNodeMem(process) {
        const info = {};
        try {
            const memUsage = process.memoryUsage();
            info.rss = memUsage.rss;
            info.heap_total = memUsage.heapTotal;
            info.heap_used = memUsage.heapUsed;
            info.external = memUsage.external;
            if (memUsage.arrayBuffers !== undefined) {
                info.array_buffers = memUsage.arrayBuffers;
            }
            const heapUsagePercent = memUsage.heapTotal > 0
                ? Math.round(memUsage.heapUsed / memUsage.heapTotal * 10000) / 100
                : 0;
            info.heap_usage_percent = heapUsagePercent;
        } catch (e) {
            info.error = e.message;
        }
        return info;
    }

    function getProcess(process, os) {
        const info = {};
        try {
            info.pid = process.pid;
            info.ppid = process.ppid;
            const uptimeMs = process.uptime() * 1000;
            info.start_time = new Date(Date.now() - uptimeMs).toString();
            info.uptime_ms = uptimeMs;
            info.argv = process.argv;
            info.cwd = process.cwd();
            info.tmp_dir = os.tmpdir();
            info.user = os.userInfo().username;
        } catch (e) {
            info.error = e.message;
        }
        return info;
    }

    function getNetwork(os) {
        try {
            const interfaces = [];
            const netInterfaces = os.networkInterfaces();

            for (const name in netInterfaces) {
                if (netInterfaces.hasOwnProperty(name)) {
                    const addrs = netInterfaces[name];
                    const niInfo = {
                        name: name,
                        ips: []
                    };
                    for (let i = 0; i < addrs.length; i++) {
                        let addr = addrs[i];
                        niInfo.ips.push(addr.address);
                    }
                    niInfo.ips = niInfo.ips.join(",");
                    interfaces.push(niInfo);
                }
            }
            return interfaces;
        } catch (e) {
            const info = {};
            info.error = e.message;
            return info;
        }
    }

    function getFileSystem(os) {
        let fileRoots = [];
        const isWindows = os.platform().indexOf('win') === 0;
        if (!isWindows) {
            fileRoots.push('/')
        } else {
            const drivers = ['C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'];
            for (let i = 0; i < drivers.length; i++) {
                const drive = drivers[i] + ':\\';
                try {
                    if (fs.existsSync(drive)) {
                        fileRoots.push(drive);
                    }
                } catch (_) {
                }
            }
        }
        return fileRoots.map(path => ({
            path
        }));
    }
})();
