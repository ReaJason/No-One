import { Shuffle } from "lucide-react";
import { Fragment, useState } from "react";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";

export function OptionalClassFormField() {
  const [useRandomClassName, setUseRandomClassName] = useState(true);
  const [shellClassName, setShellClassName] = useState("");
  const [injectorClassName, setInjectorClassName] = useState("");

  const handleToggleRandomClass = (checked: boolean) => {
    setUseRandomClassName(checked);
    if (checked) {
      setShellClassName("");
      setInjectorClassName("");
    }
  };

  return (
    <Fragment>
      <div className="pt-2 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-sm font-medium">
          <Shuffle className="h-4 w-4" />
          <span>Random Class Name</span>
        </div>
        <Switch
          id="randomClassName"
          checked={useRandomClassName}
          onCheckedChange={handleToggleRandomClass}
        />
      </div>

      {!useRandomClassName && (
        <>
          <input type="hidden" name="shellClassName" value={shellClassName} />
          <Field className="gap-1">
            <FieldLabel htmlFor="shellClassName">
              Shell Class Name (optional)
            </FieldLabel>
            <Input
              id="shellClassName"
              value={shellClassName}
              onChange={(e) => setShellClassName(e.target.value)}
              placeholder="Enter class name"
            />
          </Field>
        </>
      )}
      {!useRandomClassName && (
        <>
          <input
            type="hidden"
            name="injectorClassName"
            value={injectorClassName}
          />
          <Field className="gap-1">
            <FieldLabel htmlFor="injectClassName">
              Injector Class Name (optional)
            </FieldLabel>
            <Input
              id="injectClassName"
              value={injectorClassName}
              onChange={(e) => setInjectorClassName(e.target.value)}
              placeholder="Enter class name"
            />
          </Field>
        </>
      )}
    </Fragment>
  );
}
