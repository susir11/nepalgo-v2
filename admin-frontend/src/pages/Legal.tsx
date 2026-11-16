import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { api } from "../lib/api";
import { useGet, useInvalidate } from "../lib/hooks";
import { dateTime } from "../lib/format";
import { Badge, Button, Card, Empty, ErrorBox, Field, Input, Loading, PageHeader, StatusBadge, Textarea, useConfirm } from "../components/ui";

// Policy & Privacy Content Manager (spec 10/15): version history, draft ->
// preview -> publish. A published version is never edited (API + DB trigger).
export default function Legal() {
  const [type, setType] = useState<"terms" | "privacy">("terms");
  const [selected, setSelected] = useState<number | null>(null);
  const versions = useGet<any[]>(`/api/admin/legal/${type}/versions`);
  const invalidate = useInvalidate();
  const [err, setErr] = useState<unknown>(null);

  useEffect(() => { setSelected(null); }, [type]);
  useEffect(() => {
    if (!selected && versions.data?.length) setSelected((versions.data.find((v) => v.status === "DRAFT") ?? versions.data.find((v) => v.current) ?? versions.data[0]).id);
  }, [versions.data, selected]);

  const newDraft = async () => {
    setErr(null);
    try {
      const d = await api(`/api/admin/legal/${type}/drafts`, { body: { changeSummary: "" } });
      invalidate("/api/admin/legal"); setSelected(d.id);
    } catch (e) { setErr(e); }
  };

  return (
    <>
      <PageHeader title="Policy content" subtitle="Terms & Conditions and Privacy Policy, versioned"
        actions={<Button onClick={newDraft}>+ New draft (copy of current)</Button>} />
      <div className="mb-4 flex gap-2">
        {(["terms", "privacy"] as const).map((t) => (
          <button key={t} onClick={() => setType(t)} className={`rounded-full px-4 py-1.5 text-sm ${type === t ? "bg-primary text-white" : "bg-card"}`}>
            {t === "terms" ? "Terms & Conditions" : "Privacy Policy"}
          </button>
        ))}
      </div>
      <ErrorBox error={err} />
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[300px_1fr]">
        <Card title="Versions">
          {versions.isLoading ? <Loading /> : !versions.data?.length ? <Empty>No versions.</Empty> : (
            <ul className="space-y-1">
              {versions.data.map((v) => (
                <li key={v.id}>
                  <button onClick={() => setSelected(v.id)} className={`w-full rounded-[14px] px-3 py-2 text-left text-sm ${selected === v.id ? "bg-primary-tint" : "hover:bg-background"}`}>
                    <div className="flex items-center gap-2"><b>{v.versionLabel}</b><StatusBadge status={v.status} />{v.current && <Badge tone="brand">current</Badge>}</div>
                    <div className="text-xs text-muted">{v.publishedAt ? `published ${dateTime(v.publishedAt)}` : `draft ${dateTime(v.updatedAt)}`}</div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>
        {selected ? <DocEditor key={selected} id={selected} type={type} onGone={() => setSelected(null)} /> : <Card><Empty>Select a version.</Empty></Card>}
      </div>
    </>
  );
}

function DocEditor({ id, type, onGone }: { id: number; type: string; onGone: () => void }) {
  const doc = useGet<any>(`/api/admin/legal/documents/${id}`);
  const invalidate = useInvalidate();
  const confirm = useConfirm();
  const { register, handleSubmit, reset, formState } = useForm<any>();
  const [err, setErr] = useState<unknown>(null);
  useEffect(() => { if (doc.data) reset(doc.data); }, [doc.data, reset]);
  if (doc.isLoading || !doc.data) return <Card><Loading /></Card>;
  const d = doc.data;
  const draft = d.status === "DRAFT";
  const terms = type === "terms";

  const save = handleSubmit(async (f) => {
    setErr(null);
    try {
      await api(`/api/admin/legal/documents/${id}`, { method: "PUT", body: { title: f.title, fullText: f.fullText, changeSummary: f.changeSummary,
        ...(terms ? { cancellationMatrix: f.cancellationMatrix, forceMajeureWeather: f.forceMajeureWeather, identityLiability: f.identityLiability } : {}) } });
      invalidate("/api/admin/legal");
    } catch (e) { setErr(e); }
  });
  const publish = () => confirm({
    title: `Publish ${d.versionLabel}`, danger: true,
    summary: [["Document", terms ? "Terms & Conditions" : "Privacy Policy"], ["Version", d.versionLabel], ["What changed", d.changeSummary || "—"]],
    consequence: terms
      ? "Becomes the binding Terms immediately. Customers mid-checkout must re-agree before paying; earlier consents stay linked to the version they saw. This version can never be edited afterwards."
      : "Becomes the Privacy Policy shown everywhere immediately. This version can never be edited afterwards.",
    confirmLabel: "Publish now",
    run: () => api(`/api/admin/legal/documents/${id}/publish`, { body: {} }).then(() => invalidate("/api/admin/legal")),
  });
  const del = () => confirm({
    title: "Delete draft", summary: [["Version", d.versionLabel]], consequence: "The draft is discarded. Published versions are not affected.",
    confirmLabel: "Delete draft", danger: true,
    run: () => api(`/api/admin/legal/documents/${id}`, { method: "DELETE" }).then(() => { invalidate("/api/admin/legal"); onGone(); }),
  });

  return (
    <Card title={<span className="flex items-center gap-2">{d.versionLabel} <StatusBadge status={d.status} /></span>}
      actions={draft && <><Button variant="ghost" onClick={del}>Delete draft</Button><Button variant="secondary" busy={formState.isSubmitting} onClick={save}>Save draft</Button><Button onClick={publish}>Publish…</Button></>}>
      {!draft && <p className="mb-3 rounded-[14px] bg-background/60 px-4 py-2 text-xs text-muted">Published {dateTime(d.publishedAt)} by staff #{d.publishedBy} · fingerprint <span className="mono">{d.contentSha256?.slice(0, 20)}…</span> · read-only</p>}
      <form className="space-y-3" onSubmit={save}>
        <fieldset disabled={!draft} className="space-y-3">
          <Field label="What changed (internal note)"><Input {...register("changeSummary")} placeholder="e.g. Filled in company legal name" /></Field>
          <Field label="Title"><Input {...register("title")} /></Field>
          {terms && <>
            <Field label="Policy Review card 1 — Tiered Cancellation Matrix"><Textarea rows={6} className="mono" {...register("cancellationMatrix")} /></Field>
            <Field label="Policy Review card 2 — Force-Majeure & Himalayan Weather Protocol"><Textarea rows={5} className="mono" {...register("forceMajeureWeather")} /></Field>
            <Field label="Policy Review card 3 — Absolute Identity Liability"><Textarea rows={5} className="mono" {...register("identityLiability")} /></Field>
          </>}
          <Field label="Full text"><Textarea rows={18} className="mono" {...register("fullText")} /></Field>
        </fieldset>
        <ErrorBox error={err} />
      </form>
    </Card>
  );
}
