import { useState } from "react";
import { login, register, setSession } from "../lib/api";

export default function AuthPage({ onAuthenticated }) {
  const [mode, setMode] = useState("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
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

  return (
    <div className="min-h-screen grid grid-cols-1 lg:grid-cols-2 bg-ink text-bone">
      {/* Left: brand panel */}
      <div className="hidden lg:flex flex-col justify-between p-12 relative overflow-hidden border-r border-line">
        <div
          className="absolute inset-0 opacity-40"
          style={{
            background:
              "radial-gradient(circle at 20% 20%, var(--color-accent-dim), transparent 55%), radial-gradient(circle at 80% 70%, var(--color-gain-dim), transparent 45%)",
          }}
        />
        <div className="relative z-10 text-2xl font-semibold tracking-tight">meridian</div>

        <div className="relative z-10 max-w-md">
          <div className="text-4xl font-semibold leading-tight mb-4">
            Trade with clarity.<br />Track with precision.
          </div>
          <p className="text-muted text-[15px] leading-relaxed">
            Real-time market data, live portfolio tracking, and a clean paper-trading
            simulator — built to feel like the platforms it's inspired by.
          </p>
        </div>

        <div className="relative z-10 flex gap-8 text-sm text-dim font-mono">
          <div>
            <div className="text-bone text-lg">$10,000</div>
            <div>starting balance</div>
          </div>
          <div>
            <div className="text-bone text-lg">Live</div>
            <div>WebSocket pricing</div>
          </div>
        </div>
      </div>

      {/* Right: form */}
      <div className="flex items-center justify-center p-8">
        <div className="w-full max-w-sm">
          <div className="lg:hidden text-xl font-semibold mb-8">meridian</div>

          <div className="text-2xl font-semibold mb-1">
            {mode === "login" ? "Welcome back" : "Create your account"}
          </div>
          <div className="text-sm text-muted mb-8">
            {mode === "login" ? "Log in to access your portfolio" : "Start with $10,000 in virtual cash"}
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-xs text-muted block mb-1.5">Email</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full bg-panel border border-line rounded-lg px-3.5 py-2.5 text-sm outline-none focus:border-accent transition-colors"
                placeholder="you@example.com"
              />
            </div>
            <div>
              <label className="text-xs text-muted block mb-1.5">Password</label>
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full bg-panel border border-line rounded-lg px-3.5 py-2.5 text-sm outline-none focus:border-accent transition-colors"
                placeholder={mode === "register" ? "At least 8 characters" : "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"}
              />
            </div>

            {error && (
              <div className="text-sm text-loss bg-loss-dim border border-loss/30 p-3 rounded-lg">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="w-full py-2.5 rounded-lg bg-accent hover:bg-accent-2 transition-colors text-white text-sm font-medium disabled:opacity-50"
            >
              {submitting ? "Please wait..." : mode === "login" ? "Log in" : "Create account"}
            </button>
          </form>

          <div className="text-center text-sm text-muted mt-6">
            {mode === "login" ? (
              <>
                Don't have an account?{" "}
                <button
                  className="text-accent-2 font-medium"
                  onClick={() => { setMode("register"); setError(null); }}
                >
                  Sign up
                </button>
              </>
            ) : (
              <>
                Already have an account?{" "}
                <button
                  className="text-accent-2 font-medium"
                  onClick={() => { setMode("login"); setError(null); }}
                >
                  Log in
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
