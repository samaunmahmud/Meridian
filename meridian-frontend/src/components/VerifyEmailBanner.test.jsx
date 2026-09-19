import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import VerifyEmailBanner from "./VerifyEmailBanner";
import { resendVerification } from "../lib/api";
import { showToast } from "../lib/toast";

vi.mock("../lib/api", () => ({ resendVerification: vi.fn() }));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

beforeEach(() => vi.clearAllMocks());

describe("VerifyEmailBanner", () => {
  it("names the address the link was sent to", () => {
    render(<VerifyEmailBanner email="me@example.com" />);
    expect(screen.getByText(/Please confirm your email address/)).toBeInTheDocument();
    expect(screen.getByText("me@example.com")).toBeInTheDocument();
  });

  it("resends the link and confirms with a toast", async () => {
    resendVerification.mockResolvedValue({ message: "We've sent a new confirmation link to me@example.com." });
    render(<VerifyEmailBanner email="me@example.com" />);

    await userEvent.click(screen.getByRole("button", { name: "Resend link" }));

    expect(resendVerification).toHaveBeenCalledTimes(1);
    expect(showToast).toHaveBeenCalledWith("success", "We've sent a new confirmation link to me@example.com.");
    expect(screen.getByRole("button", { name: "Resend link" })).toBeEnabled();
  });

  it("shows the error (for example a rate limit) instead of failing silently", async () => {
    resendVerification.mockRejectedValue(new Error("Too many verification emails requested."));
    render(<VerifyEmailBanner email="me@example.com" />);

    await userEvent.click(screen.getByRole("button", { name: "Resend link" }));

    expect(showToast).toHaveBeenCalledWith("error", "Too many verification emails requested.");
  });
});
