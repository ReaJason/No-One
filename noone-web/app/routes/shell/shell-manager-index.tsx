import { redirect, type LoaderFunctionArgs } from "react-router";

export function clientLoader({ params }: LoaderFunctionArgs) {
  return redirect(`/shells/${params.shellId}/info`);
}
