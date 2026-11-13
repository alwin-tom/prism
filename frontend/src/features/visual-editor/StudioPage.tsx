import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlignCenter, AlignEndHorizontal, AlignEndVertical, AlignHorizontalSpaceBetween, AlignLeft, AlignRight, AlignStartHorizontal, AlignStartVertical, AlignCenterHorizontal, AlignCenterVertical, ArrowLeft, BringToFront, ChevronDown, ChevronUp, ClipboardPaste, Copy, Eye, Monitor, Paintbrush, PaintBucket, Redo2, Save, SendToBack, Smartphone, Tablet, Trash2, Undo2, ZoomIn, ZoomOut, CopyPlus } from "lucide-react";
import { api, errorMessage } from "../../services/api";
import type { Envelope, EventSite } from "../../types";
import { Mark } from "../../components/shell/Mark";
import { TypographyPanel } from "../typography/TypographyPanel";
import { CAROUSEL_PRESETS, ELEMENT_LIBRARY, FRAME_WIDTH, INSERT_BLOCKS, SHADOW_PRESETS, addChild, duplicateNode, findNode, groupNodes, hasOverride, insertBlock, insertCopy, parentOf, placeAcross, propsFor, removeNode, removeResponsiveOverride, restack, setDeviceVisibility, shiftChild, stylesFor, ungroupNode, updateNode, type Breakpoint, type EditorDocument, type EditorNode } from "./document";
import { contentBoxWidth, moveStyles, preferPercent, readRotate, usedOffset } from "./geometry";
import { VisualPage } from "./VisualPage";

type Stored = { version: number; document: EditorDocument };

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 2;
const FIT_ZOOM = 0.67;
const ZOOM_STEPS = [0.5, 0.67, 0.75, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2];

function Tool({ label, disabled, pressed, onClick, children }: { label: string; disabled?: boolean; pressed?: boolean; onClick: () => void; children: ReactNode }) {
  return <button type="button" aria-label={label} title={label} disabled={disabled} aria-pressed={pressed} onClick={onClick}>{children}</button>;
}

function frameWidth(breakpoint: Breakpoint) {
  return FRAME_WIDTH[breakpoint];
}

function clampZoom(value: number) {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round(value * 100) / 100));
}

function openingZoom(available: number, page: number) {
  if (available <= 0 || page <= 0) return FIT_ZOOM;
  const fit = Math.floor((available / page) * 100) / 100;
  if (fit >= FIT_ZOOM) return FIT_ZOOM;
  return Math.min(MAX_ZOOM, Math.max(0.25, fit));
}

function stepZoom(current: number, direction: number) {
  if (direction > 0) return ZOOM_STEPS.find((value) => value > current + 0.001) ?? MAX_ZOOM;
  return [...ZOOM_STEPS].reverse().find((value) => value < current - 0.001) ?? MIN_ZOOM;
}

export function StudioPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const features = useQuery({
    queryKey: ["features"],
    queryFn: async () => (await api.get<Envelope<{ advancedEditorEnabled: boolean }>>("/config/features")).data.data,
  });
  const page = useQuery({
    queryKey: ["editor-document", id],
    enabled: features.data?.advancedEditorEnabled === true,
    retry: false,
    queryFn: async () => (await api.get<Envelope<Stored>>(`/events/${id}/editor/document`)).data.data,
  });
  if (features.isLoading) return <p className="studio-note">Loading the editor.</p>;
  if (!features.data?.advancedEditorEnabled) {
    return <p className="studio-note">The advanced editor is turned off. <Link to={`/app/events/${id}/edit`}>Back to the current editor</Link></p>;
  }
  if (page.isLoading) return <p className="studio-note">Loading the page.</p>;
  if (page.isError || !page.data) {
    return <StartStudio id={id} onReady={() => void queryClient.invalidateQueries({ queryKey: ["editor-document", id] })} />;
  }
  return <Studio id={id} initial={page.data} />;
}

function StartStudio({ id, onReady }: { id: string; onReady: () => void }) {
  const convert = useMutation({
    mutationFn: async () => (await api.post<Envelope<Stored>>(`/events/${id}/editor/convert`)).data.data,
    onSuccess: onReady,
  });
  return (
    <div className="studio-note">
      <p>This keeps the current template and makes a separate visual page from it. The published site does not change until this revision is approved.</p>
      <button type="button" disabled={convert.isPending} onClick={() => convert.mutate()}>{convert.isPending ? "Preparing" : "Convert this template"}</button>
      {convert.isError ? <p>{errorMessage(convert.error, "The visual page was not created.")}</p> : null}
      <p><Link to={`/app/events/${id}/edit`}>Stay with the current editor</Link></p>
    </div>
  );
}

