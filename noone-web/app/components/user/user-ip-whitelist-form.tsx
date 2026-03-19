import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

export function UserIpWhitelistForm() {
  const [ipAddress, setIpAddress] = useState("");
  const [error, setError] = useState("");

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    if (!ipAddress.trim()) {
      event.preventDefault();
      setError("IP address is required");
      return;
    }

    setError("");
  };

  return (
    <form
      method="post"
      className="grid gap-4 rounded-xl border p-4 md:grid-cols-[1fr_auto]"
      onSubmit={handleSubmit}
    >
      <input type="hidden" name="intent" value="add" />
      <Field data-invalid={Boolean(error)}>
        <FieldLabel htmlFor="ipAddress">IP Address</FieldLabel>
        <Input
          id="ipAddress"
          name="ipAddress"
          value={ipAddress}
          onChange={(event) => setIpAddress(event.target.value)}
          placeholder="203.0.113.10"
          aria-invalid={Boolean(error) || undefined}
        />
        <FieldError>{error}</FieldError>
      </Field>
      <div className="flex items-end">
        <Button type="submit">Add entry</Button>
      </div>
    </form>
  );
}
