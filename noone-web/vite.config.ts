import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, type PluginOption } from "vite";
import devtoolsJson from "vite-plugin-devtools-json";

export default defineConfig(() => {
  const plugins: PluginOption[] = [tailwindcss(), devtoolsJson()];

  if (!process.env.VITEST) {
    plugins.splice(1, 0, reactRouter());
  }

  return {
    server: {
      proxy: {
        "/api": {
          target: process.env.VITE_API_PROXY_TARGET ?? "http://localhost:8888",
          changeOrigin: true,
        },
      },
    },
    resolve: {
      tsconfigPaths: true,
    },
    plugins,
    test: {
      environment: "jsdom",
      globals: true,
      include: ["app/**/*.test.{ts,tsx}"],
      setupFiles: ["./vitest.setup.ts"],
    },
  };
});