function Studio({ id, initial }: { id: string; initial: Stored }) {
  const queryClient = useQueryClient();
  const [document, setDocument] = useState(initial.document);
  const [version, setVersion] = useState(initial.version);
  const [past, setPast] = useState<EditorDocument[]>([]);
  const [future, setFuture] = useState<EditorDocument[]>([]);
  const [selectedId, setSelectedId] = useState("page");
  const [rail, setRail] = useState<"add" | "layers" | "blocks">("add");
  const [alsoSelected, setAlsoSelected] = useState<string[]>([]);
  const [breakpoint, setBreakpoint] = useState<Breakpoint>("desktop");
  const [zoom, setZoom] = useState(FIT_ZOOM);
  const userZoomed = useRef(false);
  const canvasRef = useRef<HTMLDivElement>(null);
  const sheetRef = useRef<HTMLDivElement>(null);
  const [sheetHeight, setSheetHeight] = useState(640);
  const [status, setStatus] = useState("Saved");
  const generation = useRef(0);
  const pushVersion = useRef(false);
  const clipboard = useRef<EditorNode | null>(null);
  const styleClipboard = useRef<Record<string, string> | null>(null);
  const selected = findNode(document.root, selectedId) ?? document.root;

  function change(apply: (copy: EditorDocument) => void, coalesce = false) {
    generation.current += 1;
    if (!coalesce) {
      setPast((items) => [...items.slice(-29), structuredClone(document)]);
      setFuture([]);
    }
    setDocument((current) => {
      const copy = structuredClone(current);
      apply(copy);
      return copy;
    });
    setStatus("Unsaved changes");
  }

  function undo() {
    setPast((items) => {
      const previous = items[items.length - 1];
      if (!previous) return items;
      setFuture((next) => [document, ...next]);
      setDocument(previous);
      setStatus("Unsaved changes");
      return items.slice(0, -1);
    });
  }

  function redo() {
    setFuture((items) => {
      const [next, ...rest] = items;
      if (!next) return items;
      setPast((history) => [...history, document]);
      setDocument(next);
      setStatus("Unsaved changes");
      return rest;
    });
  }

  const site = useQuery({
    queryKey: ["event", id],
    queryFn: async () => (await api.get<Envelope<EventSite>>(`/events/${id}`)).data.data,
  });

  function openPreview() {
    const event = site.data;
    if (!event) return;
    sessionStorage.setItem(`prism-preview-${id}`, JSON.stringify({
      slug: event.slug,
      eventName: event.eventName,
      templateCode: event.templateCode,
      configuration: event.configuration,
      editorDocument: document,
    }));
    const preview = window.open(`/app/events/${id}/preview`, "_blank");
    preview?.focus();
  }

  function commitSpan(key: "width" | "left", value: string) {
    const element = window.document.querySelector(`[data-node="${CSS.escape(selected.id)}"]`);
    const basis = element instanceof HTMLElement && element.parentElement ? contentBoxWidth(element.parentElement) : frameWidth(breakpoint);
    const next = preferPercent(value, basis);
    if (next === value.trim()) return;
    if (key === "left") {
      change((copy) => placeAcross(copy, selected.id, { position: styleValue(selected, breakpoint, "position") || "relative", left: next }, breakpoint));
      return;
    }
    paint({ [key]: next });
  }

  function paint(styles: Record<string, string>, coalesce = false) {
    change((copy) => {
      const node = findNode(copy.root, selected.id);
      if (!node) return;
      if (node.type === "SPACER" && styles.height) {
        const size = Math.min(240, Math.max(4, Number.parseInt(styles.height, 10) || 24));
        updateNode(copy, node.id, { props: { size }, styles: without(styles, "height") }, "desktop");
        return;
      }
      updateNode(copy, selected.id, { styles }, breakpoint);
    }, coalesce);
  }

  const save = useMutation({
    mutationFn: async (gen: number) => ({ gen, stored: (await api.put<Envelope<Stored>>(`/events/${id}/editor/document`, { document, version })).data.data }),
    onSuccess: ({ gen, stored }) => {
      setVersion(stored.version);
      if (gen === generation.current) setStatus(pushVersion.current ? `Version ${stored.version}` : "Saved");
      pushVersion.current = false;
    },
    onError: (error) => setStatus(errorMessage(error, "Save failed")),
  });

  useEffect(() => {
    if (status !== "Unsaved changes" || save.isPending) return;
    const timer = window.setTimeout(() => save.mutate(generation.current), 1500);
    return () => window.clearTimeout(timer);
  }, [document, status, save.isPending, version]);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement;
      if (target.closest("input, textarea, select")) return;
      const meta = event.ctrlKey || event.metaKey;
      if (meta && event.key.toLowerCase() === "z") {
        event.preventDefault();
        if (event.shiftKey) redo();
        else undo();
      } else if (meta && (event.key === "=" || event.key === "+" || event.key === "-")) {
        event.preventDefault();
        userZoomed.current = true;
        setZoom((current) => stepZoom(current, event.key === "-" ? -1 : 1));
      } else if (meta && event.key === "0") {
        event.preventDefault();
        userZoomed.current = false;
        setZoom(fittedZoom());
      } else if (meta && event.key.toLowerCase() === "s") {
        event.preventDefault();
        save.mutate(generation.current);
      } else if (meta && event.key.toLowerCase() === "d" && selectedId !== document.root.id) {
        event.preventDefault();
        change((copy) => {
          const copyNode = duplicateNode(copy, selectedId);
          if (copyNode) setSelectedId(copyNode.id);
        });
      } else if (meta && event.key.toLowerCase() === "c") {
        event.preventDefault();
        clipboard.current = selected.id === document.root.id ? null : structuredClone(selected);
      } else if (meta && event.key.toLowerCase() === "v" && clipboard.current) {
        event.preventDefault();
        const source = clipboard.current;
        const parent = parentOf(document.root, selectedId)?.id ?? document.root.id;
        change((copy) => {
          const pasted = insertCopy(copy, parent, source);
          setSelectedId(pasted.id);
        });
      } else if (meta && event.key.toLowerCase() === "g") {
        event.preventDefault();
        const ids = [selectedId, ...alsoSelected];
        if (event.shiftKey) change((copy) => ungroupNode(copy, selectedId));
        else change((copy) => {
          const group = groupNodes(copy, ids);
          if (group) {
            setSelectedId(group.id);
            setAlsoSelected([]);
          }
        });
      } else if (event.key === "Escape") {
        setSelectedId(document.root.id);
        setAlsoSelected([]);
      } else if (!meta && event.key.startsWith("Arrow") && selectedId !== document.root.id && !selected.locked) {
        event.preventDefault();
        const step = event.shiftKey ? 10 : 1;
        const dx = event.key === "ArrowLeft" ? -step : event.key === "ArrowRight" ? step : 0;
        const dy = event.key === "ArrowUp" ? -step : event.key === "ArrowDown" ? step : 0;
        const element = window.document.querySelector(`[data-node="${CSS.escape(selectedId)}"]`);
        const basis = element instanceof HTMLElement && element.parentElement ? contentBoxWidth(element.parentElement) : frameWidth(breakpoint);
        const patch = moveStyles({
          left: element instanceof HTMLElement ? usedOffset(element, "left") : 0,
          top: element instanceof HTMLElement ? usedOffset(element, "top") : 0,
          width: 0,
          height: 0,
          position: styleValue(selected, breakpoint, "position"),
        }, dx, dy, basis);
        change((copy) => placeAcross(copy, selected.id, patch, breakpoint));
      } else if ((event.key === "Delete" || event.key === "Backspace") && selectedId !== document.root.id) {
        event.preventDefault();
        change((copy) => {
          removeNode(copy, selectedId);
          alsoSelected.forEach((item) => removeNode(copy, item));
        });
        setSelectedId(document.root.id);
        setAlsoSelected([]);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const onWheel = (event: WheelEvent) => {
      if (!event.ctrlKey && !event.metaKey) return;
      event.preventDefault();
      userZoomed.current = true;
      setZoom((current) => clampZoom(current * (event.deltaY < 0 ? 1.1 : 1 / 1.1)));
    };
    canvas.addEventListener("wheel", onWheel, { passive: false });
    return () => canvas.removeEventListener("wheel", onWheel);
  }, []);

  useLayoutEffect(() => {
    const sheet = sheetRef.current;
    if (!sheet) return;
    const measure = () => setSheetHeight(Math.max(640, sheet.offsetHeight));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(sheet);
    return () => observer.disconnect();
  }, [document, breakpoint]);

  const width = frameWidth(breakpoint);
  const deviceName = breakpoint === "desktop" ? "Desktop" : breakpoint === "tablet" ? "Tablet" : "Mobile";
  const deviceProps = propsFor(selected, breakpoint);

  function mark(property: string) {
    if (breakpoint === "desktop") return null;
    if (!hasOverride(selected, property, breakpoint)) return <em className="studio-inherited">Inherited</em>;
    return <button type="button" className="studio-override" onClick={() => change((copy) => removeResponsiveOverride(copy, selected.id, [property], breakpoint))}>Reset</button>;
  }

  function fittedZoom() {
    const canvas = canvasRef.current;
    if (!canvas) return FIT_ZOOM;
    const style = getComputedStyle(canvas);
    const pad = (Number.parseFloat(style.paddingLeft) || 0) + (Number.parseFloat(style.paddingRight) || 0);
    return openingZoom(canvas.clientWidth - pad, width);
  }

  useLayoutEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const apply = () => {
      if (userZoomed.current) return;
      setZoom(fittedZoom());
    };
    apply();
    const observer = new ResizeObserver(apply);
    observer.observe(canvas);
    return () => observer.disconnect();
  }, [width]);
  return (
    <div className="studio">
      <header className="studio-bar">
        <div className="studio-bar-title">
          <Link to={`/app/events/${id}/edit`} aria-label="Back to the event"><ArrowLeft size={16} /></Link>
          <Mark />
          <strong title={document.page.title || "Visual page"}>{document.page.title || "Visual page"}</strong>
        </div>
        <span className="studio-status">{save.isPending ? "Saving" : status}</span>
        <div className="studio-tools">
          <div className="studio-tool" role="group" aria-label="History">
            <Tool label="Undo" disabled={past.length === 0} onClick={undo}><Undo2 size={15} /></Tool>
            <Tool label="Redo" disabled={future.length === 0} onClick={redo}><Redo2 size={15} /></Tool>
          </div>
          <div className="studio-tool" role="group" aria-label="Elements">
            <Tool label="Duplicate" disabled={selectedId === document.root.id} onClick={() => change((copy) => { const copyNode = duplicateNode(copy, selectedId); if (copyNode) setSelectedId(copyNode.id); })}><CopyPlus size={15} /></Tool>
            <Tool label="Copy" onClick={() => { clipboard.current = selected.id === document.root.id ? null : structuredClone(selected); }}><Copy size={15} /></Tool>
            <Tool label="Paste" disabled={!clipboard.current} onClick={() => { if (!clipboard.current) return; const source = clipboard.current; change((copy) => { const pasted = insertCopy(copy, parentOf(copy.root, selectedId)?.id ?? copy.root.id, source); setSelectedId(pasted.id); }); }}><ClipboardPaste size={15} /></Tool>
          </div>
          <div className="studio-tool" role="group" aria-label="Style">
            <Tool label="Copy style" onClick={() => { styleClipboard.current = { ...stylesFor(selected, breakpoint) }; }}><Paintbrush size={15} /></Tool>
            <Tool label="Paste style" disabled={!styleClipboard.current} onClick={() => { if (styleClipboard.current) paint(styleClipboard.current); }}><PaintBucket size={15} /></Tool>
          </div>
          <div className="studio-tool" role="group" aria-label="Stack">
            <Tool label="Bring to front" disabled={selectedId === document.root.id} onClick={() => change((copy) => restack(copy, selectedId, "front", breakpoint))}><BringToFront size={15} /></Tool>
            <Tool label="Send to back" disabled={selectedId === document.root.id} onClick={() => change((copy) => restack(copy, selectedId, "back", breakpoint))}><SendToBack size={15} /></Tool>
          </div>
          <div className="studio-tool" role="group" aria-label="Zoom">
            <Tool label="Zoom out" disabled={zoom <= MIN_ZOOM} onClick={() => { userZoomed.current = true; setZoom((current) => stepZoom(current, -1)); }}><ZoomOut size={15} /></Tool>
            <button type="button" className="studio-zoom-level" aria-label="Fit the page" title="Fit the page" onClick={() => { userZoomed.current = false; setZoom(fittedZoom()); }}>{Math.round(zoom * 100)}%</button>
            <Tool label="Zoom in" disabled={zoom >= MAX_ZOOM} onClick={() => { userZoomed.current = true; setZoom((current) => stepZoom(current, 1)); }}><ZoomIn size={15} /></Tool>
          </div>
          <div className="studio-tool" role="group" aria-label="Screen">
            <Tool label="Desktop" pressed={breakpoint === "desktop"} onClick={() => setBreakpoint("desktop")}><Monitor size={15} /></Tool>
            <Tool label="Tablet" pressed={breakpoint === "tablet"} onClick={() => setBreakpoint("tablet")}><Tablet size={15} /></Tool>
            <Tool label="Mobile" pressed={breakpoint === "mobile"} onClick={() => setBreakpoint("mobile")}><Smartphone size={15} /></Tool>
            <strong className="studio-device">{deviceName}</strong>
          </div>
        </div>
        <div className="studio-actions">
          <button type="button" onClick={openPreview} disabled={!site.data}><Eye size={15} /> Preview</button>
          <button type="button" onClick={() => save.mutate(generation.current)} disabled={save.isPending}><Save size={15} /> Save</button>
          <button type="button" onClick={() => { pushVersion.current = true; save.mutate(generation.current); }} disabled={save.isPending}>Push version</button>
        </div>
      </header>
      <div className="studio-body">
        <aside className="studio-library">
          <div className="studio-rail-tabs" role="tablist" aria-label="Library">
            <button type="button" aria-pressed={rail === "add"} onClick={() => setRail("add")}>Add</button>
            <button type="button" aria-pressed={rail === "layers"} onClick={() => setRail("layers")}>Layers</button>
            <button type="button" aria-pressed={rail === "blocks"} onClick={() => setRail("blocks")}>Blocks</button>
          </div>
          {rail === "add" ? ["Layout", "Text", "Media"].map((category) => (
            <div key={category} className="studio-library-group">
              <p>{category}</p>
              {ELEMENT_LIBRARY.filter((item) => item.category === category).map((item) => (
                <button
                  key={item.type}
                  type="button"
                  draggable
                  onDragStart={(event) => event.dataTransfer.setData("application/prism-element", item.type)}
                  onClick={() => change((copy) => {
                    const node = addChild(copy, selectedId, item.type);
                    if (item.type === "COUNTDOWN") {
                      const start = String(site.data?.configuration?.event?.startAt ?? "").trim();
                      if (start) updateNode(copy, node.id, { props: { target: start } }, breakpoint);
                    }
                    setSelectedId(node.id);
                    setAlsoSelected([]);
                  })}
                >{item.name}</button>
              ))}
            </div>
          )) : null}
          {rail === "layers" ? (
            <div className="studio-layers">
              <Layer node={document.root} breakpoint={breakpoint} selectedId={selectedId} onSelect={setSelectedId} onShift={(nodeId, direction) => change((copy) => shiftChild(copy, nodeId, direction))} depth={0} index={0} count={1} />
            </div>
          ) : null}
          {rail === "blocks" ? (
            <div className="studio-library-group">
              <p>Blocks</p>
              {INSERT_BLOCKS.map((block) => (
                <button key={block.id} type="button" onClick={() => change((copy) => { const node = insertBlock(copy, selectedId, block.id); setSelectedId(node.id); })}>{block.name}</button>
              ))}
              <p className="studio-hint">A heading and text, a picture with a caption, or a reply button. Each one is made of the same elements you can edit.</p>
            </div>
          ) : null}
        </aside>
        <div className="studio-canvas" ref={canvasRef}>
          <div className="studio-zoom-slot" style={{ width: width * zoom, height: sheetHeight * zoom }}>
          <div className="studio-sheet" ref={sheetRef} style={{ width, transform: `scale(${zoom})` }}>
            <VisualPage
              document={document}
              breakpoint={breakpoint}
              mediaEventId={id}
              countdownFallback={site.data?.configuration?.event?.startAt}
              onUploadPicture={async (nodeId, file) => {
                const mediaId = await uploadPicture(id, file);
                await queryClient.invalidateQueries({ queryKey: ["media", id] });
                change((copy) => updateNode(copy, nodeId, { props: { mediaId } }, breakpoint));
              }}
              onAddPictures={async (parentId, files) => {
                const mediaIds: string[] = [];
                for (const file of files) mediaIds.push(await uploadPicture(id, file));
                await queryClient.invalidateQueries({ queryKey: ["media", id] });
                change((copy) => {
                  for (const mediaId of mediaIds) {
                    const slide = addChild(copy, parentId, "IMAGE");
                    updateNode(copy, slide.id, { props: { mediaId } }, "desktop");
                  }
                });
              }}
              zoom={zoom}
              selectedId={selectedId}
              selectedIds={[...new Set([selectedId, ...alsoSelected])]}
              onSelect={(next, extend) => {
                const id = next || document.root.id;
                if (!extend || !next) {
                  setAlsoSelected([]);
                  setSelectedId(id);
                  return;
                }
                setAlsoSelected((ids) => {
                  const current = [...new Set([selectedId, ...ids])];
                  return current.includes(id) ? current.filter((item) => item !== id) : [...current, id];
                });
                setSelectedId(id);
              }}
              onDropType={(parentId, type) => change((copy) => {
                const node = addChild(copy, parentId, type);
                if (type === "COUNTDOWN") {
                  const start = String(site.data?.configuration?.event?.startAt ?? "").trim();
                  if (start) updateNode(copy, node.id, { props: { target: start } }, breakpoint);
                }
                setSelectedId(node.id);
                setAlsoSelected([]);
              })}
              onResize={(patches, coalesce) => change((copy) => {
                for (const patch of patches) {
                  const node = findNode(copy.root, patch.id);
                  if (!node || node.locked) continue;
                  if (node.type === "SPACER" && patch.styles.height) {
                    const size = Math.min(240, Math.max(4, Number.parseInt(patch.styles.height, 10) || 24));
                    updateNode(copy, patch.id, { props: { size }, styles: without(patch.styles, "height") }, breakpoint);
                    continue;
                  }
                  placeAcross(copy, patch.id, patch.styles, breakpoint);
                }
              }, coalesce)}
            />
          </div>
          </div>
        </div>
        <aside className="studio-inspector">
          <p className="studio-device-note">
            <span>Editing {deviceName}</span>
            {breakpoint === "desktop" ? <em className="studio-inherited">Base</em> : <em className="studio-inherited">Inherited unless marked</em>}
          </p>
          <p>{selected.name}</p>
          <label className="check-line">Hidden on {deviceName}
            <input type="checkbox" checked={styleValue(selected, breakpoint, "display") === "none"} onChange={(event) => change((copy) => setDeviceVisibility(copy, selected.id, breakpoint, event.target.checked))} />
          </label>
          {selected.props.title !== undefined ? (
            <label>Title
              <input value={String(selected.props.title ?? "")} onChange={(event) => change((copy) => updateNode(copy, selected.id, { props: { title: event.target.value } }, breakpoint))} />
            </label>
          ) : null}
          {selected.props.text !== undefined ? (
            <label>Text
              <textarea value={String(selected.props.text)} onChange={(event) => change((copy) => updateNode(copy, selected.id, { props: { text: event.target.value } }, breakpoint))} />
            </label>
          ) : null}
          {selected.type === "QUOTE" ? (
            <label>Attribution
              <input value={String(selected.props.attribution ?? "")} onChange={(event) => change((copy) => updateNode(copy, selected.id, { props: { attribution: event.target.value } }, breakpoint))} />
            </label>
          ) : null}
          {selected.type === "COUNTDOWN" ? (
            <>
              <label>Label
                <input value={String(selected.props.label ?? "")} onChange={(event) => change((copy) => updateNode(copy, selected.id, { props: { label: event.target.value } }, breakpoint))} />
              </label>
              <label>Target date and time
                <input
                  type="datetime-local"
                  value={toDatetimeLocal(String(selected.props.target ?? "") || String(site.data?.configuration?.event?.startAt ?? ""))}
                  onChange={(event) => change((copy) => updateNode(copy, selected.id, { props: { target: fromDatetimeLocal(event.target.value) } }, breakpoint))}
                />
              </label>
              <p className="studio-hint">Counts down in days, hours, minutes, and seconds to the date you set.</p>
            </>
          ) : null}
          {selected.type === "HEADING" ? (
            <label>Level
              <input type="number" min={1} max={6} value={Number(selected.props.level ?? 2)} onChange={(event) => change((copy) => updateNode(copy, selected.id, { props: { level: Number(event.target.value) } }, breakpoint))} />
            </label>
          ) : null}
          {selected.type === "BUTTON" ? (
            <>
              <label>Action
                <select
                  value={String(selected.props.href ?? "").trim().toLowerCase() === "#rsvp" ? "rsvp" : "link"}
                  onChange={(event) => change((copy) => updateNode(copy, selected.id, {
                    props: { href: event.target.value === "rsvp" ? "#rsvp" : "" },
                  }, breakpoint))}
                >
                  <option value="link">Open a link</option>
                  <option value="rsvp">Guest reply</option>
                </select>
              </label>
              {String(selected.props.href ?? "").trim().toLowerCase() === "#rsvp" ? null : (
                <label>Link
                  <input value={String(selected.props.href ?? "")} onChange={(event) => change((copy) => updateNode(copy, selected.id, { props: { href: event.target.value } }, breakpoint))} />
                </label>
              )}
            </>
          ) : null}
          {selected.type === "IMAGE" ? (
            <PictureField
              eventId={id}
              mediaId={String(deviceProps.mediaId ?? "")}
              alt={String(selected.props.alt ?? "")}
              onPick={(mediaId) => change((copy) => updateNode(copy, selected.id, { props: { mediaId } }, breakpoint))}
              onAlt={(alt) => change((copy) => updateNode(copy, selected.id, { props: { alt } }, breakpoint))}
            />
          ) : null}
          <ColourField label="Text colour" value={styleValue(selected, breakpoint, "color")} fallback="#121417" onChange={(value, coalesce) => paint({ color: value }, coalesce)} />
          <ColourField label="Background" value={solidBackground(styleValue(selected, breakpoint, "background"))} fallback="#ffffff" onChange={(value, coalesce) => paint({ background: value }, coalesce)} />
          <GradientField value={styleValue(selected, breakpoint, "background")} onChange={(value, coalesce) => paint({ background: value }, coalesce)} />
          <p className="studio-label">Shape</p>
          <div className="studio-presets" role="group" aria-label="Shape">
            <button type="button" aria-pressed={shapePreset(styleValue(selected, breakpoint, "borderRadius")) === "square"} onClick={() => paint({ borderRadius: "0", overflow: "visible" })}>Square</button>
            <button type="button" aria-pressed={shapePreset(styleValue(selected, breakpoint, "borderRadius")) === "rounded"} onClick={() => paint({ borderRadius: "16px", overflow: "hidden" })}>Rounded</button>
            <button type="button" aria-pressed={shapePreset(styleValue(selected, breakpoint, "borderRadius")) === "circle"} onClick={() => paint({ borderRadius: "50%", overflow: "hidden" })}>Circle</button>
            <button type="button" aria-pressed={shapePreset(styleValue(selected, breakpoint, "borderRadius")) === "pill"} onClick={() => paint({ borderRadius: "9999px", overflow: "hidden" })}>Pill</button>
          </div>
          <label>Corner radius
            <input value={styleValue(selected, breakpoint, "borderRadius")} placeholder="0" onChange={(event) => paint({ borderRadius: event.target.value })} />
          </label>
          <label>Shadow
            <select value={Object.entries(SHADOW_PRESETS).find(([, value]) => value === styleValue(selected, breakpoint, "boxShadow"))?.[0] ?? "Custom"} onChange={(event) => { if (SHADOW_PRESETS[event.target.value] !== undefined) paint({ boxShadow: SHADOW_PRESETS[event.target.value] }); }}>
              {Object.keys(SHADOW_PRESETS).map((name) => <option key={name} value={name}>{name}</option>)}
              <option value="Custom">Custom</option>
            </select>
          </label>
          <OpacityField value={styleValue(selected, breakpoint, "opacity")} onChange={(value, coalesce) => paint({ opacity: value }, coalesce)} />
          {selected.type === "CAROUSEL" ? (
            <>
              <label>Carousel style {mark("preset")}
                <select value={String(deviceProps.preset || "minimal")} onChange={(event) => change((copy) => updateNode(copy, selected.id, { props: { preset: event.target.value } }, breakpoint))}>
                  {CAROUSEL_PRESETS.map((preset) => <option key={preset} value={preset}>{preset}</option>)}
                </select>
              </label>
              <label>Slides in view {mark("perView")}
                <select value={String(deviceProps.perView || 1)} onChange={(event) => change((copy) => updateNode(copy, selected.id, { props: { perView: Number(event.target.value) } }, breakpoint))}>
                  <option value="1">1</option>
                  <option value="2">2</option>
                  <option value="3">3</option>
                </select>
              </label>
              <label className="check-line">Autoplay {mark("autoplay")}
                <input type="checkbox" checked={Number(deviceProps.autoplay) !== 0} onChange={(event) => change((copy) => updateNode(copy, selected.id, { props: { autoplay: event.target.checked ? 1 : 0 } }, breakpoint))} />
              </label>
              <CarouselSlides
                eventId={id}
                slides={selected.children.filter((child) => child.type === "IMAGE")}
                onUpload={async (files, slideId) => {
                  const mediaIds: string[] = [];
                  for (const file of files) mediaIds.push(await uploadPicture(id, file));
                  await queryClient.invalidateQueries({ queryKey: ["media", id] });
                  const carouselId = selected.id;
                  change((copy) => {
                    if (slideId) {
                      updateNode(copy, slideId, { props: { mediaId: mediaIds[0] } }, "desktop");
                      return;
                    }
                    for (const mediaId of mediaIds) {
                      const slide = addChild(copy, carouselId, "IMAGE");
                      updateNode(copy, slide.id, { props: { mediaId } }, "desktop");
                    }
                  });
                }}
                onPick={(mediaId) => {
                  const carouselId = selected.id;
                  change((copy) => {
                    const slide = addChild(copy, carouselId, "IMAGE");
                    updateNode(copy, slide.id, { props: { mediaId } }, "desktop");
                  });
                }}
                onShift={(slideId, direction) => change((copy) => shiftChild(copy, slideId, direction))}
                onRemove={(slideId) => change((copy) => removeNode(copy, slideId))}
              />
            </>
          ) : null}
          <p className="studio-label">Align text</p>
          <div className="choice-row" role="group" aria-label="Align text">
            <AlignButton label="Align left" pressed={styleValue(selected, breakpoint, "textAlign") === "left"} onClick={() => paint({ textAlign: "left" })}><AlignLeft size={15} /></AlignButton>
            <AlignButton label="Align center" pressed={styleValue(selected, breakpoint, "textAlign") === "center"} onClick={() => paint({ textAlign: "center" })}><AlignCenter size={15} /></AlignButton>
            <AlignButton label="Align right" pressed={styleValue(selected, breakpoint, "textAlign") === "right"} onClick={() => paint({ textAlign: "right" })}><AlignRight size={15} /></AlignButton>
          </div>
          <p className="studio-label">Align across</p>
          <div className="choice-row" role="group" aria-label="Align across">
            <AlignButton label="Align to the start" pressed={axisValue(selected, breakpoint, "across") === "flex-start"} onClick={() => paint(axisPatch(selected, breakpoint, "across", "flex-start"))}><AlignStartHorizontal size={15} /></AlignButton>
            <AlignButton label="Align to the centre" pressed={axisValue(selected, breakpoint, "across") === "center"} onClick={() => paint(axisPatch(selected, breakpoint, "across", "center"))}><AlignCenterHorizontal size={15} /></AlignButton>
            <AlignButton label="Align to the end" pressed={axisValue(selected, breakpoint, "across") === "flex-end"} onClick={() => paint(axisPatch(selected, breakpoint, "across", "flex-end"))}><AlignEndHorizontal size={15} /></AlignButton>
            {axisKey(selected, breakpoint, "across") === "justifyContent" ? <AlignButton label="Space apart" pressed={styleValue(selected, breakpoint, "justifyContent") === "space-between"} onClick={() => paint({ justifyContent: "space-between" })}><AlignHorizontalSpaceBetween size={15} /></AlignButton> : null}
          </div>
          <p className="studio-label">Align down</p>
          <div className="choice-row" role="group" aria-label="Align down">
            <AlignButton label="Align to the top" pressed={axisValue(selected, breakpoint, "down") === "flex-start"} onClick={() => paint(axisPatch(selected, breakpoint, "down", "flex-start"))}><AlignStartVertical size={15} /></AlignButton>
            <AlignButton label="Align to the middle" pressed={axisValue(selected, breakpoint, "down") === "center"} onClick={() => paint(axisPatch(selected, breakpoint, "down", "center"))}><AlignCenterVertical size={15} /></AlignButton>
            <AlignButton label="Align to the bottom" pressed={axisValue(selected, breakpoint, "down") === "flex-end"} onClick={() => paint(axisPatch(selected, breakpoint, "down", "flex-end"))}><AlignEndVertical size={15} /></AlignButton>
            {axisKey(selected, breakpoint, "down") === "justifyContent" ? <AlignButton label="Space apart" pressed={styleValue(selected, breakpoint, "justifyContent") === "space-between"} onClick={() => paint({ justifyContent: "space-between" })}><AlignHorizontalSpaceBetween size={15} /></AlignButton> : null}
          </div>
          <TypographyPanel eventId={id} document={document} selected={selected} breakpoint={breakpoint} paint={paint} change={change} mark={mark} />
          <label>X
            <input value={styleValue(selected, breakpoint, "left")} placeholder="0%" onChange={(event) => change((copy) => placeAcross(copy, selected.id, { position: styleValue(selected, breakpoint, "position") || "relative", left: event.target.value }, breakpoint))} onBlur={(event) => commitSpan("left", event.target.value)} />
          </label>
          <label>Y
            <input value={styleValue(selected, breakpoint, "top")} placeholder="0px" onChange={(event) => paint({ position: styleValue(selected, breakpoint, "position") || "relative", top: event.target.value })} />
          </label>
          <label>Width {mark("width")}
            <input value={styleValue(selected, breakpoint, "width")} placeholder="auto" onChange={(event) => paint({ width: event.target.value })} onBlur={(event) => commitSpan("width", event.target.value)} />
          </label>
          <label>Height {mark(selected.type === "SPACER" ? "size" : "height")}
            <input value={selected.type === "SPACER" ? `${deviceProps.size ?? 24}px` : styleValue(selected, breakpoint, "height")} placeholder="auto" onChange={(event) => {
              if (selected.type === "SPACER") {
                const size = Number.parseInt(event.target.value, 10);
                change((copy) => updateNode(copy, selected.id, { props: { size: Number.isFinite(size) ? size : 24 } }, breakpoint));
                return;
              }
              paint({ height: event.target.value });
            }} />
          </label>
          <label>Rotation
            <input value={String(readRotate(styleValue(selected, breakpoint, "transform")))} onChange={(event) => paint({ transform: `rotate(${Math.round(Number(event.target.value) || 0)}deg)` })} />
          </label>
          <label>Position
            <select value={styleValue(selected, breakpoint, "position") || "static"} onChange={(event) => paint({ position: event.target.value === "static" ? "static" : event.target.value })}>
              <option value="static">In the flow</option>
              <option value="relative">Offset</option>
              <option value="absolute">Free on the page</option>
            </select>
          </label>
          <label>Margin {mark("margin")}
            <input value={styleValue(selected, breakpoint, "margin")} placeholder="0" onChange={(event) => paint({ margin: event.target.value })} />
          </label>
          <label>Padding {mark("padding")}
            <input value={styleValue(selected, breakpoint, "padding")} placeholder="0" onChange={(event) => paint({ padding: event.target.value })} />
          </label>
          <label>Gap {mark("gap")}
            <input value={styleValue(selected, breakpoint, "gap")} placeholder="0" onChange={(event) => paint({ gap: event.target.value })} />
          </label>
          {selected.type === "FLEX" || selected.type === "SECTION" || selected.type === "CONTAINER" || selected.type === "GROUP" ? (
            <label>Direction {mark("flexDirection")}
              <select value={styleValue(selected, breakpoint, "flexDirection") || "row"} onChange={(event) => paint({ flexDirection: event.target.value })}>
                <option value="row">Row</option>
                <option value="column">Column</option>
              </select>
            </label>
          ) : null}
          {selected.type === "GRID" ? (
            <label>Columns {mark("gridTemplateColumns")}
              <select value={styleValue(selected, breakpoint, "gridTemplateColumns") || "1fr 1fr"} onChange={(event) => paint({ gridTemplateColumns: event.target.value })}>
                <option value="1fr">1</option>
                <option value="1fr 1fr">2</option>
                <option value="1fr 1fr 1fr">3</option>
                <option value="1fr 1fr 1fr 1fr">4</option>
              </select>
            </label>
          ) : null}
          <label>Order {mark("order")}
            <input value={styleValue(selected, breakpoint, "order")} placeholder="0" onChange={(event) => paint({ order: event.target.value })} />
          </label>
          <p className="studio-label">Place in the parent</p>
          <div className="choice-row" role="group" aria-label="Place in the parent">
            <AlignButton label="Place at the left" pressed={styleValue(selected, breakpoint, "alignSelf") === "flex-start"} onClick={() => paint({ alignSelf: "flex-start" })}><AlignLeft size={15} /></AlignButton>
            <AlignButton label="Place in the centre" pressed={styleValue(selected, breakpoint, "alignSelf") === "center"} onClick={() => paint({ alignSelf: "center" })}><AlignCenter size={15} /></AlignButton>
            <AlignButton label="Place at the right" pressed={styleValue(selected, breakpoint, "alignSelf") === "flex-end"} onClick={() => paint({ alignSelf: "flex-end" })}><AlignRight size={15} /></AlignButton>
          </div>
          <p className="studio-hint">Drag an element to move it on this screen only. A line appears when it meets the middle or lines up with another element. Edges change one side. Corners keep the proportion. The circle above the box rotates. Zoom changes the view, not the page width.</p>
          <div className="studio-presets" role="group" aria-label="Stacking order">
            <button type="button" disabled={selected.id === document.root.id} onClick={() => change((copy) => restack(copy, selected.id, "forward", breakpoint))}>Forward</button>
            <button type="button" disabled={selected.id === document.root.id} onClick={() => change((copy) => restack(copy, selected.id, "backward", breakpoint))}>Backward</button>
          </div>
          <button type="button" onClick={() => change((copy) => updateNode(copy, selected.id, { locked: !selected.locked }, breakpoint))}>{selected.locked ? "Unlock" : "Lock"}</button>
          <button type="button" disabled={alsoSelected.length < 1} onClick={() => change((copy) => { const group = groupNodes(copy, [selectedId, ...alsoSelected]); if (group) { setSelectedId(group.id); setAlsoSelected([]); } })}>Group</button>
          <button type="button" disabled={selected.type !== "GROUP"} onClick={() => change((copy) => ungroupNode(copy, selectedId))}>Ungroup</button>
          {selected.id !== document.root.id ? <button type="button" onClick={() => { change((copy) => removeNode(copy, selected.id)); setSelectedId(document.root.id); }}>Remove</button> : null}
          <p className="studio-label">Order</p>
          <div className="studio-layers">
            <Layer node={document.root} breakpoint={breakpoint} selectedId={selectedId} onSelect={setSelectedId} onShift={(nodeId, direction) => change((copy) => shiftChild(copy, nodeId, direction))} depth={0} index={0} count={1} />
          </div>
        </aside>
      </div>
    </div>
  );
}

