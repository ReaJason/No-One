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
  godzillaPass: z.string().optional(),
  godzillaKey: z.string().optional(),
  headerName: z.string().optional(),
  headerValue: z.string().optional(),
  injectorClassName: z.string().optional(),
  packingMethod: z.string().min(1),
  shrink: z.boolean().optional(),
  lambdaSuffix: z.boolean().optional(),
  probe: z.boolean().optional(),
  profileId: z.string().optional(),
});

export type MemShellFormSchema = z.infer<typeof memShellFormSchema>;
