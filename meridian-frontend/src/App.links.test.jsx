import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { axeViolations } from "./test/axe";

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

describe("opening the site while the server is down", () => {
  function unavailable(status) {
    const err = new Error("down");
    err.status = status;
    return err;
  }

  it("says Meridian is unavailable instead of showing the login page", async () => {
    await openSiteAt("/", (a) => a.getMe.mockRejectedValue(unavailable(503)));

    expect(await screen.findByRole("heading", { name: "Meridian is unavailable" })).toBeInTheDocument();
    expect(screen.queryByText("Welcome back")).not.toBeInTheDocument();
    expect(await axeViolations(document.body, { page: true })).toEqual([]);
  });

  it("says the same when the server cannot be reached at all", async () => {
    await openSiteAt("/", (a) => a.getMe.mockRejectedValue(unavailable(0)));

    expect(await screen.findByRole("heading", { name: "Meridian is unavailable" })).toBeInTheDocument();
  });

  it("Try again goes straight to the login page once the server answers that nobody is signed in", async () => {
    const api = await openSiteAt("/", (a) => a.getMe.mockRejectedValueOnce(unavailable(503)));
    await screen.findByRole("heading", { name: "Meridian is unavailable" });

    api.getMe.mockRejectedValueOnce(unavailable(401));
    screen.getByRole("button", { name: "Try again" }).click();

    expect(await screen.findByText("Welcome back")).toBeInTheDocument();
    expect(api.getMe).toHaveBeenCalledTimes(2);
  });
});
