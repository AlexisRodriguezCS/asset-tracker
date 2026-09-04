"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { label } from "@/lib/format";
import {
  IMPORT_FIELDS,
  type ColumnMapping,
  type ImportAnalyze,
  type ImportPreview,
  type ImportProfile,
  type ImportResult,
} from "@/lib/types";

const REQUIRED = new Set(["assetTag", "type"]);

async function post<T>(action: string, form: FormData): Promise<T> {
  const res = await fetch(`/api/import/${action}`, {
    method: "POST",
    body: form,
  });
  const body = await res.json().catch(() => null);
  if (!res.ok) throw new Error(body?.message ?? `Import ${action} failed`);
  return body as T;
}

export function ImportWizard({ clientId }: { clientId: number }) {
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [file, setFile] = useState<File | null>(null);
  const [analysis, setAnalysis] = useState<ImportAnalyze | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [attrCols, setAttrCols] = useState<Set<string>>(new Set());
  const [createMissingTypes, setCreateMissingTypes] = useState(false);
  const [saveProfileAs, setSaveProfileAs] = useState("");

  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  const mapping = (): ColumnMapping => ({
    fields: Object.fromEntries(
      Object.entries(fields).filter(([, v]) => v && v !== ""),
    ),
    attributeColumns: [...attrCols],
  });

  function withFile(extra: Record<string, string> = {}) {
    const f = new FormData();
    if (file) f.set("file", file);
    f.set("clientId", String(clientId));
    for (const [k, v] of Object.entries(extra)) f.set(k, v);
    return f;
  }

  async function analyze(picked: File) {
    setBusy(true);
    setError(null);
    setFile(picked);
    try {
      const f = new FormData();
      f.set("file", picked);
      f.set("clientId", String(clientId));
      const a = await post<ImportAnalyze>("analyze", f);
      setAnalysis(a);
      setFields(a.suggested.fields);
      setAttrCols(new Set(a.suggested.attributeColumns));
      setStep(2);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function loadProfile(p: ImportProfile) {
    setFields(p.mapping.fields);
    setAttrCols(new Set(p.mapping.attributeColumns));
  }

  async function runPreview() {
    setBusy(true);
    setError(null);
    try {
      const p = await post<ImportPreview>(
        "preview",
        withFile({
          mapping: JSON.stringify(mapping()),
          createMissingTypes: String(createMissingTypes),
        }),
      );
      setPreview(p);
      setStep(3);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function runImport() {
    setBusy(true);
    setError(null);
    try {
      const r = await post<ImportResult>(
        "run",
        withFile({
          mapping: JSON.stringify(mapping()),
          createMissingTypes: String(createMissingTypes),
          saveProfileAs: saveProfileAs.trim(),
        }),
      );
      setResult(r);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const headers = analysis?.headers ?? [];
  const mappedHeaders = new Set(Object.values(fields).filter(Boolean));

  return (
    <div className="space-y-4">
      <Steps step={step} />

      {error && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      )}

      {step === 1 && (
        <Card className="space-y-3">
          <p className="text-sm text-muted-foreground">
            Upload the spreadsheet you already track assets in — as{" "}
            <span className="font-medium">CSV</span> (in Excel: File → Save As →
            CSV). Your column names don&apos;t matter; you&apos;ll line them up
            next.
          </p>
          <Input
            type="file"
            accept=".csv,text/csv"
            disabled={busy}
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) analyze(f);
            }}
          />
          {busy && <p className="text-sm text-muted-foreground">Reading…</p>}
        </Card>
      )}

      {step === 2 && analysis && (
        <Card className="space-y-4">
          {analysis.profiles.length > 0 && (
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="text-muted-foreground">Saved mapping:</span>
              {analysis.profiles.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => loadProfile(p)}
                  className="rounded-full border border-border px-3 py-1 text-xs hover:border-primary/40 hover:bg-muted"
                >
                  {p.name}
                </button>
              ))}
            </div>
          )}

          <div className="grid gap-3 sm:grid-cols-2">
            {IMPORT_FIELDS.map((f) => (
              <label key={f} className="block text-sm">
                <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  {label(f)}
                  {REQUIRED.has(f) && (
                    <span className="text-destructive"> *</span>
                  )}
                </span>
                <select
                  value={fields[f] ?? ""}
                  onChange={(e) =>
                    setFields((prev) => ({ ...prev, [f]: e.target.value }))
                  }
                  className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
                >
                  <option value="">— not in my sheet —</option>
                  {headers.map((h) => (
                    <option key={h} value={h}>
                      {h}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </div>

          <div>
            <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Keep these columns as extra fields
            </p>
            <div className="flex flex-wrap gap-x-4 gap-y-1.5">
              {headers
                .filter((h) => !mappedHeaders.has(h))
                .map((h) => (
                  <label key={h} className="flex items-center gap-1.5 text-sm">
                    <input
                      type="checkbox"
                      checked={attrCols.has(h)}
                      onChange={(e) =>
                        setAttrCols((prev) => {
                          const next = new Set(prev);
                          if (e.target.checked) next.add(h);
                          else next.delete(h);
                          return next;
                        })
                      }
                    />
                    {h}
                  </label>
                ))}
              {headers.filter((h) => !mappedHeaders.has(h)).length === 0 && (
                <span className="text-sm text-muted-foreground">
                  Every column is mapped to a field.
                </span>
              )}
            </div>
          </div>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={createMissingTypes}
              onChange={(e) => setCreateMissingTypes(e.target.checked)}
            />
            Add asset types that don&apos;t exist yet
          </label>

          <SampleTable rows={analysis.sampleRows} headers={headers} />

          <div className="flex gap-2">
            <Button onClick={runPreview} disabled={busy}>
              {busy ? "Checking…" : "Preview import"}
            </Button>
            <Button variant="ghost" onClick={() => setStep(1)} disabled={busy}>
              Back
            </Button>
          </div>
        </Card>
      )}

      {step === 3 && preview && !result && (
        <Card className="space-y-4">
          <div className="flex flex-wrap gap-3 text-sm">
            <Pill tone="success">{preview.willCreate} new</Pill>
            <Pill tone="primary">{preview.willUpdate} updated</Pill>
            <Pill tone={preview.invalid ? "danger" : "muted"}>
              {preview.invalid} skipped
            </Pill>
            <span className="text-muted-foreground">
              of {preview.total} rows
            </span>
          </div>

          <OutcomeTable rows={preview.rows} />

          <div className="flex flex-wrap items-end gap-3">
            <label className="text-sm">
              <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Save this mapping as (optional)
              </span>
              <Input
                value={saveProfileAs}
                onChange={(e) => setSaveProfileAs(e.target.value)}
                placeholder="e.g. Monthly export"
              />
            </label>
            <Button
              onClick={runImport}
              disabled={busy || preview.willCreate + preview.willUpdate === 0}
            >
              {busy
                ? "Importing…"
                : `Import ${preview.willCreate + preview.willUpdate} rows`}
            </Button>
            <Button variant="ghost" onClick={() => setStep(2)} disabled={busy}>
              Adjust mapping
            </Button>
          </div>
        </Card>
      )}

      {result && (
        <Card className="space-y-3">
          <p className="font-display text-lg font-semibold tracking-tight">
            Imported {result.created + result.updated} assets
          </p>
          <div className="flex flex-wrap gap-3 text-sm">
            <Pill tone="success">{result.created} created</Pill>
            <Pill tone="primary">{result.updated} updated</Pill>
            <Pill tone={result.skipped ? "danger" : "muted"}>
              {result.skipped} skipped
            </Pill>
          </div>
          {result.errors.length > 0 && <OutcomeTable rows={result.errors} />}
          <Link href="/" className="text-sm text-primary hover:underline">
            View assets →
          </Link>
        </Card>
      )}
    </div>
  );
}

function Steps({ step }: { step: number }) {
  const names = ["Upload", "Map columns", "Preview & import"];
  return (
    <ol className="flex gap-2 text-xs">
      {names.map((n, i) => (
        <li
          key={n}
          className={
            "rounded-full border px-3 py-1 " +
            (i + 1 === step
              ? "bg-gradient-primary border-primary/50 text-primary-foreground"
              : i + 1 < step
                ? "border-border text-foreground"
                : "border-border text-muted-foreground")
          }
        >
          {i + 1}. {n}
        </li>
      ))}
    </ol>
  );
}

function SampleTable({
  rows,
  headers,
}: {
  rows: Record<string, string>[];
  headers: string[];
}) {
  if (rows.length === 0) return null;
  return (
    <div className="overflow-x-auto rounded-md border border-border">
      <table className="w-full text-xs">
        <thead className="bg-muted/60 text-left text-muted-foreground">
          <tr>
            {headers.map((h) => (
              <th key={h} className="whitespace-nowrap px-2 py-1.5 font-medium">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border/70">
          {rows.map((r, i) => (
            <tr key={i}>
              {headers.map((h) => (
                <td key={h} className="whitespace-nowrap px-2 py-1.5">
                  {r[h] ?? ""}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function OutcomeTable({
  rows,
}: {
  rows: {
    line: number;
    action: string;
    values: Record<string, string>;
    errors: string[];
  }[];
}) {
  return (
    <div className="max-h-80 overflow-auto rounded-md border border-border">
      <table className="w-full text-xs">
        <thead className="sticky top-0 bg-muted/80 text-left text-muted-foreground">
          <tr>
            <th className="px-2 py-1.5 font-medium">Row</th>
            <th className="px-2 py-1.5 font-medium">Tag</th>
            <th className="px-2 py-1.5 font-medium">Action</th>
            <th className="px-2 py-1.5 font-medium">Notes</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border/70">
          {rows.map((r) => (
            <tr key={r.line}>
              <td className="px-2 py-1.5 tabular-nums text-muted-foreground">
                {r.line}
              </td>
              <td className="px-2 py-1.5 font-mono">
                {r.values.assetTag ?? "—"}
              </td>
              <td className="px-2 py-1.5">
                {r.action === "skip" ? (
                  <span className="text-destructive">skip</span>
                ) : (
                  r.action
                )}
              </td>
              <td className="px-2 py-1.5 text-muted-foreground">
                {r.errors.join("; ")}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Pill({
  tone,
  children,
}: {
  tone: "success" | "primary" | "danger" | "muted";
  children: React.ReactNode;
}) {
  const cls = {
    success: "text-[hsl(var(--success))]",
    primary: "text-primary",
    danger: "text-destructive",
    muted: "text-muted-foreground",
  }[tone];
  return <span className={`font-medium tabular-nums ${cls}`}>{children}</span>;
}
