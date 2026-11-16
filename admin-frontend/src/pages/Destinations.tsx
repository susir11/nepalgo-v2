import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router";
import { api } from "../lib/api";
import { useGet, qs } from "../lib/hooks";
import { usd } from "../lib/format";
import { Button, Card, Empty, ErrorBox, Field, Input, Loading, Modal, PageHeader, Table, Td, Textarea } from "../components/ui";

export default function Destinations() {
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [open, setOpen] = useState(false);
  const list = useGet<any>(`/api/destinations?${qs({ q: search, size: 100 })}`);
  return (
    <>
      <PageHeader title="Destinations & itineraries" actions={<Button onClick={() => setOpen(true)}>+ New destination</Button>} />
      <Card>
        <form className="mb-4 flex gap-2" onSubmit={(e) => { e.preventDefault(); setSearch(q.trim()); }}>
          <Input className="max-w-sm" placeholder="Search name, region, type…" value={q} onChange={(e) => setQ(e.target.value)} />
          <Button type="submit" variant="secondary">Search</Button>
        </form>
        <ErrorBox error={list.error} />
        {list.isLoading ? <Loading /> : !list.data?.content?.length ? <Empty>No destinations.</Empty> : (
          <Table head={["", "Name", "Region", "Type", "From"]}>
            {list.data.content.map((d: any) => (
              <tr key={d.id} className="hover:bg-background/40">
                <Td>{d.thumbnailUrl ? <img src={d.thumbnailUrl} alt="" className="size-12 rounded-[12px] object-cover" /> : <div className="size-12 rounded-[12px] bg-background" />}</Td>
                <Td><Link className="font-semibold text-primary" to={`/destinations/${d.id}`}>{d.name}</Link></Td>
                <Td>{d.region}</Td><Td>{d.type}</Td><Td>{usd(d.basePriceUsd)}</Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      <NewDestination open={open} onOpenChange={setOpen} />
    </>
  );
}

function NewDestination({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const nav = useNavigate();
  const { register, handleSubmit, formState } = useForm<any>();
  const [error, setError] = useState<unknown>(null);
  const save = handleSubmit(async (f) => {
    setError(null);
    try {
      const d = await api("/api/admin/destinations", { body: { ...f, basePriceUsd: Number(f.basePriceUsd),
        latitude: f.latitude ? Number(f.latitude) : null, longitude: f.longitude ? Number(f.longitude) : null } });
      onOpenChange(false);
      nav(`/destinations/${d.id}`);
    } catch (e) { setError(e); }
  });
  return (
    <Modal open={open} onOpenChange={onOpenChange} title="New destination">
      <form onSubmit={save} className="space-y-3">
        <Field label="Name"><Input {...register("name", { required: true })} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Region"><Input {...register("region", { required: true })} placeholder="Khumbu" /></Field>
          <Field label="Type"><Input {...register("type", { required: true })} placeholder="Trek" /></Field>
        </div>
        <Field label="Description"><Textarea rows={3} {...register("description", { required: true })} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Base price (USD)"><Input type="number" step="0.01" min="0" {...register("basePriceUsd", { required: true })} /></Field>
          <Field label="Best season"><Input {...register("bestSeason")} placeholder="Oct–Nov" /></Field>
        </div>
        <Field label="Permit notes (guidance only)"><Textarea rows={2} {...register("permitNotes")} /></Field>
        <ErrorBox error={error} />
        <div className="flex justify-end gap-2"><Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button type="submit" busy={formState.isSubmitting}>Create</Button></div>
      </form>
    </Modal>
  );
}
