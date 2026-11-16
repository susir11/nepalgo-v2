import { useState } from "react";
import { useForm } from "react-hook-form";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { useGet, useInvalidate } from "../lib/hooks";
import { Badge, Button, Card, ErrorBox, Field, Input, Loading, Modal, PageHeader, Select, StatusBadge, Table, Td, useConfirm } from "../components/ui";

// Admin Team Management (spec 15.2 / 5b): SUPER_ADMIN only. Makes "every admin
// action is attributable to a specific admin" actually true.
export default function Staff() {
  const { session } = useAuth();
  const list = useGet<any>("/api/v1/admin/staff?size=100");
  const invalidate = useInvalidate();
  const confirm = useConfirm();
  const [open, setOpen] = useState(false);
  if (session?.role !== "SUPER_ADMIN") return <ErrorBox error={new Error("Only SUPER_ADMIN can manage the admin team.")} />;

  const act = (s: any, what: "deactivate" | "reset-mfa" | "role") => {
    const newRole = s.role === "OPS" ? "SUPER_ADMIN" : "OPS";
    const map = {
      deactivate: { title: "Deactivate staff account", consequence: "They can no longer sign in. Their past actions stay attributed to them.", body: undefined, path: "deactivate" },
      "reset-mfa": { title: "Reset two-step verification", consequence: "Their authenticator is unlinked; they must enrol again at next sign-in.", body: undefined, path: "reset-mfa" },
      role: { title: `Change role to ${newRole}`, consequence: newRole === "SUPER_ADMIN" ? "Grants full access: FX approval, VAT settings, policy publishing, staff management." : "Removes SUPER_ADMIN powers.", body: { role: newRole }, path: "role" },
    }[what];
    confirm({
      title: map.title, danger: what !== "role" || newRole === "SUPER_ADMIN", summary: [["Staff", s.email], ["Current role", s.role]],
      consequence: map.consequence, confirmLabel: "Confirm",
      run: () => api(`/api/v1/admin/staff/${s.id}/${map.path}`, { body: map.body ?? {} }).then(() => invalidate("/api/v1/admin/staff")),
    });
  };

  return (
    <>
      <PageHeader title="Admin team" actions={<Button onClick={() => setOpen(true)}>+ Add staff</Button>} />
      <Card>
        <ErrorBox error={list.error} />
        {list.isLoading ? <Loading /> : (
          <Table head={["Email", "Role", "2-step", "Status", ""]}>
            {list.data?.content?.map((s: any) => (
              <tr key={s.id}>
                <Td>{s.email} {s.id === session.staffId && <Badge tone="brand">you</Badge>}</Td>
                <Td><Badge tone={s.role === "SUPER_ADMIN" ? "mustard" : "neutral"}>{s.role}</Badge></Td>
                <Td>{s.mfaEnabled ? <Badge tone="success">On</Badge> : <Badge tone={s.role === "SUPER_ADMIN" ? "error" : "warning"}>Not enrolled</Badge>}</Td>
                <Td><StatusBadge status={s.active ? "ACTIVE" : "INACTIVE"} /></Td>
                <Td className="whitespace-nowrap">{s.id !== session.staffId && s.active && <>
                  <Button variant="ghost" onClick={() => act(s, "role")}>Make {s.role === "OPS" ? "super admin" : "ops"}</Button>
                  {s.mfaEnabled && <Button variant="ghost" onClick={() => act(s, "reset-mfa")}>Reset 2-step</Button>}
                  <Button variant="ghost" className="text-error" onClick={() => act(s, "deactivate")}>Deactivate</Button></>}</Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      <AddStaff open={open} onOpenChange={setOpen} />
    </>
  );
}

function AddStaff({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const { register, handleSubmit, reset, formState } = useForm<any>({ defaultValues: { role: "OPS" } });
  const invalidate = useInvalidate();
  const [err, setErr] = useState<unknown>(null);
  const submit = handleSubmit(async (f) => {
    setErr(null);
    try { await api("/api/v1/admin/staff", { body: f }); invalidate("/api/v1/admin/staff"); reset(); onOpenChange(false); }
    catch (e) { setErr(e); }
  });
  return (
    <Modal open={open} onOpenChange={onOpenChange} title="Add staff member">
      <form onSubmit={submit} className="space-y-3">
        <Field label="Email"><Input type="email" {...register("email", { required: true })} /></Field>
        <Field label="Temporary password" hint="Share it privately; they must change it at first sign-in."><Input type="text" autoComplete="off" {...register("temporaryPassword", { required: true, minLength: 12 })} /></Field>
        <Field label="Role"><Select {...register("role")}><option value="OPS">OPS (day-to-day bookings)</option><option value="SUPER_ADMIN">SUPER_ADMIN (full access)</option></Select></Field>
        <ErrorBox error={err} />
        <div className="flex justify-end gap-2"><Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>Cancel</Button><Button type="submit" busy={formState.isSubmitting}>Create</Button></div>
      </form>
    </Modal>
  );
}
