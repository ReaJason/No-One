import * as yup from "yup";

export const memShellFormSchema = yup.object({
  server: yup.string().required().min(1),
  serverVersion: yup.string().required().min(1),
  targetJdkVersion: yup.string().optional(),
  debug: yup.boolean().optional(),
  byPassJavaModule: yup.boolean().optional(),
  staticInitialize: yup.boolean().optional(),
  shellClassName: yup.string().optional(),
  shellTool: yup.string().required().min(1),
  shellType: yup.string().required().min(1),
  urlPattern: yup.string().optional(),
  godzillaPass: yup.string().optional(),
  godzillaKey: yup.string().optional(),
  headerName: yup.string().optional(),
  headerValue: yup.string().optional(),
  injectorClassName: yup.string().optional(),
  packingMethod: yup.string().required().min(1),
  shrink: yup.boolean().optional(),
  lambdaSuffix: yup.boolean().optional(),
  probe: yup.boolean().optional(),
  profileId: yup.string().optional(),
});

export type MemShellFormSchema = yup.InferType<typeof memShellFormSchema>;
