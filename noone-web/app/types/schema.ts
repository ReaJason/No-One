import { z } from "zod";

export const memShellFormSchema = z.object({
  server: z.string().min(1),
  serverVersion: z.string().min(1),
  targetJdkVersion: z.string().optional(),
  debug: z.boolean().optional(),
  byPassJavaModule: z.boolean().optional(),
  staticInitialize: z.boolean().optional(),
  shellClassName: z.string().optional(),
  shellTool: z.string().min(1),
  shellType: z.string().min(1),
  urlPattern: z.string().optional(),
  injectorClassName: z.string().optional(),
  packingMethod: z.string().min(1),
  shrink: z.boolean().optional(),
  lambdaSuffix: z.boolean().optional(),
  probe: z.boolean().optional(),
  probeMethod: z.enum(["ResponseBody", "ResponseHeader"]).optional(),
  respHeaderName: z.string().optional(),
  obfuscationEnabled: z.boolean().optional(),
  obfuscationScope: z.enum(["ALL", "MEMSHELL_INJECTOR", "INJECTOR", "MEMSHELL"]).optional(),
  coreProfileId: z.string().optional(),
  loaderProfileId: z.string().optional(),
  staging: z.boolean().optional(),
});

export type MemShellFormSchema = z.infer<typeof memShellFormSchema>;
