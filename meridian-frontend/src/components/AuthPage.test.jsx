import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AuthPage from "./AuthPage";
import { forgotPassword, login, register, resetPassword } from "../lib/api";

vi.mock("../lib/api", () => ({
  login: vi.fn(),
  register: vi.fn(),
  forgotPassword: vi.fn(),
  resetPassword: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());

const email = () => screen.getByPlaceholderText("you@example.com");

describe("AuthPage: logging in and signing up", () => {
  it("logs in and passes the response (email + verification state) up", async () => {
    login.mockResolvedValue({ email: "me@example.com", emailVerified: false });
    const onAuthenticated = vi.fn();
    render(<AuthPage onAuthenticated={onAuthenticated} />);

    await userEvent.type(email(), "me@example.com");
    await userEvent.type(screen.getByPlaceholderText("Your password"), "secret-password");
    await userEvent.click(screen.getByRole("button", { name: "Log in" }));

    expect(login).toHaveBeenCalledWith("me@example.com", "secret-password");
    expect(onAuthenticated).toHaveBeenCalledWith({ email: "me@example.com", emailVerified: false });
  });

  it("shows the server's message when the login is refused", async () => {
    login.mockRejectedValue(new Error("Invalid email or password"));
    render(<AuthPage onAuthenticated={vi.fn()} />);

    await userEvent.type(email(), "me@example.com");
    await userEvent.type(screen.getByPlaceholderText("Your password"), "wrong");
    await userEvent.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
  });

  it("switches to sign-up and registers", async () => {
    register.mockResolvedValue({ email: "new@example.com", emailVerified: false });
    const onAuthenticated = vi.fn();
    render(<AuthPage onAuthenticated={onAuthenticated} />);

    await userEvent.click(screen.getByRole("tab", { name: "Sign up" }));
    expect(screen.getByText("Create your account")).toBeInTheDocument();
    await userEvent.type(email(), "new@example.com");
    await userEvent.type(screen.getByPlaceholderText("At least 8 characters"), "long-enough-pw");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));

    expect(register).toHaveBeenCalledWith("new@example.com", "long-enough-pw");
    expect(onAuthenticated).toHaveBeenCalled();
  });
});

describe("AuthPage: forgot password", () => {
  it("asks for just an email and shows the generic confirmation", async () => {
    forgotPassword.mockResolvedValue({ message: "If an account exists for that email, we've sent a link to reset the password." });
    render(<AuthPage onAuthenticated={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: "Forgot password?" }));
    expect(screen.getByText("Reset your password")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Your password")).not.toBeInTheDocument();
    await userEvent.type(email(), "me@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Send reset link" }));

    expect(forgotPassword).toHaveBeenCalledWith("me@example.com");
    expect(await screen.findByRole("status")).toHaveTextContent("If an account exists for that email");
  });

  it("can go back to the login form", async () => {
    render(<AuthPage onAuthenticated={vi.fn()} />);
    await userEvent.click(screen.getByRole("button", { name: "Forgot password?" }));

    await userEvent.click(screen.getByRole("button", { name: /Back to log in/ }));

    expect(screen.getByText("Welcome back")).toBeInTheDocument();
  });
});

describe("AuthPage: choosing a new password from an emailed link", () => {
  it("opens straight on the new-password form, with no email field", () => {
    render(<AuthPage onAuthenticated={vi.fn()} resetToken="abc123" />);

    expect(screen.getByText("Choose a new password")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Type it again")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("you@example.com")).not.toBeInTheDocument();
  });

  it("does not call the server when the two passwords differ", async () => {
    render(<AuthPage onAuthenticated={vi.fn()} resetToken="abc123" />);

    await userEvent.type(screen.getByPlaceholderText("At least 8 characters"), "brand-new-password");
    await userEvent.type(screen.getByPlaceholderText("Type it again"), "something-different");
    await userEvent.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("don't match");
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it("sends the token and password, then returns to login with a confirmation", async () => {
    resetPassword.mockResolvedValue({ message: "ok" });
    const onResetDone = vi.fn();
    render(<AuthPage onAuthenticated={vi.fn()} resetToken="abc123" onResetDone={onResetDone} />);

    await userEvent.type(screen.getByPlaceholderText("At least 8 characters"), "brand-new-password");
    await userEvent.type(screen.getByPlaceholderText("Type it again"), "brand-new-password");
    await userEvent.click(screen.getByRole("button", { name: "Change password" }));

    expect(resetPassword).toHaveBeenCalledWith("abc123", "brand-new-password");
    expect(await screen.findByText("Welcome back")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Your password has been changed");
    expect(onResetDone).toHaveBeenCalled();
  });

  it("shows why an expired or used link failed", async () => {
    resetPassword.mockRejectedValue(new Error("This link is invalid or has expired. Please request a new one."));
    render(<AuthPage onAuthenticated={vi.fn()} resetToken="old" />);

    await userEvent.type(screen.getByPlaceholderText("At least 8 characters"), "brand-new-password");
    await userEvent.type(screen.getByPlaceholderText("Type it again"), "brand-new-password");
    await userEvent.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("invalid or has expired");
  });
});

describe("AuthPage: result of an emailed confirmation link", () => {
  it("shows a notice that arrives after the page has appeared", async () => {
    const { rerender } = render(<AuthPage onAuthenticated={vi.fn()} notice={null} />);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    rerender(<AuthPage onAuthenticated={vi.fn()} notice={{ kind: "success", message: "Your email address is confirmed." }} />);

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Your email address is confirmed."));
  });
});
