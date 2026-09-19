import { useEffect, useState } from "react";
import { forgotPassword, login, register, resetPassword } from "../lib/api";
import Icon from "./Icon";
import Logo from "./Logo";
import ThemeToggle from "./ThemeToggle";

const FEATURES = [
  { icon: "pulse", text: "Live prices over WebSocket" },
  { icon: "swap", text: "USD · EUR · GBP wallets" },
  { icon: "trend", text: "Market, limit & stop-loss orders" },
];

// Decorative globe: concentric meridians and parallels, stroke-only so it
// picks up the theme colours.
function Globe() {
  const rx = [272, 208, 136, 64];
  return (
    <svg
      className="absolute -right-40 -bottom-44 pointer-events-none"
      // Fade the globe out on its left side so its lines never run under the copy.
      style={{
        maskImage: "linear-gradient(to right, transparent 20%, black 62%)",
        WebkitMaskImage: "linear-gradient(to right, transparent 20%, black 62%)",
      }}
      width="640"
      height="640"
      viewBox="0 0 640 640"
      fill="none"
      strokeWidth="1.5"
      aria-hidden="true"
    >
      <circle cx="320" cy="320" r="319" stroke="var(--c-line)" />
      {rx.map((r, i) => (
        <ellipse key={r} cx="320" cy="320" rx={r} ry="319" stroke="var(--c-accent)" opacity={0.55 - i * 0.07} />
      ))}
      <path d="M1 320h638" stroke="var(--c-accent)" opacity="0.5" />
      <path d="M43 160h554M43 480h554" stroke="var(--c-accent)" opacity="0.28" />
    </svg>
  );
}

function Field({ label, icon, trailing, ...inputProps }) {
  return (
    <div>
      <label className="text-[13px] font-medium block mb-2">{label}</label>
      <div className="flex items-center gap-3 h-[52px] px-4 rounded-[14px] bg-panel border border-line focus-within:border-accent transition-colors">
        <Icon name={icon} size={18} className="text-dim" />
        <input
          {...inputProps}
          className="flex-1 min-w-0 bg-transparent outline-none text-[15px] placeholder:text-dim"
        />
        {trailing}
      </div>
    </div>
  );
}

