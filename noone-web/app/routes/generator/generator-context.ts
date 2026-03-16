import type { Profile } from "@/types/profile";

import { useOutletContext } from "react-router";

export interface GeneratorContext {
  profiles: Promise<Profile[]>;
}

export function useGeneratorContext() {
  return useOutletContext<GeneratorContext>();
}
