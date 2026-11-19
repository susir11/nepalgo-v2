import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useParams } from "react-router";
import { api } from "../lib/api";
import { useGet, useInvalidate } from "../lib/hooks";
import { Button, Card, ErrorBox, Field, Input, Loading, PageHeader, Textarea, useConfirm, useToast } from "../components/ui";
import { DayListEditor, daysValid, toApiDays, toDays, type Day } from "../components/DayListEditor";

export default function DestinationEdit() {
  const id = Number(useParams().id);
  const nav = useNavigate();
  const confirm = useConfirm();
  const toast = useToast();
  const invalidate = useInvalidate();
  const d = useGet<any>(`/api/destinations/${id}`);
  const { register, handleSubmit, reset, formState } = useForm<any>();
  const [error, setError] = useState<unknown>(null);
  const [days, setDays] = useState<Day[]>([]);
  const [dayErr, setDayErr] = useState<unknown>(null);
  const [savingDays, setSavingDays] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [photoUrl, setPhotoUrl] = useState("");
  const [importing, setImporting] = useState(false);
  const [photoErr, setPhotoErr] = useState<unknown>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!d.data) return;
    reset({ name: d.data.name, region: d.data.region, type: d.data.type, description: d.data.description,
      bestSeason: d.data.bestSeason ?? "", basePriceUsd: d.data.basePriceUsd, permitNotes: d.data.permitNotes ?? "" });
    setDays(toDays(d.data.defaultItinerary?.days));
  }, [d.data, reset]);

  if (d.isLoading) return <Loading />;
  if (!d.data) return <ErrorBox error={d.error ?? new Error("Not found")} />;

  const saveDetails = handleSubmit(async (f) => {
    setError(null);
    try {
      await api(`/api/admin/destinations/${id}`, { method: "PUT", body: { ...f, basePriceUsd: Number(f.basePriceUsd) } });
      invalidate("/api/destinations"); toast("Destination saved");
    } catch (e) { setError(e); }
  });

  const remove = () => confirm({
    title: "Delete destination", danger: true,
    summary: [["Destination", d.data.name], ["Itinerary days", String(d.data.defaultItinerary?.days?.length ?? 0)]],
    consequence: "Removes it from the catalogue. Existing bookings keep their own itinerary snapshot and are not affected.",
    confirmLabel: "Delete",
    run: () => api(`/api/admin/destinations/${id}`, { method: "DELETE" }).then(() => { invalidate("/api/destinations"); nav("/destinations"); }),
  });

  const saveDays = async () => {
    setDayErr(null); setSavingDays(true);
    // Day numbers come from the ORDER on screen: drag a day and it renumbers.
    const body = { tier: "DEFAULT", days: toApiDays(days) };
    try {
      const v = d.data.defaultItinerary;
      await api(`/api/admin/destinations/${id}/itinerary-versions${v ? `/${v.id}` : ""}`, { method: v ? "PUT" : "POST", body });
      invalidate("/api/destinations"); toast("Itinerary saved");
    } catch (e) { setDayErr(e); } finally { setSavingDays(false); }
  };

  // "Add by URL": the server downloads the picture and runs it through the same
  // resize/strip pipeline as an upload, so the app never depends on someone else's site.
  const importFromUrl = async () => {
    setPhotoErr(null); setImporting(true);
    try {
      await api(`/api/admin/destinations/${id}/images/from-url`, { method: "POST", body: { url: photoUrl.trim() } });
      setPhotoUrl(""); invalidate("/api/destinations"); toast("Photo added");
    } catch (e) { setPhotoErr(e); } finally { setImporting(false); }
  };

  const removePhoto = (img: any, index: number) => confirm({
    title: "Remove photo", danger: true,
    summary: [["Destination", d.data.name], ["Photo", index === 0 ? "1 (cover)" : String(index + 1)]],
    consequence: "Removed from the app straight away. Bookings keep their own itinerary snapshot; no booking is affected.",
    confirmLabel: "Remove",
    run: () => api(`/api/admin/destinations/${id}/images/${img.id}`, { method: "DELETE" }).then(() => invalidate("/api/destinations")),
  });

  const upload = async (file: File) => {
    setUploading(true);
    const form = new FormData(); form.append("file", file);
    setPhotoErr(null);
    try { await api(`/api/admin/destinations/${id}/images`, { form, method: "POST" }); invalidate("/api/destinations"); toast("Photo added"); }
    catch (e) { setPhotoErr(e); } finally { setUploading(false); if (fileRef.current) fileRef.current.value = ""; }
  };

  return (
    <>
      <PageHeader title={d.data.name} subtitle={`${d.data.region} · ${d.data.type}`} actions={<Button variant="danger" onClick={remove}>Delete</Button>} />
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1fr_1.3fr]">
        <div className="space-y-5">
          <Card title="Details">
            <form onSubmit={saveDetails} className="space-y-3">
              <Field label="Name"><Input {...register("name", { required: true })} /></Field>
              <div className="grid grid-cols-2 gap-3"><Field label="Region"><Input {...register("region")} /></Field><Field label="Type"><Input {...register("type")} /></Field></div>
              <Field label="Description"><Textarea rows={4} {...register("description")} /></Field>
              <div className="grid grid-cols-2 gap-3"><Field label="Base price (USD)"><Input type="number" step="0.01" {...register("basePriceUsd")} /></Field>
                <Field label="Best season"><Input {...register("bestSeason")} /></Field></div>
              <Field label="Permit notes"><Textarea rows={2} {...register("permitNotes")} /></Field>
              <ErrorBox error={error} />
              <Button type="submit" busy={formState.isSubmitting}>Save details</Button>
            </form>
          </Card>
          <Card title="Photos" actions={<><input ref={fileRef} type="file" accept="image/jpeg,image/png" className="hidden"
            onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
            <Button variant="secondary" busy={uploading} onClick={() => fileRef.current?.click()}>Upload</Button></>}>
            <form className="mb-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (photoUrl.trim()) importFromUrl(); }}>
              <Input type="url" placeholder="https://… paste a photo address" value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)} aria-label="Photo URL" />
              <Button type="submit" variant="secondary" busy={importing} disabled={!photoUrl.trim()}>Add from URL</Button>
            </form>
            <div className="mb-3"><ErrorBox error={photoErr} /></div>
            <div className="grid grid-cols-3 gap-2">
              {(d.data.images ?? []).map((img: any, i: number) => (
                <figure key={img.id} className="group relative">
                  <img src={img.thumbnailUrl} alt={`Photo ${i + 1}`} className="aspect-square w-full rounded-[14px] object-cover" />
                  {i === 0 && <span className="absolute left-2 top-2 rounded-full bg-nav/80 px-2 py-0.5 text-xs text-white">Cover</span>}
                  <button type="button" onClick={() => removePhoto(img, i)} aria-label={`Remove photo ${i + 1}`}
                    className="absolute right-2 top-2 rounded-full bg-white/90 px-2 py-0.5 text-xs font-semibold text-error shadow">Remove</button>
                </figure>
              ))}
            </div>
            <p className="mt-3 text-xs text-muted">{(d.data.images ?? []).length
              ? "The app shows these as a slideshow (5 s per photo); the first one is the cover in Explore. JPEG or PNG, max 10 MB, up to 12 photos."
              : "No photos yet. Upload a JPEG/PNG or paste a photo URL. Every photo is resized and stripped of location data."}</p>
          </Card>
        </div>
        <Card title="DEFAULT itinerary" actions={<Button busy={savingDays} disabled={!daysValid(days)} onClick={saveDays}>Save itinerary</Button>}>
          <p className="mb-3 text-sm text-muted">Drag ⠿ to reorder (keyboard: focus the handle, Space, arrows). Day numbers follow the order.
            Bookings already placed keep the snapshot they were booked with.</p>
          <DayListEditor days={days} onChange={setDays} />
          <div className="mt-3"><ErrorBox error={dayErr} /></div>
        </Card>
      </div>
    </>
  );
}
