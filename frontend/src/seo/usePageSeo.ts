import { useEffect } from "react";
import { applyPageSeo, DEFAULT_DESCRIPTION } from "./pageSeo";

type Options = {
  title: string;
  description?: string;
  path?: string;
  image?: string;
  noIndex?: boolean;
  robots?: string;
};

export function usePageSeo({
  title,
  description = DEFAULT_DESCRIPTION,
  path,
  image,
  noIndex = false,
  robots,
}: Options) {
  useEffect(() => {
    applyPageSeo({ title, description, path, image, noIndex, robots });
  }, [title, description, path, image, noIndex, robots]);
}
