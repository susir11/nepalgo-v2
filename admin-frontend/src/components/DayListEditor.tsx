import { DndContext, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Button, Input, Textarea } from "./ui";

// Shared day-list editor (spec 8b: "reusing the same day-list editor components
// as the ItineraryVersion tool"). Used by the catalogue's DEFAULT itinerary and
// by Create Booking Manually's one-off custom itinerary. Day numbers come from
// the ORDER on screen: drag a day and everything renumbers.

export type Day = { key: string; narrative: string; hotelName: string; hotelLat: string; hotelLng: string };
let seq = 0;
export const newDay = (p: Partial<Omit<Day, "key">> = {}): Day =>
  ({ key: `d${++seq}`, narrative: "", hotelName: "", hotelLat: "", hotelLng: "", ...p });

/** API day objects ({dayNumber, narrative, hotelName, hotelLat, hotelLng}) -> editor rows. */
export const toDays = (apiDays: any[] | undefined | null): Day[] =>
  (apiDays ?? []).slice().sort((a, b) => a.dayNumber - b.dayNumber).map((x) => newDay({
    narrative: x.narrative ?? "", hotelName: x.hotelName ?? "",
    hotelLat: x.hotelLat == null ? "" : String(x.hotelLat), hotelLng: x.hotelLng == null ? "" : String(x.hotelLng) }));

/** Editor rows -> API day objects, numbered by position. */
export const toApiDays = (days: Day[]) => days.map((x, i) => ({
  dayNumber: i + 1, narrative: x.narrative.trim(), hotelName: x.hotelName.trim() || null,
  hotelLat: x.hotelLat === "" ? null : Number(x.hotelLat), hotelLng: x.hotelLng === "" ? null : Number(x.hotelLng) }));

export const daysValid = (days: Day[]) => days.length > 0 && days.every((x) => x.narrative.trim());

export function DayListEditor({ days, onChange }: { days: Day[]; onChange: (d: Day[]) => void }) {
  const sensors = useSensors(useSensor(PointerSensor), useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }));
  const onDragEnd = (e: DragEndEvent) => {
    if (!e.over || e.active.id === e.over.id) return;
    onChange(arrayMove(days, days.findIndex((x) => x.key === e.active.id), days.findIndex((x) => x.key === e.over!.id)));
  };
  return (
    <>
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={days.map((x) => x.key)} strategy={verticalListSortingStrategy}>
          <ol className="space-y-3">
            {days.map((day, i) => <DayRow key={day.key} day={day} index={i}
              onChange={(patch) => onChange(days.map((x) => (x.key === day.key ? { ...x, ...patch } : x)))}
              onRemove={() => onChange(days.filter((x) => x.key !== day.key))} />)}
          </ol>
        </SortableContext>
      </DndContext>
      <Button type="button" variant="secondary" className="mt-3" onClick={() => onChange([...days, newDay()])}>+ Add day</Button>
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
