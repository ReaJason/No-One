import type { Config } from "@react-router/dev/config";
import { vercelPreset } from "@vercel/react-router/vite";

export default {
  ssr: true,
  future: {
    v8_middleware: true,
  },
  presets: [vercelPreset()],
} satisfies Config;
