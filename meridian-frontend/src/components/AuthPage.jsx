import { useState } from "react";
import { login, register, setSession } from "../lib/api";
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

export default function AuthPage({ onAuthenticated }) {
  const [mode, setMode] = useState("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const response = mode === "login" ? await login(email, password) : await register(email, password);
      setSession(response.token, response.email);
      onAuthenticated(response.email);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  const isLogin = mode === "login";

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
                onClick={() => {
                  setMode(t.key);
                  setError(null);
                }}
                className={`h-10 rounded-[10px] text-sm font-semibold transition-colors ${
                  mode === t.key ? "bg-panel border border-line text-bone" : "text-muted hover:text-bone"
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          <h1 className="font-display font-normal text-[38px] leading-none" style={{ letterSpacing: "-0.035em" }}>
            {isLogin ? "Welcome back" : "Create your account"}
          </h1>
          <p className="text-[15px] text-muted mt-2 mb-8">
            {isLogin ? "Log in to your Meridian account." : "Start with $10,000 in virtual cash."}
          </p>

          <form onSubmit={handleSubmit} className="space-y-5">
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
            <Field
              label="Password"
              icon="lock"
              type={showPassword ? "text" : "password"}
              required
              autoComplete={isLogin ? "current-password" : "new-password"}
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
              {submitting ? "Please wait…" : isLogin ? "Log in" : "Create account"}
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
