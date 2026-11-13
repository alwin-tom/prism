import { execSync } from "node:child_process";

const cwd = "d:/Workspace/prism";

function run(query) {
  return execSync(`docker compose exec -T postgres psql -U prism -d prism -t -A -c ${JSON.stringify(query)}`, {
    cwd,
    encoding: "utf8",
  }).trim();
}

function walk(node, target) {
  if (!node || typeof node !== "object") return;
  if (node.type === "COUNTDOWN") {
    node.props ??= {};
    if (!node.props.target) {
      node.props.target = target;
      if (!node.props.label) node.props.label = "Until the day";
    }
  }
  for (const child of node.children ?? []) walk(child, target);
}

const rows = [
  { slug: "ajayweds", target: "2027-10-10T11:11:00" },
  { slug: "test1", target: "2027-10-10T09:00:00" },
];

for (const row of rows) {
  const raw = run(`SELECT d.document_json::text FROM event_page_documents d JOIN events e ON e.id = d.event_id WHERE e.slug = '${row.slug}' LIMIT 1`);
  if (!raw) {
    console.log("skip", row.slug);
    continue;
  }
  const doc = JSON.parse(raw);
  walk(doc.root, row.target);
  const escaped = JSON.stringify(doc).replaceAll("'", "''");
  run(`UPDATE event_page_documents d SET document_json = '${escaped}'::jsonb, updated_at = now() FROM events e WHERE e.id = d.event_id AND e.slug = '${row.slug}'`);
  console.log("patched", row.slug, "->", row.target);
}
