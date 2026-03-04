import { redirect, type LoaderFunctionArgs } from "react-router";

export function loader({ params }: LoaderFunctionArgs) {
  return redirect(`/shells/${params.shellId}/info`);
}
