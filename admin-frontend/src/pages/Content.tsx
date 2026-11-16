import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { useGet, useInvalidate } from "../lib/hooks";
import { dateTime } from "../lib/format";
import { Badge, Button, Card, ErrorBox, Field, Input, Loading, PageHeader, StatusBadge, Textarea, useConfirm } from "../components/ui";

// Onboarding carousel + Company Profile content (spec 4a, 16.8, 15).
// Structured forms (not raw JSON) so a typo can't break the app; the server
// validates the shape again before saving.
export default function Content() {
  const [key, setKey] = useState<"onboarding" | "company-profile">("onboarding");
  return (
    <>
      <PageHeader title="Onboarding & company content" subtitle="Shown in the app; every publish is a new version" />
      <div className="mb-4 flex gap-2">
        {(["onboarding", "company-profile"] as const).map((k) => (
          <button key={k} onClick={() => setKey(k)} className={`rounded-full px-4 py-1.5 text-sm ${key === k ? "bg-primary text-white" : "bg-card"}`}>
            {k === "onboarding" ? "Onboarding carousel" : "Company profile"}
          </button>
        ))}
      </div>
      <Editor key={key} k={key} />
    </>
  );
}

function Editor({ k }: { k: "onboarding" | "company-profile" }) {
  const versions = useGet<any[]>(`/api/admin/content/${k}/versions`);
  const invalidate = useInvalidate();
  const confirm = useConfirm();
  const latest = versions.data?.[0];
  const [payload, setPayload] = useState<any>(null);
  const [summary, setSummary] = useState("");
  const [err, setErr] = useState<unknown>(null);
  useEffect(() => { if (latest) { setPayload(structuredClone(latest.payload)); setSummary(latest.status === "DRAFT" ? latest.changeSummary ?? "" : ""); } }, [latest]);
  if (versions.isLoading || !payload) return <Card><Loading /></Card>;

  const saveDraft = async () => {
    setErr(null);
    try {
      const r = latest?.status === "DRAFT"
        ? await api(`/api/admin/content/versions/${latest.id}`, { method: "PUT", body: { payload, changeSummary: summary } })
        : await api(`/api/admin/content/${k}/drafts`, { body: { payload, changeSummary: summary } });
      invalidate("/api/admin/content");
      return r;
    } catch (e) { setErr(e); throw e; }
  };
  const publish = () => confirm({
    title: "Publish content", summary: [["Content", k], ["Change", summary || "—"]],
    consequence: "The app shows this version from the next launch. The previous version stays in history.",
    confirmLabel: "Publish",
    run: async () => { const d = await saveDraft(); await api(`/api/admin/content/versions/${d.id}/publish`, { body: {} }); invalidate("/api/admin/content"); },
  });

  return (
    <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1fr_320px]">
      <Card title="Edit" actions={<><Button variant="secondary" onClick={() => saveDraft().catch(() => {})}>Save draft</Button><Button onClick={publish}>Publish…</Button></>}>
        <div className="space-y-4">
          <Field label="What changed (internal)"><Input value={summary} onChange={(e) => setSummary(e.target.value)} /></Field>
          {k === "onboarding" ? (
            <>
              {payload.slides.map((s: any, i: number) => (
                <div key={i} className="space-y-2 rounded-[14px] bg-background/60 p-4">
                  <div className="flex items-center justify-between"><b className="text-sm">Slide {i + 1}</b>
                    <button className="text-xs text-error disabled:opacity-40" disabled={payload.slides.length === 1}
                      onClick={() => setPayload({ ...payload, slides: payload.slides.filter((_: any, j: number) => j !== i) })}>Remove</button></div>
                  {(["eyebrow", "headline", "body", "imageUrl"] as const).map((f) => (
                    <Field key={f} label={f}>
                      {f === "body" ? <Textarea rows={3} value={s[f] ?? ""} onChange={(e) => upd(i, f, e.target.value)} />
                        : <Input value={s[f] ?? ""} onChange={(e) => upd(i, f, e.target.value || (f === "imageUrl" ? null : ""))} />}
                    </Field>
                  ))}
                </div>
              ))}
              {payload.slides.length < 5 && <Button variant="secondary" onClick={() => setPayload({ ...payload, slides: [...payload.slides, { eyebrow: "", headline: "", body: "", imageUrl: null }] })}>+ Add slide</Button>}
              <Field label="Button label"><Input value={payload.ctaLabel ?? ""} onChange={(e) => setPayload({ ...payload, ctaLabel: e.target.value })} /></Field>
            </>
          ) : (
            ["legalName", "registrationNumber", "registeredAddress", "supportEmail", "supportWhatsApp", "website", "about"].map((f) => (
              <Field key={f} label={f}>{f === "about" ? <Textarea rows={3} value={payload[f] ?? ""} onChange={(e) => setPayload({ ...payload, [f]: e.target.value })} />
                : <Input value={payload[f] ?? ""} onChange={(e) => setPayload({ ...payload, [f]: e.target.value })} />}</Field>
            ))
          )}
          <ErrorBox error={err} />
        </div>
      </Card>
      <Card title="History">
        <ul className="space-y-2 text-sm">
          {versions.data?.map((v) => (
            <li key={v.id} className="flex flex-wrap items-center gap-2">
              <b>v{v.versionNo}</b><StatusBadge status={v.status} />{v.current && <Badge tone="brand">live</Badge>}
              <span className="w-full text-xs text-muted">{v.changeSummary || "—"} · {dateTime(v.publishedAt ?? v.createdAt)}</span>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );

  function upd(i: number, f: string, v: string | null) {
    setPayload({ ...payload, slides: payload.slides.map((s: any, j: number) => (j === i ? { ...s, [f]: v } : s)) });
  }
}
