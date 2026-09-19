import { useState } from "react";
import { resendVerification } from "../lib/api";
import { showToast } from "../lib/toast";
import Icon from "./Icon";

// Shown to signed-in users whose email address isn't confirmed yet. Nothing is
// blocked (this is a practice account); confirming just makes sure a password
// reset email will reach them.
export default function VerifyEmailBanner({ email }) {
  const [sending, setSending] = useState(false);

  async function resend() {
    setSending(true);
    try {
      const response = await resendVerification();
      showToast("success", response.message);
    } catch (err) {
      showToast("error", err.message);
    } finally {
      setSending(false);
    }
  }

  return (
    <div
      role="status"
      className="mx-4 sm:mx-6 lg:mx-8 mb-4 flex flex-wrap items-center gap-x-3 gap-y-2 rounded-[14px] border border-line bg-accent-dim px-4 py-3 text-sm max-w-[1240px]"
    >
      <Icon name="mail" size={16} className="text-accent shrink-0" />
      <span className="text-bone">
        Please confirm your email address — we sent a link to <span className="font-medium">{email}</span>.
      </span>
      <button
        type="button"
        onClick={resend}
        disabled={sending}
        className="ml-auto text-accent font-medium hover:underline disabled:opacity-50"
      >
        {sending ? "Sending…" : "Resend link"}
      </button>
    </div>
  );
}
