import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useParams } from "react-router";
import { DndContext, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { api } from "../lib/api";
import { useGet, useInvalidate } from "../lib/hooks";
import { Button, Card, ErrorBox, Field, Input, Loading, PageHeader, Textarea, useConfirm, useToast } from "../components/ui";

type Day = { key: string; narrative: string; hotelName: string; hotelLat: string; hotelLng: string };
let seq = 0;
const newKey = () => `d${++seq}`;

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
  const sensors = useSensors(useSensor(PointerSensor), useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }));

  useEffect(() => {
    if (!d.data) return;
    reset({ name: d.data.name, region: d.data.region, type: d.data.type, description: d.data.description,
      bestSeason: d.data.bestSeason ?? "", basePriceUsd: d.data.basePriceUsd, permitNotes: d.data.permitNotes ?? "" });
    setDays((d.data.defaultItinerary?.days ?? []).map((x: any) => ({ key: newKey(), narrative: x.narrative, hotelName: x.hotelName ?? "",
      hotelLat: x.hotelLat ?? "", hotelLng: x.hotelLng ?? "" })));
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

  const onDragEnd = (e: DragEndEvent) => {
    if (!e.over || e.active.id === e.over.id) return;
    setDays((ds) => arrayMove(ds, ds.findIndex((x) => x.key === e.active.id), ds.findIndex((x) => x.key === e.over!.id)));
  };

  const saveDays = async () => {
    setDayErr(null); setSavingDays(true);
    // Day numbers come from the ORDER on screen: drag a day and it renumbers.
    const body = { tier: "DEFAULT", days: days.map((x, i) => ({ dayNumber: i + 1, narrative: x.narrative.trim(), hotelName: x.hotelName || null,
      hotelLat: x.hotelLat === "" ? null : Number(x.hotelLat), hotelLng: x.hotelLng === "" ? null : Number(x.hotelLng) })) };
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
        <Card title="DEFAULT itinerary" actions={<Button busy={savingDays} disabled={!days.length || days.some((x) => !x.narrative.trim())} onClick={saveDays}>Save itinerary</Button>}>
          <p className="mb-3 text-sm text-muted">Drag ⠿ to reorder (keyboard: focus the handle, Space, arrows). Day numbers follow the order.
            Bookings already placed keep the snapshot they were booked with.</p>
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
            <SortableContext items={days.map((x) => x.key)} strategy={verticalListSortingStrategy}>
              <ol className="space-y-3">
                {days.map((day, i) => <DayRow key={day.key} day={day} index={i}
                  onChange={(patch) => setDays((ds) => ds.map((x) => (x.key === day.key ? { ...x, ...patch } : x)))}
                  onRemove={() => setDays((ds) => ds.filter((x) => x.key !== day.key))} />)}
              </ol>
            </SortableContext>
          </DndContext>
          <Button variant="secondary" className="mt-3" onClick={() => setDays((ds) => [...ds, { key: newKey(), narrative: "", hotelName: "", hotelLat: "", hotelLng: "" }])}>+ Add day</Button>
          <div className="mt-3"><ErrorBox error={dayErr} /></div>
        </Card>
      </div>
    </>
  );
}

function DayRow({ day, index, onChange, onRemove }: { day: Day; index: number; onChange: (p: Partial<Day>) => void; onRemove: () => void }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: day.key });
  return (
    <li ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition }}
      className={`rounded-[14px] bg-background/60 p-3 ${isDragging ? "ring-2 ring-primary" : ""}`}>
      <div className="mb-2 flex items-center gap-2">
        <button type="button" className="cursor-grab rounded-full px-2 text-lg text-muted" aria-label={`Reorder day ${index + 1}`} {...attributes} {...listeners}>⠿</button>
        <b className="text-sm">Day {index + 1}</b>
        <button type="button" className="ml-auto text-xs text-error" onClick={onRemove}>Remove</button>
      </div>
      <Textarea rows={2} placeholder="What happens this day" value={day.narrative} onChange={(e) => onChange({ narrative: e.target.value })} aria-label={`Day ${index + 1} narrative`} />
      <div className="mt-2 grid grid-cols-1 gap-2 md:grid-cols-[2fr_1fr_1fr]">
        <Input placeholder="Curated hotel (optional)" value={day.hotelName} onChange={(e) => onChange({ hotelName: e.target.value })} />
        <Input placeholder="Lat" type="number" step="any" value={day.hotelLat} onChange={(e) => onChange({ hotelLat: e.target.value })} />
        <Input placeholder="Lng" type="number" step="any" value={day.hotelLng} onChange={(e) => onChange({ hotelLng: e.target.value })} />
      </div>
    </li>
  );
}
