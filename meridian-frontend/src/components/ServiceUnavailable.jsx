import { useEffect } from "react";
import { LogoMark } from "./Logo";

const RETRY_EVERY_MS = 15000;

// Shown instead of the login page when the server could not say whether anyone is signed in (it is down,
// or its database is): a login form would only fail, and would suggest the session had ended.
export default function ServiceUnavailable({ onRetry, retrying }) {
  useEffect(() => {
    const timer = setInterval(onRetry, RETRY_EVERY_MS);
    return () => clearInterval(timer);
  }, [onRetry]);

  return (
    <main className="min-h-screen bg-ink text-bone flex items-center justify-center px-4">
      <div className="max-w-sm text-center">
        <LogoMark size={40} className="text-accent mx-auto mb-5" />
        <h1 className="font-display text-2xl mb-2">Meridian is unavailable</h1>
        <p className="text-sm text-muted mb-6">
          We cannot reach the server right now. Your account and your portfolio are safe. This page tries again on
          its own every few seconds.
        </p>
        <button
          type="button"
          onClick={onRetry}
          disabled={retrying}
          className="h-10 px-5 rounded-full bg-accent text-accent-ink font-semibold disabled:opacity-60"
        >
          {retrying ? "Trying…" : "Try again"}
        </button>
      </div>
    </main>
  );
}
