import { useState } from "react";
import { X } from "lucide-react";
import { api, errorMessage } from "../../services/api";
import type { Envelope } from "../../types";
import { Button } from "../../components/ui";
import type { Question } from "./types";

export function QuestionField({ question, value, onChange, eventId }: {
  question: Question;
  value: unknown;
  onChange: (value: unknown) => void;
  eventId: string | null;
}) {
  if (question.type === "textarea") {
    return <textarea rows={5} placeholder={question.placeholder} value={text(value)} onChange={(event) => onChange(event.target.value)} />;
  }
  if (question.type === "single" || question.type === "yesno") {
    const options = question.type === "yesno"
      ? [{ value: "yes", label: "Yes" }, { value: "no", label: "No" }]
      : question.options ?? [];
    return (
      <div className="choice-stack">
        {options.map((option) => (
          <button key={option.value} type="button" className="choice" aria-pressed={text(value) === option.value} onClick={() => onChange(option.value)}>
            <strong>{option.label}</strong>
          </button>
        ))}
      </div>
    );
  }
  if (question.type === "multi") {
    const selected = Array.isArray(value) ? value.map(String) : [];
    return (
      <div className="choice-stack">
        {(question.options ?? []).map((option) => {
          const on = selected.includes(option.value);
          return (
            <button key={option.value} type="button" className="choice" aria-pressed={on} onClick={() => onChange(on ? selected.filter((item) => item !== option.value) : [...selected, option.value])}>
              <strong>{option.label}</strong>
            </button>
          );
        })}
      </div>
    );
  }
  if (question.type === "dropdown") {
    return (
      <select value={text(value)} onChange={(event) => onChange(event.target.value)}>
        <option value="">Choose one</option>
        {(question.options ?? []).map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    );
  }
  if (question.type === "images") {
    return <ImageAnswers eventId={eventId} value={Array.isArray(value) ? value.map(String) : []} onChange={onChange} />;
  }
  if (question.type === "repeat") {
    const rows = Array.isArray(value) ? value as Record<string, string>[] : [];
    return (
      <div className="repeat-list">
        {rows.map((row, index) => (
          <div key={index} className="repeat-row">
            {(question.item ?? []).map((field) => (
              <label key={field.key} className="field">
                <span>{field.label}</span>
                <input type={field.type === "date" || field.type === "time" ? field.type : "text"} value={row[field.key] ?? ""} onChange={(event) => {
                  const next = rows.map((item, itemIndex) => itemIndex === index ? { ...item, [field.key]: event.target.value } : item);
                  onChange(next);
                }} />
              </label>
            ))}
            <Button type="button" tone="text" onClick={() => onChange(rows.filter((_, itemIndex) => itemIndex !== index))}>Remove</Button>
          </div>
        ))}
        <Button type="button" tone="secondary" onClick={() => onChange([...rows, {}])}>Add another</Button>
      </div>
    );
  }
  const inputType = question.type === "date" || question.type === "time" || question.type === "number" || question.type === "url" || question.type === "email"
    ? question.type
    : question.type === "phone" ? "tel" : "text";
  return <input type={inputType} placeholder={question.placeholder} value={text(value)} onChange={(event) => onChange(question.type === "number" && event.target.value !== "" ? Number(event.target.value) : event.target.value)} />;
}

function ImageAnswers({ eventId, value, onChange }: { eventId: string | null; value: string[]; onChange: (value: string[]) => void }) {
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState("");

  async function upload(file: File) {
    if (!eventId) {
      setError("Start the website before uploading.");
      return;
    }
    setError("");
    setProgress(0);
    const body = new FormData();
    body.append("file", file);
    try {
      const uploaded = await api.post<Envelope<{ id: string }>>(`/events/${eventId}/media`, body, {
        onUploadProgress: (event) => {
          if (event.total) setProgress(Math.round((event.loaded / event.total) * 100));
        },
      });
      onChange([...value, uploaded.data.data.id]);
    } catch (uploadError) {
      setError(errorMessage(uploadError, "That file could not be uploaded."));
    } finally {
      setProgress(null);
    }
  }

  return (
    <div className="upload-list">
      <div className="upload-previews">
        {value.map((id) => (
          <figure key={id}>
            <img src={`/api/v1/events/${eventId}/media/${id}`} alt="" />
            <button type="button" aria-label="Remove image" onClick={() => onChange(value.filter((item) => item !== id))}><X size={14} /></button>
          </figure>
        ))}
      </div>
      <label className="btn btn-secondary">
        Upload
        <input type="file" accept="image/jpeg,image/png,image/gif,image/webp,video/mp4,video/webm" hidden onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = "";
          if (file) void upload(file);
        }} />
      </label>
      {progress != null ? <p className="studio-hint">Uploading {progress}%</p> : null}
      {error ? <p className="error-line">{error}</p> : null}
    </div>
  );
}

function text(value: unknown) {
  return value == null ? "" : String(value);
}
