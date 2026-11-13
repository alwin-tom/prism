import type { ReactNode } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { Mark } from "../components/shell/Mark";
import { Button, Field } from "../components/ui";
import { api, errorMessage } from "../services/api";
import type { Account, Envelope } from "../types";

const phone = z.object({ phoneNumber: z.string().min(10, "Enter a mobile number.") });
const codeSchema = z.object({
  otp: z.string().regex(/^\d{6}$/, "Enter the six-digit code."),
  fullName: z.string().optional(),
});

export function LoginPage() {
  return <AuthScreen title="Sign in" mode="login" />;
}

export function RegisterPage() {
  return <AuthScreen title="Create an account" mode="register" />;
}

export function StaffLoginPage() {
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
    <AuthFrame>
      <h1>Staff sign in</h1>
      <p>Admins and reviewers use a password. Customers use a WhatsApp code.</p>
      <form className="stack" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
        <Field label="Mobile number"><input {...form.register("phoneNumber")} autoComplete="username" /></Field>
        <Field label="Password"><input type="password" {...form.register("password")} autoComplete="current-password" /></Field>
        {mutation.isError ? <p className="error-line">{errorMessage(mutation.error, "Those details were not accepted.")}</p> : null}
        <Button type="submit" disabled={mutation.isPending}>Sign in</Button>
      </form>
    </AuthFrame>
  );
}

function AuthScreen({ title, mode }: { title: string; mode: "login" | "register" }) {
  const requestForm = useForm<{ phoneNumber: string }>({ resolver: zodResolver(phone) });
  const verifyForm = useForm<z.infer<typeof codeSchema>>({ resolver: zodResolver(codeSchema) });
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const request = useMutation({
    mutationFn: async (phoneNumber: string) => api.post("/auth/otp/request", { phoneNumber }),
  });
  const verify = useMutation({
    mutationFn: async (values: z.infer<typeof codeSchema> & { phoneNumber: string }) => {
      const response = await api.post<Envelope<Account>>("/auth/otp/verify", values);
      return response.data.data;
    },
    onSuccess: (account) => {
      queryClient.setQueryData(["me"], account);
      navigate(account.roles.includes("ADMIN") || account.roles.includes("REVIEWER") ? "/admin" : "/app");
    },
  });
  return (
    <AuthFrame>
      <h1>{title}</h1>
      <p>WebAux hosts the event website. ₹500 for each day it stays up.</p>
      <form className="stack" onSubmit={requestForm.handleSubmit((values) => request.mutate(values.phoneNumber))}>
        <Field label="Mobile number"><input {...requestForm.register("phoneNumber")} inputMode="tel" autoComplete="tel" /></Field>
        <Button type="submit" tone="secondary" disabled={request.isPending}>Send a code</Button>
        {request.isError ? <p className="error-line">{errorMessage(request.error, "The code was not sent.")}</p> : null}
        {request.isSuccess ? <p>If the number can receive a code, it is on its way. It lasts five minutes.</p> : null}
      </form>
      <form className="stack" onSubmit={verifyForm.handleSubmit((values) => verify.mutate({ ...values, phoneNumber: requestForm.getValues("phoneNumber") }))}>
        {mode === "register" ? <Field label="Your name"><input {...verifyForm.register("fullName")} autoComplete="name" /></Field> : null}
        <Field label="Six-digit code"><input {...verifyForm.register("otp")} inputMode="numeric" autoComplete="one-time-code" /></Field>
        {verifyForm.formState.errors.otp ? <p className="error-line">{verifyForm.formState.errors.otp.message}</p> : null}
        {verify.isError ? <p className="error-line">{errorMessage(verify.error, "That code was not accepted.")}</p> : null}
        <Button type="submit" disabled={verify.isPending}>{mode === "register" ? "Create the account" : "Sign in"}</Button>
      </form>
      <p style={{ marginTop: 18 }}>
        {mode === "login" ? <Link to="/register">Create an account</Link> : <Link to="/login">I already have an account</Link>}
        {" · "}
        <Link to="/staff">Staff sign in</Link>
      </p>
    </AuthFrame>
  );
}

function AuthFrame({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <aside className="auth-side">
        <div className="brand"><Mark /><span className="brand-copy"><strong>WebAux</strong><span>Event sites</span></span></div>
        <p>A site for the event, at its own address, for as many days as you pay for.</p>
      </aside>
      <div className="auth-sheet">
        <div className="auth-card">{children}</div>
      </div>
    </div>
  );
}
