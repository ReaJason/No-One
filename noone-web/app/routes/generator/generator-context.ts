import { useOutletContext } from "react-router";
import type { Profile } from "@/types/profile";

export interface GeneratorContext {
  profiles: Profile[];
}

export function useGeneratorContext() {
  return useOutletContext<GeneratorContext>();
}
