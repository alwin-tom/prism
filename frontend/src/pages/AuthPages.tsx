import { useEffect, useRef, useState, type ClipboardEvent, type KeyboardEvent, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Check, Globe, MessageCircle, Receipt, Zap } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { Mark } from "../components/shell/Mark";
import { usePageSeo } from "../seo/usePageSeo";
import { api, errorMessage } from "../services/api";
import type { Account, Envelope } from "../types";

const phone = z.object({ phoneNumber: z.string().min(10, "Enter a mobile number.") });
const codeSchema = z.object({
  otp: z.string().regex(/^\d{6}$/, "Enter the six-digit code."),
  fullName: z.string().optional(),
});

export function LoginPage() {
  usePageSeo({
    title: "Sign in",
    description: "Sign in to WebAux to manage your event websites, hosting days, and guest replies.",
    path: "/login",
  });
  return <AuthScreen title="Sign in" mode="login" />;
}

export function RegisterPage() {
  usePageSeo({
    title: "Create an account",
    description: "Create a WebAux account and publish an event website at its own address for ₹500 a day.",
    path: "/register",
  });
  return <AuthScreen title="Create an account" mode="register" />;
}

export function StaffLoginPage() {
  usePageSeo({
    title: "Staff sign in",
    description: "Staff access for WebAux admins and reviewers.",
    path: "/staff",
    noIndex: true,
  });
  const form = useForm<{ phoneNumber: string; password: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: async (values: { phoneNumber: string; password: string }) => {
      const response = await api.post<Envelope<Account>>("/auth/login", values);
      return response.data.data;
    },
    onSuccess: (account) => {
      queryClient.setQueryData(["me"], account);
      navigate("/admin");
    },
  });
  return (
    <AuthShell>
      <header className="auth-form-header">
        <h1>Staff sign in</h1>
        <p>Admins and reviewers use a password. Customers use a WhatsApp code.</p>
      </header>
      <form className="auth-form" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
        <label className="auth-field">
          <span className="auth-field-label">Mobile number</span>
          <input {...form.register("phoneNumber")} autoComplete="username" className="auth-input" />
        </label>
        <label className="auth-field">
          <span className="auth-field-label">Password</span>
          <input type="password" {...form.register("password")} autoComplete="current-password" className="auth-input" />
        </label>
        {mutation.isError ? <p className="error-line">{errorMessage(mutation.error, "Those details were not accepted.")}</p> : null}
        <button type="submit" className="auth-submit" disabled={mutation.isPending}>
          <span>{mutation.isPending ? "Signing in" : "Sign in"}</span>
          <ArrowRight size={16} aria-hidden />
        </button>
      </form>
      <p className="auth-secondary">
        <Link to="/login">Customer sign in</Link>
      </p>
    </AuthShell>
  );
}

