import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "./api";

// GET helper: the query key IS the path, so invalidating "/api/admin/bookings"
// refreshes every list/detail that starts with it.
export function useGet<T = any>(path: string | null, opts: { allow404?: boolean } = {}) {
  return useQuery<T | null>({
    queryKey: [path],
    enabled: !!path,
    queryFn: async () => {
      try {
        return await api<T>(path!);
      } catch (e) {
        if (opts.allow404 && e instanceof ApiError && e.status === 404) return null;
        throw e;
      }
    },
  });
}

// Mutation that refreshes every cached query whose path starts with one of `invalidate`.
export function useSend(invalidate: string[] = []) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ path, method, body }: { path: string; method?: string; body?: any }) =>
      api(path, { method: method ?? "POST", body }),
    onSuccess: () => {
      qc.invalidateQueries({
        predicate: (q) => typeof q.queryKey[0] === "string" && invalidate.some((p) => (q.queryKey[0] as string).startsWith(p)),
      });
    },
  });
}

export function useInvalidate() {
  const qc = useQueryClient();
  return (...prefixes: string[]) =>
    qc.invalidateQueries({
      predicate: (q) => typeof q.queryKey[0] === "string" && prefixes.some((p) => (q.queryKey[0] as string).startsWith(p)),
    });
}

export const qs = (o: Record<string, string | number | undefined | null>) =>
  Object.entries(o).filter(([, v]) => v !== undefined && v !== null && v !== "").map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join("&");
