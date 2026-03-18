import { z } from "zod";

export const pluginEditSchema = z.object({
  name: z.string().trim().min(1, "Name is required"),
  description: z.string().trim().optional().default(""),
  author: z.string().trim().optional().default(""),
  type: z.string().trim().min(1, "Type is required"),
  runMode: z.string().trim().optional().default(""),
});

export type PluginEditFormValues = z.infer<typeof pluginEditSchema>;

export type PluginEditActionData = {
  errors?: Record<string, string>;
  success?: boolean;
};