// Modes: "login" and "register" (the two tabs), "forgot" (ask for a reset
// link) and "reset" (choose a new password; only when the page was opened from
// the link in the reset email, which App passes in as `resetToken`).
export default function AuthPage({ onAuthenticated, resetToken = null, onResetDone, notice = null }) {
  const [mode, setMode] = useState(resetToken ? "reset" : "login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState(null);
  const [info, setInfo] = useState(null);

  // `notice` is the result of opening an email link (e.g. "Your email address
  // is confirmed."); it can arrive after this page has already appeared.
  useEffect(() => {
    if (!notice) return;
    if (notice.kind === "error") setError(notice.message);
    else setInfo(notice.message);
  }, [notice]);
  const [submitting, setSubmitting] = useState(false);

  function goTo(next) {
    setMode(next);
    setError(null);
    setInfo(null);
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setInfo(null);
    setSubmitting(true);
    try {
      if (mode === "forgot") {
        const response = await forgotPassword(email);
        setInfo(response.message);
      } else if (mode === "reset") {
        if (password !== confirm) {
          setError("The two passwords don't match.");
          return;
        }
        await resetPassword(resetToken, password);
        setPassword("");
        setConfirm("");
        setMode("login");
        setInfo("Your password has been changed. Log in with the new one.");
        onResetDone?.();
      } else {
        const response = mode === "login" ? await login(email, password) : await register(email, password);
        onAuthenticated(response);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  const isLogin = mode === "login";
  const isTab = mode === "login" || mode === "register";
  const COPY = {
    login: { title: "Welcome back", subtitle: "Log in to your Meridian account.", button: "Log in" },
    register: { title: "Create your account", subtitle: "Start with $10,000 in virtual cash.", button: "Create account" },
    forgot: {
      title: "Reset your password",
      subtitle: "Enter your email and we'll send you a link to choose a new password.",
      button: "Send reset link",
    },
    reset: { title: "Choose a new password", subtitle: "Use at least 8 characters. You'll be signed out on any other device.", button: "Change password" },
  }[mode];

  return (
    <div className="min-h-screen grid grid-cols-1 lg:grid-cols-2 bg-ink text-bone">
      {/* Left: brand panel */}
      <div className="hidden lg:flex flex-col p-14 relative overflow-hidden bg-panel border-r border-line">
        <Globe />
        <div className="relative z-10">
          <Logo size={30} />
        </div>

        <div className="relative z-10 mt-auto mb-10 max-w-[520px]">
          <h2 className="font-display font-normal text-[52px] xl:text-[60px] leading-[1.05]" style={{ letterSpacing: "-0.04em" }}>
            Invest in
            <br />
            <em className="text-accent italic">every currency.</em>
          </h2>
          <p className="text-muted text-base leading-relaxed mt-5 max-w-[440px]">
            Stocks, crypto and multi-currency wallets in one calm place. Practice with $10,000 in virtual cash.
          </p>
          <ul className="mt-8 space-y-3">
            {FEATURES.map((f) => (
              <li key={f.text} className="flex items-center gap-3 text-sm text-muted">
                <span className="w-8 h-8 rounded-full bg-accent-dim text-accent flex items-center justify-center">
                  <Icon name={f.icon} size={15} strokeWidth={2} />
                </span>
                {f.text}
              </li>
            ))}
          </ul>
        </div>
      </div>

      {/* Right: form */}
      <div className="relative flex items-center justify-center p-6 sm:p-8">
        <div className="absolute top-6 right-6 sm:top-8 sm:right-8">
          <ThemeToggle />
        </div>

        <div className="w-full max-w-[400px]">
          <div className="lg:hidden mb-10">
            <Logo size={26} />
          </div>

          {isTab ? (
          <div role="tablist" className="grid grid-cols-2 gap-1 p-1 rounded-[14px] bg-panel-2 mb-7">
            {[
              { key: "login", label: "Log in" },
              { key: "register", label: "Sign up" },
            ].map((t) => (
              <button
                key={t.key}
                type="button"
                role="tab"
                aria-selected={mode === t.key}
                onClick={() => goTo(t.key)}
                className={`h-10 rounded-[10px] text-sm font-semibold transition-colors ${
                  mode === t.key ? "bg-panel border border-line text-bone" : "text-muted hover:text-bone"
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>
          ) : (
            <button
              type="button"
              onClick={() => goTo("login")}
              className="text-sm text-muted hover:text-bone transition-colors mb-7 inline-flex items-center gap-1.5"
            >
              ← Back to log in
            </button>
          )}

          <h1 className="font-display font-normal text-[38px] leading-none" style={{ letterSpacing: "-0.035em" }}>
            {COPY.title}
          </h1>
          <p className="text-[15px] text-muted mt-2 mb-8">{COPY.subtitle}</p>

          <form onSubmit={handleSubmit} className="space-y-5">
            {mode !== "reset" && (
              <Field
                label="Email"
                icon="mail"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
              />
            )}
            {mode !== "forgot" && (
            <Field
              label={mode === "reset" ? "New password" : "Password"}
              icon="lock"
              type={showPassword ? "text" : "password"}
              required
              autoComplete={isLogin ? "current-password" : "new-password"}
              minLength={isLogin ? undefined : 8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={isLogin ? "Your password" : "At least 8 characters"}
              trailing={
                <button
                  type="button"
                  onClick={() => setShowPassword((s) => !s)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  className="text-dim hover:text-bone transition-colors"
                >
                  <Icon name={showPassword ? "eyeOff" : "eye"} size={18} />
                </button>
              }
            />
            )}

            {mode === "reset" && (
              <Field
                label="Confirm new password"
                icon="lock"
                type={showPassword ? "text" : "password"}
                required
                autoComplete="new-password"
                minLength={8}
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                placeholder="Type it again"
              />
            )}

            {isLogin && (
              <div className="-mt-2 text-right">
                <button type="button" onClick={() => goTo("forgot")} className="text-[13px] text-accent hover:underline">
                  Forgot password?
                </button>
              </div>
            )}

            {info && (
              <div role="status" className="text-sm text-gain bg-gain-dim border border-gain/30 p-3 rounded-xl">
                {info}
              </div>
            )}

            {error && (
              <div role="alert" className="text-sm text-loss bg-loss-dim border border-loss/30 p-3 rounded-xl">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="w-full h-[52px] rounded-[14px] bg-accent text-accent-ink text-base font-semibold hover:brightness-110 active:scale-[0.99] transition-all disabled:opacity-50"
            >
              {submitting ? "Please wait…" : COPY.button}
            </button>
          </form>

          <div className="flex items-center justify-center gap-2 mt-7 text-xs text-muted">
            <Icon name="info" size={14} className="text-dim" />
            Simulated trading — no real money involved.
          </div>
        </div>
      </div>
    </div>
  );
}