function shapePreset(radius: string) {
  const value = radius.trim().toLowerCase();
  if (value === "0" || value === "0px" || value === "0%") return "square";
  if (value === "16px") return "rounded";
  if (value === "50%") return "circle";
  if (value === "9999px") return "pill";
  return "";
}

function styleValue(node: NonNullable<ReturnType<typeof findNode>>, breakpoint: Breakpoint, key: string) {
  return stylesFor(node, breakpoint)[key] ?? "";
}

function columnLayout(node: EditorNode, breakpoint: Breakpoint) {
  const direction = styleValue(node, breakpoint, "flexDirection");
  if (direction === "column" || direction === "column-reverse") return true;
  if (direction === "row" || direction === "row-reverse") return false;
  return node.type !== "FLEX";
}

function axisKey(node: EditorNode, breakpoint: Breakpoint, axis: "across" | "down") {
  const column = columnLayout(node, breakpoint);
  if (axis === "across") return column ? "alignItems" : "justifyContent";
  return column ? "justifyContent" : "alignItems";
}

function axisValue(node: EditorNode, breakpoint: Breakpoint, axis: "across" | "down") {
  return styleValue(node, breakpoint, axisKey(node, breakpoint, axis));
}

function axisPatch(node: EditorNode, breakpoint: Breakpoint, axis: "across" | "down", value: string) {
  return { [axisKey(node, breakpoint, axis)]: value };
}

