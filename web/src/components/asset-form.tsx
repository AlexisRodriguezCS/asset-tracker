"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Asset, AssetCondition, AssetType } from "@/lib/types";

const TYPES: AssetType[] = [
  "LAPTOP",
  "TABLET",
  "PHONE",
  "MONITOR",
  "DOCK",
  "CHARGER",
  "CABLE",
  "HOTSPOT",
  "PERIPHERAL",
  "OTHER",
];
const CONDITIONS: AssetCondition[] = ["NEW", "GOOD", "FAIR", "POOR", "DAMAGED"];

type Prefill = {
  type?: string;
  assetTag?: string;
  make?: string;
  model?: string;
  category?: string;
  /** "PERSON:1" / "LOCATION:4" - check the new unit straight out to that holder. */
  reassignTo?: string;
};

type Props =
  | {
      mode: "create";
      clientId: number;
      prefill?: Prefill;
      categories?: string[];
      onDone?: () => void;
    }
  | {
      mode: "edit";
      asset: Asset;
      categories?: string[];
      onDone?: () => void;
    };

/** Create or edit an asset. Writes go through the authenticated BFF proxy. */
export function AssetForm(props: Props) {
  const router = useRouter();
  const edit = props.mode === "edit";
  const a = edit ? props.asset : undefined;
  const pf = edit ? undefined : props.prefill;

  const [type, setType] = useState<string>(pf?.type ?? "LAPTOP");
  const [assetTag, setAssetTag] = useState(pf?.assetTag ?? a?.assetTag ?? "");
  const [serialNumber, setSerialNumber] = useState("");
  const [make, setMake] = useState(pf?.make ?? a?.make ?? "");
  const [model, setModel] = useState(pf?.model ?? a?.model ?? "");
  const [condition, setCondition] = useState<string>(a?.condition ?? "GOOD");
  const [category, setCategory] = useState(pf?.category ?? a?.category ?? "");
  const [costUsd, setCostUsd] = useState(
    a?.purchaseCostCents != null ? String(a.purchaseCostCents / 100) : "",
  );
  const [purchaseDate, setPurchaseDate] = useState(
    a?.purchaseDate?.slice(0, 10) ?? "",
  );
  const [deployedOn, setDeployedOn] = useState(
    a?.deployedOn?.slice(0, 10) ?? "",
  );
  const [warrantyEndsOn, setWarrantyEndsOn] = useState(
    a?.warrantyEndsOn?.slice(0, 10) ?? "",
  );
  const [notes, setNotes] = useState(a?.notes ?? "");

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function explain(res: Response) {
    if (res.status === 409) {
      return "A live asset of that type already uses this tag.";
    }
    const b = await res.json().catch(() => null);
    return b?.message ?? "Save failed.";
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);

    const shared = {
      make: trimmed(make),
      model: trimmed(model),
      condition,
      category: trimmed(category),
      deployedOn: trimmed(deployedOn),
      warrantyEndsOn: trimmed(warrantyEndsOn),
      notes: trimmed(notes),
    };

    if (edit) {
      const res = await fetch(`/api/bff/assets/${a!.id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(shared),
      });
      setBusy(false);
      if (!res.ok) {
        setError(await explain(res));
        return;
      }
      props.onDone?.();
      router.refresh();
      return;
    }

    const cost = Number(costUsd);
    const res = await fetch("/api/bff/assets", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        clientId: props.clientId,
        type,
        serialNumber: trimmed(serialNumber),
        assetTag: trimmed(assetTag),
        purchaseDate: trimmed(purchaseDate),
        purchaseCostCents:
          costUsd.trim() !== "" && Number.isFinite(cost)
            ? Math.round(cost * 100)
            : undefined,
        ...shared,
      }),
    });
    if (!res.ok) {
      setBusy(false);
      setError(await explain(res));
      return;
    }
    const created = (await res.json()) as { id: number };

    if (pf?.reassignTo) {
      const [holderType, holderId] = pf.reassignTo.split(":");
      await fetch("/api/bff/assignments", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          clientId: props.clientId,
          assetId: created.id,
          holderType,
          holderId: Number(holderId),
        }),
      });
    }
    setBusy(false);
    router.push(`/assets/${created.id}`);
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        {!edit && (
          <>
            <Select
              label="Type"
              value={type}
              onChange={setType}
              options={TYPES}
            />
            <Field label="Asset tag">
              <Input
                required
                value={assetTag}
                onChange={(e) => setAssetTag(e.target.value)}
                placeholder="ACME-L-014"
              />
            </Field>
            <Field label="Serial number">
              <Input
                required
                value={serialNumber}
                onChange={(e) => setSerialNumber(e.target.value)}
              />
            </Field>
          </>
        )}
        <Field label="Make">
          <Input value={make} onChange={(e) => setMake(e.target.value)} />
        </Field>
        <Field label="Model">
          <Input value={model} onChange={(e) => setModel(e.target.value)} />
        </Field>
        <Select
          label="Condition"
          value={condition}
          onChange={setCondition}
          options={CONDITIONS}
        />
        <Field label="Category">
          <Input
            list="asset-categories"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            placeholder="e.g. Field kit, Loaner pool"
          />
          <datalist id="asset-categories">
            {(props.categories ?? []).map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
        </Field>
        {!edit && (
          <>
            <Field label="Purchase cost (USD)">
              <Input
                type="number"
                min="0"
                step="0.01"
                value={costUsd}
                onChange={(e) => setCostUsd(e.target.value)}
              />
            </Field>
            <Field label="Purchased">
              <Input
                type="date"
                value={purchaseDate}
                onChange={(e) => setPurchaseDate(e.target.value)}
              />
            </Field>
          </>
        )}
        <Field label="Deployed on">
          <Input
            type="date"
            value={deployedOn}
            onChange={(e) => setDeployedOn(e.target.value)}
          />
        </Field>
        <Field label="Warranty ends">
          <Input
            type="date"
            value={warrantyEndsOn}
            onChange={(e) => setWarrantyEndsOn(e.target.value)}
          />
        </Field>
      </div>

      <Field label="Notes">
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={2}
          className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:border-primary"
        />
      </Field>

      {error && <p className="text-sm text-destructive">{error}</p>}

      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? "Saving…" : edit ? "Save changes" : "Add asset"}
        </Button>
      </div>
    </form>
  );
}

function trimmed(v: string): string | undefined {
  return v.trim() === "" ? undefined : v.trim();
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </span>
      {children}
    </label>
  );
}

function Select({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: readonly string[];
}) {
  return (
    <Field label={label}>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
      >
        {options.map((o) => (
          <option key={o} value={o}>
            {o.charAt(0) + o.slice(1).toLowerCase()}
          </option>
        ))}
      </select>
    </Field>
  );
}