function AuthScreen({ title, mode }: { title: string; mode: "login" | "register" }) {
  const requestForm = useForm<{ phoneNumber: string }>({ resolver: zodResolver(phone) });
  const verifyForm = useForm<z.infer<typeof codeSchema>>({ resolver: zodResolver(codeSchema), defaultValues: { otp: "" } });
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [resendIn, setResendIn] = useState(0);
  const phoneValue = requestForm.watch("phoneNumber");

  useEffect(() => {
    if (resendIn <= 0) return;
    const id = window.setTimeout(() => setResendIn((value) => value - 1), 1000);
    return () => window.clearTimeout(id);
  }, [resendIn]);

  const request = useMutation({
    mutationFn: async (phoneNumber: string) => api.post("/auth/otp/request", { phoneNumber: normalizePhone(phoneNumber) }),
    onSuccess: () => setResendIn(45),
  });
  const verify = useMutation({
    mutationFn: async (values: z.infer<typeof codeSchema> & { phoneNumber: string }) => {
      const response = await api.post<Envelope<Account>>("/auth/otp/verify", {
        ...values,
        phoneNumber: normalizePhone(values.phoneNumber),
      });
      return response.data.data;
    },
    onSuccess: (account) => {
      queryClient.setQueryData(["me"], account);
      navigate(account.roles.includes("ADMIN") || account.roles.includes("REVIEWER") ? "/admin" : "/app");
    },
  });

  return (
    <AuthShell>
      <header className="auth-form-header">
        <div className="auth-mobile-brand">
          <Mark />
          <strong>WebAux</strong>
        </div>
        <h1>{title}</h1>
        <p>
          WebAux hosts the event website. <span>₹500 for each day</span> it stays up.
        </p>
      </header>

      <form
        className="auth-form"
        onSubmit={requestForm.handleSubmit((values) => request.mutate(values.phoneNumber))}
      >
        <div className="auth-field">
          <div className="auth-field-head">
            <label htmlFor="auth-phone" className="auth-field-label">Mobile number</label>
            <span className="auth-field-meta">SMS / WhatsApp OTP</span>
          </div>
          <div className="auth-phone">
            <span className="auth-phone-prefix" aria-hidden>+91</span>
            <input
              id="auth-phone"
              {...requestForm.register("phoneNumber")}
              inputMode="tel"
              autoComplete="tel"
              className="auth-input auth-phone-input"
              placeholder="97478 20848"
            />
          </div>
          {requestForm.formState.errors.phoneNumber ? (
            <p className="error-line">{requestForm.formState.errors.phoneNumber.message}</p>
          ) : null}
        </div>
        <button type="submit" className="auth-secondary-btn" disabled={request.isPending || resendIn > 0}>
          {request.isPending ? "Sending" : resendIn > 0 ? `Resend (${resendIn}s)` : "Send a code"}
        </button>
        {request.isError ? <p className="error-line">{errorMessage(request.error, "The code was not sent.")}</p> : null}
        {request.isSuccess ? (
          <p className="auth-sent">
            <Check size={14} aria-hidden />
            {phoneValue
              ? `If ${displayPhone(phoneValue)} can receive a code, it is on its way.`
              : "If the number can receive a code, it is on its way."}
          </p>
        ) : null}
      </form>

      <form
        className="auth-form"
        onSubmit={verifyForm.handleSubmit((values) =>
          verify.mutate({ ...values, phoneNumber: requestForm.getValues("phoneNumber") }),
        )}
      >
        {mode === "register" ? (
          <label className="auth-field">
            <span className="auth-field-label">Your name</span>
            <input {...verifyForm.register("fullName")} autoComplete="name" className="auth-input" />
          </label>
        ) : null}
        <div className="auth-field">
          <div className="auth-field-head">
            <label htmlFor="auth-otp-0" className="auth-field-label">Six-digit code</label>
          </div>
          <OtpBoxes
            value={verifyForm.watch("otp") ?? ""}
            onChange={(next) => verifyForm.setValue("otp", next, { shouldValidate: true, shouldDirty: true })}
          />
          <input type="hidden" {...verifyForm.register("otp")} />
          {verifyForm.formState.errors.otp ? <p className="error-line">{verifyForm.formState.errors.otp.message}</p> : null}
        </div>
        {verify.isError ? <p className="error-line">{errorMessage(verify.error, "That code was not accepted.")}</p> : null}
        <button type="submit" className="auth-submit" disabled={verify.isPending}>
          <span>{verify.isPending ? "Checking" : mode === "register" ? "Create the account" : "Sign in"}</span>
          <ArrowRight size={16} aria-hidden />
        </button>
      </form>

      <div className="auth-footer-links">
        <p className="auth-secondary">
          {mode === "login" ? (
            <>
              Don&apos;t have an account? <Link to="/register">Create an account</Link>
            </>
          ) : (
            <>
              Already have an account? <Link to="/login">Sign in</Link>
            </>
          )}
          <span className="auth-dot" aria-hidden>·</span>
          <Link to="/staff">Staff sign in</Link>
        </p>
      </div>
    </AuthShell>
  );
}

