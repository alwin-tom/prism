import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { PublicSite } from "../components/templates/PublicSite";
import { api } from "../services/api";
import type { Envelope, PublicEvent } from "../types";

export function PublicEventPage() {
  const { slug = "" } = useParams();
  const event = useQuery({
    queryKey: ["public", slug],
    queryFn: async () => (await api.get<Envelope<PublicEvent>>(`/public/events/${slug}`)).data.data,
  });
  if (event.isLoading) {
    return <main className="event-status"><p>Loading the site.</p></main>;
  }
  if (event.isError || !event.data) {
    return (
      <main className="event-status">
        <div>
          <h1>This page is not available</h1>
          <p style={{ marginTop: 12 }}>There is no published WebAux site at this address.</p>
        </div>
      </main>
    );
  }
  return <PublicSite event={event.data} />;
}