function without(styles: Record<string, string>, key: string) {
  const next = { ...styles };
  delete next[key];
  return next;
}

function toHex(value: string, fallback: string) {
  return /^#[0-9a-f]{6}$/i.test(value) ? value : fallback;
}

async function uploadPicture(eventId: string, file: File) {
  const body = new FormData();
  body.append("file", file);
  const response = await api.post<Envelope<{ id: string }>>(`/events/${eventId}/media`, body);
  return response.data.data.id;
}

function PictureField({ eventId, mediaId, alt, onPick, onAlt }: {
  eventId: string;
  mediaId: string;
  alt: string;
  onPick: (mediaId: string) => void;
  onAlt: (alt: string) => void;
}) {
  const queryClient = useQueryClient();
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const media = useQuery({
    queryKey: ["media", eventId],
    queryFn: async () => (await api.get<Envelope<{ id: string; mediaType: string; url: string; originalFilename: string }[]>>(`/events/${eventId}/media`)).data.data,
    retry: false,
  });
  const pictures = (media.data ?? []).filter((file) => file.mediaType === "IMAGE");
  async function send(file: File | undefined) {
    if (!file) return;
    setBusy(true);
    try {
      const uploaded = await uploadPicture(eventId, file);
      await queryClient.invalidateQueries({ queryKey: ["media", eventId] });
      onPick(uploaded);
      setMessage("");
    } catch (error) {
      setMessage(errorMessage(error, "The picture was not saved."));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <label className="picture-upload">
        {busy ? "Uploading" : mediaId ? "Replace picture" : "Upload a picture"}
        <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" aria-label="Upload a picture" disabled={busy} onChange={(event) => void send(event.target.files?.[0])} />
      </label>
      {pictures.length > 0 ? (
        <div className="picture-grid">
          {pictures.map((file) => (
            <button key={file.id} type="button" aria-label={file.originalFilename || "Use this picture"} aria-pressed={file.id === mediaId} onClick={() => onPick(file.id)}>
              <img src={file.url} alt="" />
            </button>
          ))}
        </div>
      ) : null}
      <label>Description
        <input value={alt} placeholder="Describe the picture" onChange={(event) => onAlt(event.target.value)} />
      </label>
      {message ? <p className="error-line">{message}</p> : null}
    </>
  );
}

const GRADIENTS = [
  { name: "Tide", value: "linear-gradient(160deg, #1b6c80, #f4efe8)" },
  { name: "Dusk", value: "linear-gradient(180deg, #121417, #1b6c80)" },
  { name: "Paper", value: "linear-gradient(135deg, #ffffff, #e7ecee)" },
];

function solidBackground(value: string) {
  return value.includes("gradient") ? "" : value;
}

function readGradient(value: string) {
  const match = value.trim().match(/^linear-gradient\(\s*(?:(\d{1,3})deg\s*,\s*)?(#[0-9a-fA-F]{3,8})\s*,\s*(#[0-9a-fA-F]{3,8})\s*\)$/);
  if (!match) return null;
  return {
    angle: Math.min(360, Number(match[1] ?? 160)),
    start: toHex(expandHex(match[2]), "#1b6c80"),
    end: toHex(expandHex(match[3]), "#f4efe8"),
  };
}

function writeGradient(angle: number, start: string, end: string) {
  return `linear-gradient(${Math.round(Math.min(360, Math.max(0, angle)))}deg, ${start}, ${end})`;
}

function expandHex(value: string) {
  const hex = value.trim();
  if (/^#[0-9a-fA-F]{3}$/.test(hex)) return `#${hex[1]}${hex[1]}${hex[2]}${hex[2]}${hex[3]}${hex[3]}`.toLowerCase();
  if (/^#[0-9a-fA-F]{6}$/.test(hex)) return hex.toLowerCase();
  return "";
}

function GradientField({ value, onChange }: { value: string; onChange: (value: string, coalesce: boolean) => void }) {
  const parsed = readGradient(value);
  const angle = parsed?.angle ?? 160;
  const start = parsed?.start ?? "#1b6c80";
  const end = parsed?.end ?? "#f4efe8";
  const dragging = useRef(false);
  function write(next: { angle?: number; start?: string; end?: string }, coalesce: boolean) {
    onChange(writeGradient(next.angle ?? angle, next.start ?? start, next.end ?? end), coalesce);
  }
  return (
    <div className="studio-gradient">
      <p className="studio-label">Gradient</p>
      <div className="studio-presets" role="group" aria-label="Gradient">
        {GRADIENTS.map((preset) => (
          <button key={preset.name} type="button" aria-pressed={value.trim() === preset.value} onClick={() => onChange(preset.value, false)}>
            <span className="studio-swatch" style={{ background: preset.value }} />
            {preset.name}
          </button>
        ))}
      </div>
      {parsed ? <span className="studio-gradient-preview" style={{ background: value }} /> : null}
      <label>Angle
        <span className="studio-slider">
          <input type="range" min={0} max={360} aria-label="Gradient angle" value={angle} onPointerDown={() => { dragging.current = false; }} onChange={(event) => { write({ angle: Number(event.target.value) }, dragging.current); dragging.current = true; }} />
          <span>{angle}°</span>
        </span>
      </label>
      <ColourField label="Start colour" value={start} fallback="#1b6c80" onChange={(color, coalesce) => write({ start: color }, coalesce)} />
      <ColourField label="End colour" value={end} fallback="#f4efe8" onChange={(color, coalesce) => write({ end: color }, coalesce)} />
    </div>
  );
}

function OpacityField({ value, onChange }: { value: string; onChange: (value: string, coalesce: boolean) => void }) {
  const parsed = Number.parseFloat(value);
  const percent = Number.isFinite(parsed) ? Math.round(Math.min(1, Math.max(0, parsed)) * 100) : 100;
  const dragging = useRef(false);
  return (
    <label>Opacity
      <span className="studio-slider">
        <input
          type="range"
          min={0}
          max={100}
          aria-label="Opacity"
          value={percent}
          onPointerDown={() => { dragging.current = false; }}
          onChange={(event) => {
            const next = Number(event.target.value);
            const stored = next === 100 ? "1" : next === 0 ? "0" : (next / 100).toFixed(2).replace(/0$/, "");
            onChange(stored, dragging.current);
            dragging.current = true;
          }}
        />
        <span>{percent}%</span>
      </span>
    </label>
  );
}

function ColourField({ label, value, fallback, onChange }: { label: string; value: string; fallback: string; onChange: (value: string, coalesce: boolean) => void }) {
  const dragging = useRef(false);
  return (
    <label>{label}
      <span className="color-row">
        <input type="color" aria-label={label} value={toHex(value, fallback)} onPointerDown={() => { dragging.current = false; }} onChange={(event) => { onChange(event.target.value, dragging.current); dragging.current = true; }} />
        <input aria-label={`${label} value`} value={value} placeholder={fallback} onChange={(event) => onChange(event.target.value, false)} />
      </span>
    </label>
  );
}

function AlignButton({ label, pressed, onClick, children }: { label: string; pressed: boolean; onClick: () => void; children: ReactNode }) {
  return <button type="button" aria-label={label} title={label} aria-pressed={pressed} onClick={onClick}>{children}</button>;
}

function toDatetimeLocal(value: string) {
  if (!value) return "";
  const parsed = Date.parse(value);
  if (Number.isNaN(parsed)) {
    return value.length >= 16 ? value.slice(0, 16) : value;
  }
  const date = new Date(parsed);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function fromDatetimeLocal(value: string) {
  if (!value) return "";
  return value.length === 16 ? `${value}:00` : value;
}

function CarouselSlides({ eventId, slides, onUpload, onPick, onShift, onRemove }: {
  eventId: string;
  slides: EditorNode[];
  onUpload: (files: File[], slideId?: string) => Promise<void>;
  onPick: (mediaId: string) => void;
  onShift: (slideId: string, direction: -1 | 1) => void;
  onRemove: (slideId: string) => void;
}) {
  const queryClient = useQueryClient();
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const media = useQuery({
    queryKey: ["media", eventId],
    queryFn: async () => (await api.get<Envelope<{ id: string; mediaType: string; url: string; originalFilename: string }[]>>(`/events/${eventId}/media`)).data.data,
    retry: false,
  });
  const pictures = (media.data ?? []).filter((file) => file.mediaType === "IMAGE");
  const urls = new Map(pictures.map((file) => [file.id, file.url]));
  async function send(files: File[], slideId?: string) {
    if (files.length === 0) return;
    setBusy(true);
    try {
      await onUpload(files, slideId);
      await queryClient.invalidateQueries({ queryKey: ["media", eventId] });
      setMessage("");
    } catch (error) {
      setMessage(errorMessage(error, "The picture was not saved."));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <p className="studio-label">Slides</p>
      {slides.map((slide, index) => {
        const mediaId = String(slide.props.mediaId ?? "");
        const url = urls.get(mediaId);
        return (
          <div key={slide.id} className="studio-slide">
            {url ? <img src={url} alt="" /> : <span>Empty</span>}
            <label className="picture-upload">
              {busy ? "Uploading" : mediaId ? "Replace" : "Upload"}
              <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" aria-label={`${mediaId ? "Replace" : "Upload"} slide ${index + 1}`} disabled={busy} onChange={(event) => { const file = event.target.files?.[0]; event.target.value = ""; if (file) void send([file], slide.id); }} />
            </label>
            <button type="button" aria-label={`Move slide ${index + 1} earlier`} disabled={index === 0} onClick={() => onShift(slide.id, -1)}><ChevronUp size={14} /></button>
            <button type="button" aria-label={`Move slide ${index + 1} later`} disabled={index === slides.length - 1} onClick={() => onShift(slide.id, 1)}><ChevronDown size={14} /></button>
            <button type="button" aria-label={`Remove slide ${index + 1}`} onClick={() => onRemove(slide.id)}><Trash2 size={14} /></button>
          </div>
        );
      })}
      <label className="picture-upload">
        {busy ? "Uploading" : "Upload pictures"}
        <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple aria-label="Upload pictures for the carousel" disabled={busy} onChange={(event) => { const files = [...(event.target.files ?? [])]; event.target.value = ""; void send(files); }} />
      </label>
      {pictures.length > 0 ? (
        <div className="picture-grid">
          {pictures.map((file) => (
            <button key={file.id} type="button" aria-label={`Add ${file.originalFilename || "this picture"} as a slide`} onClick={() => onPick(file.id)}>
              <img src={file.url} alt="" />
            </button>
          ))}
        </div>
      ) : null}
      {message ? <p className="error-line">{message}</p> : null}
    </>
  );
}

function Layer({ node, breakpoint, selectedId, onSelect, onShift, depth, index, count }: {
  node: EditorNode;
  breakpoint: Breakpoint;
  selectedId: string;
  onSelect: (id: string) => void;
  onShift: (id: string, direction: -1 | 1) => void;
  depth: number;
  index: number;
  count: number;
}) {
  const hidden = stylesFor(node, breakpoint).display === "none";
  return (
    <div>
      <div className={depth > 0 ? "studio-layer" : "studio-layer is-root"} style={{ paddingLeft: depth * 12 }}>
        <button type="button" aria-pressed={selectedId === node.id} onClick={() => onSelect(node.id)}>{node.name}{hidden ? " · Hidden" : ""}</button>
        {depth > 0 ? (
          <>
            <button type="button" aria-label={`Move ${node.name} earlier`} disabled={index === 0} onClick={() => onShift(node.id, -1)}><ChevronUp size={14} /></button>
            <button type="button" aria-label={`Move ${node.name} later`} disabled={index === count - 1} onClick={() => onShift(node.id, 1)}><ChevronDown size={14} /></button>
          </>
        ) : null}
      </div>
      {node.children.map((child, childIndex) => (
        <Layer key={child.id} node={child} breakpoint={breakpoint} selectedId={selectedId} onSelect={onSelect} onShift={onShift} depth={depth + 1} index={childIndex} count={node.children.length} />
      ))}
    </div>
  );
}
