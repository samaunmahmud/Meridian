import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ToastContainer from "./ToastContainer";
import { showToast } from "../lib/toast";

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

const toast = (kind, message) => act(() => showToast(kind, message));

describe("ToastContainer: announcements", () => {
  it("has both live regions in the page before any toast, so the first one is announced", () => {
    render(<ToastContainer />);
    expect(screen.getByRole("status")).toHaveAttribute("aria-live", "polite");
    expect(screen.getByRole("alert")).toHaveAttribute("aria-live", "assertive");
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
    expect(screen.getByRole("alert")).toBeEmptyDOMElement();
  });

  it("puts errors in the assertive region and everything else in the polite one", () => {
    render(<ToastContainer />);
    toast("success", "Order filled: bought 1 AAPL");
    toast("error", "Order rejected: not enough funds");

    expect(screen.getByRole("status")).toHaveTextContent("Order filled: bought 1 AAPL");
    expect(screen.getByRole("status")).not.toHaveTextContent("rejected");
    expect(screen.getByRole("alert")).toHaveTextContent("Order rejected: not enough funds");
  });
});

describe("ToastContainer: time to read", () => {
  it("can be dismissed with a button", () => {
    render(<ToastContainer />);
    toast("success", "Saved");
    fireEvent.click(screen.getByRole("button", { name: "Dismiss notification" }));
    expect(screen.queryByText("Saved")).not.toBeInTheDocument();
  });

  it("keeps a success for 6 s and an error for 10 s", () => {
    render(<ToastContainer />);
    toast("success", "Saved");
    toast("error", "Failed");

    act(() => vi.advanceTimersByTime(5900));
    expect(screen.getByText("Saved")).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(200));
    expect(screen.queryByText("Saved")).not.toBeInTheDocument();
    expect(screen.getByText("Failed")).toBeInTheDocument(); // errors stay longer
    act(() => vi.advanceTimersByTime(4000));
    expect(screen.queryByText("Failed")).not.toBeInTheDocument();
  });

  it("does not dismiss a toast while the pointer or keyboard focus is on it", () => {
    render(<ToastContainer />);
    toast("error", "Order rejected: reason");
    const message = screen.getByText("Order rejected: reason");

    fireEvent.mouseEnter(message.parentElement);
    act(() => vi.advanceTimersByTime(60000));
    expect(screen.getByText("Order rejected: reason")).toBeInTheDocument();

    fireEvent.mouseLeave(message.parentElement);
    act(() => vi.advanceTimersByTime(10100));
    expect(screen.queryByText("Order rejected: reason")).not.toBeInTheDocument();

    toast("success", "Saved");
    fireEvent.focus(screen.getByRole("button", { name: "Dismiss notification" }));
    act(() => vi.advanceTimersByTime(60000));
    expect(screen.getByText("Saved")).toBeInTheDocument();
  });
});
