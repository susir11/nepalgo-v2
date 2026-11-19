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

  const upload = async (file: File) => {
    setUploading(true);
    const form = new FormData(); form.append("file", file);
    try { await api(`/api/admin/destinations/${id}/images`, { form, method: "POST" }); invalidate("/api/destinations"); toast("Image uploaded"); }
    catch (e) { setError(e); } finally { setUploading(false); if (fileRef.current) fileRef.current.value = ""; }
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
          <Card title="Images" actions={<><input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp" className="hidden"
            onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
            <Button variant="secondary" busy={uploading} onClick={() => fileRef.current?.click()}>Upload</Button></>}>
            <div className="grid grid-cols-3 gap-2">
              {d.data.imageUrls?.map((u: string) => <img key={u} src={u} alt="" className="aspect-square w-full rounded-[14px] object-cover" />)}
            </div>
            {!d.data.imageUrls?.length && <p className="text-sm text-muted">No images yet. Uploads are resized/compressed by the image pipeline.</p>}
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
