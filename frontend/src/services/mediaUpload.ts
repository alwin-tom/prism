import { api } from "./api";
import type { Envelope } from "../types";

export type UploadedMedia = {
  id: string;
  mediaType?: string;
  url?: string;
  originalFilename?: string;
};

export async function uploadEventMedia(eventId: string, file: File): Promise<UploadedMedia> {
  const body = new FormData();
  body.append("file", file);
  return (await api.post<Envelope<UploadedMedia>>(`/events/${eventId}/media`, body)).data.data;
}

export async function uploadEventMediaMany(eventId: string, files: File[]): Promise<UploadedMedia[]> {
  const uploaded: UploadedMedia[] = [];
  for (const file of files) {
    uploaded.push(await uploadEventMedia(eventId, file));
  }
  return uploaded;
}

export function filesFromInput(input: HTMLInputElement | null | undefined): File[] {
  return input?.files?.length ? [...input.files] : [];
}
