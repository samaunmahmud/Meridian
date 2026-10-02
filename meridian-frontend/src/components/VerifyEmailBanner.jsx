import { useState } from "react";
import { resendVerification } from "../lib/api";
import { showToast } from "../lib/toast";
import Icon from "./Icon";

const DISMISSED_KEY = "meridian_verify_banner_dismissed";

function dismissedThisSession() {
  try {
    return sessionStorage.getItem(DISMISSED_KEY) === "1";
  } catch {
    return false;
  }
}

// Shown to signed-in users whose email address isn't confirmed yet, as a slim notice card that can be
// put away until the next visit. Nothing is blocked (this is a practice account); confirming just makes
// sure a password reset email will reach them.
export default function VerifyEmailBanner({ email }) {
  const [sending, setSending] = useState(false);
  const [hidden, setHidden] = useState(dismissedThisSession);

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

  function dismiss() {
    setHidden(true);
    try {
      sessionStorage.setItem(DISMISSED_KEY, "1");
    } catch {
      // storage unavailable: it just comes back on the next page load
    }
  }

  if (hidden) return null;

  return (
    <div role="status" className="mx-4 sm:mx-6 lg:mx-8 mb-4 max-w-[1240px] flex items-center gap-3 rounded-2xl bg-panel pl-3 pr-1.5 py-1.5 text-sm">
      <span className="w-8 h-8 rounded-full bg-accent-dim text-accent flex items-center justify-center shrink-0" aria-hidden="true">
        <Icon name="mail" size={15} />
      </span>
      <span className="flex-1 min-w-0 truncate">
        <span className="sr-only">Please confirm your email address — we sent a link to {email}.</span>
        <span aria-hidden="true">
          Confirm your email<span className="hidden sm:inline"> — we sent a link to <span className="font-medium">{email}</span></span>
        </span>
      </span>
      <button
        type="button"
        onClick={resend}
        disabled={sending}
        className="shrink-0 h-8 px-3 rounded-full text-accent font-semibold hover:bg-panel-2 disabled:opacity-50"
      >
        {sending ? "Sending…" : "Resend link"}
      </button>
      <button
        type="button"
        onClick={dismiss}
        aria-label="Hide this notice"
        className="shrink-0 w-8 h-8 rounded-full text-dim hover:text-bone hover:bg-panel-2 flex items-center justify-center"
      >
        <Icon name="x" size={15} />
      </button>
    </div>
  );
}
