import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

// Links in emails open the site as /?verify=<token> or /?reset=<token>.
vi.mock("./lib/api", async (importOriginal) => ({
  ...(await importOriginal()),
  getMe: vi.fn(() => Promise.reject(new Error("not signed in"))),
  verifyEmail: vi.fn(),
  logout: vi.fn(),
}));
vi.mock("./lib/usePriceSocket", () => ({ usePriceSocket: () => null }));

// App reads the address once, when it is first imported, so each test sets the
// URL first and then imports a fresh copy. `setup` configures the API mocks
// before the page renders.
async function openSiteAt(url, setup = () => {}) {
  vi.resetModules();
  window.history.pushState({}, "", url);
  const api = await import("./lib/api");
  setup(api);
  const { default: App } = await import("./App");
  render(<App />);
  return api;
}

beforeEach(() => window.history.pushState({}, "", "/"));

describe("opening the site from an email link", () => {
  it("a reset link shows the new-password form and removes the token from the address bar", async () => {
    await openSiteAt("/?reset=secret-reset-token");

    expect(await screen.findByText("Choose a new password")).toBeInTheDocument();
    expect(window.location.search).toBe("");
    expect(window.location.href).not.toContain("secret-reset-token");
  });

  it("a confirmation link verifies the address, tells a signed-out visitor, and hides the token", async () => {
    const api = await openSiteAt("/?verify=secret-verify-token", (a) =>
      a.verifyEmail.mockResolvedValue({ message: "Your email address is confirmed." })
    );

    expect(await screen.findByRole("status")).toHaveTextContent("Your email address is confirmed.");
    expect(api.verifyEmail).toHaveBeenCalledWith("secret-verify-token");
    expect(window.location.search).toBe("");
  });

  it("an expired confirmation link says so instead of failing silently", async () => {
    await openSiteAt("/?verify=old-token", (a) =>
      a.verifyEmail.mockRejectedValue(new Error("This link is invalid or has expired. Please request a new one."))
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("invalid or has expired");
    expect(screen.getByText("Welcome back")).toBeInTheDocument(); // the login page still works
  });

  it("a plain visit just shows the login page and asks nothing of the server", async () => {
    const api = await openSiteAt("/");

    expect(await screen.findByText("Welcome back")).toBeInTheDocument();
    expect(screen.queryByText("Choose a new password")).not.toBeInTheDocument();
    expect(api.verifyEmail).not.toHaveBeenCalled();
  });
});
