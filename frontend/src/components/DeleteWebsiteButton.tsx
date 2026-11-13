import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { api, errorMessage } from "../services/api";

export function DeleteWebsiteButton({ id, name, path, onDeleted, compact = false }: { id: string; name: string; path?: string; onDeleted?: () => void; compact?: boolean }) {
  const queryClient = useQueryClient();
  const [failed, setFailed] = useState("");
  const remove = useMutation({
    mutationFn: async () => api.delete(path ?? `/events/${id}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["events"] });
      void queryClient.invalidateQueries({ queryKey: ["event", id] });
      void queryClient.invalidateQueries({ queryKey: ["admin-events"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-event", id] });
      void queryClient.invalidateQueries({ queryKey: ["admin-reviews"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-dashboard"] });
      void queryClient.invalidateQueries({ queryKey: ["creation-sessions"] });
      onDeleted?.();
    },
    onError: (error) => setFailed(errorMessage(error, "The website was not deleted.")),
  });
  return (
    <div className={compact ? "site-delete is-compact" : "site-delete"}>
      <button
        className={compact ? "icon-soft is-danger" : "btn-text"}
        type="button"
        aria-label={`Delete ${name}`}
        disabled={remove.isPending}
        onClick={() => {
          setFailed("");
          if (window.confirm(`Delete ${name}? The page, pictures, and public address are removed.`)) {
            remove.mutate();
          }
        }}
      >
        <Trash2 size={16} />
        {compact ? null : "Delete"}
      </button>
      {failed ? <p className="error-line">{failed}</p> : null}
    </div>
  );
}