function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <aside className="auth-side" aria-label="WebAux">
        <div className="auth-side-grid" aria-hidden />
        <div className="auth-side-orb auth-side-orb-a" aria-hidden />
        <div className="auth-side-orb auth-side-orb-b" aria-hidden />
        <header className="auth-brand">
          <Link to="/login" className="auth-brand-link">
            <Mark />
            <span className="auth-brand-copy">
              <span className="auth-brand-row">
                <strong>WebAux</strong>
                <span className="auth-brand-pill">Cloud</span>
              </span>
              <span className="auth-brand-tag">Event sites infrastructure</span>
            </span>
          </Link>
        </header>
        <div className="auth-side-body">
          <p className="auth-status-pill">
            <span className="auth-status-dot" aria-hidden />
            Metered at exactly ₹500 / day
            <span aria-hidden>·</span>
            <span>No staging fees</span>
          </p>
          <h2 className="auth-side-title">A site for the event, at its own address, for as many days as you pay for.</h2>
          <p className="auth-side-copy">
            Build the invitation, take replies, and keep the page live only while you host it. Pay for the days you need—nothing more.
          </p>
          <ul className="auth-feature-grid">
            <li>
              <span className="auth-feature-icon is-teal"><Zap size={16} aria-hidden /></span>
              <span>
                <strong>Live in minutes</strong>
                <small>Publish after review and payment</small>
              </span>
            </li>
            <li>
              <span className="auth-feature-icon is-amber"><MessageCircle size={16} aria-hidden /></span>
              <span>
                <strong>Guest replies</strong>
                <small>Collect RSVPs on the public page</small>
              </span>
            </li>
            <li>
              <span className="auth-feature-icon is-coral"><Receipt size={16} aria-hidden /></span>
              <span>
                <strong>Metered hosting</strong>
                <small>₹500 a day, only while live</small>
              </span>
            </li>
            <li>
              <span className="auth-feature-icon is-green"><Globe size={16} aria-hidden /></span>
              <span>
                <strong>Own address</strong>
                <small>webaux.app/your-event</small>
              </span>
            </li>
          </ul>
        </div>
        <footer className="auth-side-foot">
          <span>© {new Date().getFullYear()} WebAux</span>
          <span className="auth-edge">
            <span className="auth-status-dot" aria-hidden />
            Ready when you are
          </span>
        </footer>
      </aside>
      <main className="auth-sheet">
        <div className="auth-card">{children}</div>
      </main>
    </div>
  );
}

function OtpBoxes({ value, onChange }: { value: string; onChange: (next: string) => void }) {
  const refs = useRef<Array<HTMLInputElement | null>>([]);
  const digits = Array.from({ length: 6 }, (_, index) => value[index] ?? "");

  const write = (next: string[]) => onChange(next.join("").replace(/\D/g, "").slice(0, 6));

  const onPaste = (event: ClipboardEvent<HTMLInputElement>) => {
    event.preventDefault();
    const text = event.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
    if (!text) return;
    const next = Array.from({ length: 6 }, (_, index) => text[index] ?? "");
    write(next);
    refs.current[Math.min(text.length, 5)]?.focus();
  };

  const onKeyDown = (index: number, event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Backspace" && !digits[index] && index > 0) {
      event.preventDefault();
      const next = [...digits];
      next[index - 1] = "";
      write(next);
      refs.current[index - 1]?.focus();
    }
  };

  return (
    <div className="auth-otp" role="group" aria-label="Six-digit code">
      {digits.map((digit, index) => (
        <input
          key={index}
          id={index === 0 ? "auth-otp-0" : undefined}
          ref={(node) => {
            refs.current[index] = node;
          }}
          className="auth-otp-cell"
          inputMode="numeric"
          autoComplete={index === 0 ? "one-time-code" : "off"}
          maxLength={1}
          value={digit}
          placeholder="·"
          aria-label={`Digit ${index + 1}`}
          onPaste={onPaste}
          onKeyDown={(event) => onKeyDown(index, event)}
          onChange={(event) => {
            const raw = event.target.value.replace(/\D/g, "");
            if (raw.length > 1) {
              const next = Array.from({ length: 6 }, (_, i) => raw[i] ?? "");
              write(next);
              refs.current[Math.min(raw.length, 5)]?.focus();
              return;
            }
            const next = [...digits];
            next[index] = raw.slice(-1);
            write(next);
            if (raw && index < 5) refs.current[index + 1]?.focus();
          }}
          onFocus={(event) => event.currentTarget.select()}
        />
      ))}
    </div>
  );
}

function normalizePhone(value: string) {
  const trimmed = value.trim().replace(/[\s-]/g, "");
  if (!trimmed) return trimmed;
  if (trimmed.startsWith("+")) return trimmed;
  if (/^91\d{10}$/.test(trimmed)) return `+${trimmed}`;
  if (/^\d{10}$/.test(trimmed)) return `+91${trimmed}`;
  return trimmed;
}

function displayPhone(value: string) {
  const normalized = normalizePhone(value);
  if (normalized.startsWith("+91") && normalized.length === 13) {
    return `+91 ${normalized.slice(3, 8)} ${normalized.slice(8)}`;
  }
  return normalized;
}
