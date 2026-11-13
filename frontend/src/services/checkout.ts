import { api } from "./api";

export type StartedPayment = {
  id: string;
  status: string;
  bypass?: boolean;
  keyId?: string;
  gatewayOrderId?: string | null;
  amountPaise: number;
  currency: string;
};

type RazorpayCheckout = { open: () => void };

export async function settlePayment(payment: StartedPayment) {
  if (payment.bypass || payment.status === "PAID" || !payment.keyId || !payment.gatewayOrderId) {
    return payment;
  }
  await loadRazorpay();
  await new Promise<void>((resolve, reject) => {
    const Razorpay = (window as unknown as { Razorpay: new (options: object) => RazorpayCheckout }).Razorpay;
    const checkout = new Razorpay({
      key: payment.keyId,
      order_id: payment.gatewayOrderId,
      amount: payment.amountPaise,
      currency: payment.currency,
      handler: () => resolve(),
      modal: { ondismiss: () => reject(new Error("The checkout was closed before payment.")) },
    });
    checkout.open();
  });
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const view = (await api.get<{ data: StartedPayment }>(`/payments/${payment.id}`)).data.data;
    if (view.status === "PAID") return view;
    await new Promise((resolve) => window.setTimeout(resolve, 1500));
  }
  return { ...payment, status: "PENDING" };
}

function loadRazorpay() {
  if ("Razorpay" in window) return Promise.resolve();
  return new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Razorpay checkout did not load."));
    document.body.appendChild(script);
  });
}
