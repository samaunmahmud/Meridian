import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EditOrderModal from "./EditOrderModal";
import { replaceOrder } from "../lib/api";
import { showToast } from "../lib/toast";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({ replaceOrder: vi.fn() }));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

const LIMIT_BUY = { id: 4, symbol: "AAPL", type: "BUY", kind: "LIMIT", status: "PENDING", quantity: 5, limitPrice: 90 };

beforeEach(() => vi.clearAllMocks());

describe("EditOrderModal", () => {
  it("starts from the order's terms and sends the new ones", async () => {
    replaceOrder.mockResolvedValue({ ...LIMIT_BUY, id: 5, quantity: 8, limitPrice: 95 });
    const onReplaced = vi.fn();
    render(<EditOrderModal order={LIMIT_BUY} onClose={() => {}} onReplaced={onReplaced} />);

    expect(screen.getByLabelText("Quantity")).toHaveValue(5);
    expect(screen.getByLabelText("Limit price")).toHaveValue(90);
    expect(await axeViolations(screen.getByRole("dialog"))).toEqual([]);

    await userEvent.clear(screen.getByLabelText("Quantity"));
    await userEvent.type(screen.getByLabelText("Quantity"), "8");
    await userEvent.clear(screen.getByLabelText("Limit price"));
    await userEvent.type(screen.getByLabelText("Limit price"), "95");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(replaceOrder).toHaveBeenCalledWith(4, { quantity: 8, limitPrice: 95, stopPrice: null, trailPercent: null });
    expect(showToast).toHaveBeenCalledWith("success", "Order updated: buy 8 AAPL");
    expect(onReplaced).toHaveBeenCalled();
  });

  it("edits a trailing stop's trail and keeps the dialog open with the reason if it fails", async () => {
    replaceOrder.mockRejectedValue(new Error("Insufficient shares"));
    const order = { id: 7, symbol: "NVDA", type: "SELL", kind: "TRAILING_STOP", status: "PENDING", quantity: 2, stopPrice: 114, trailPercent: 5 };
    render(<EditOrderModal order={order} onClose={() => {}} onReplaced={() => {}} />);

    expect(screen.getByLabelText("Trail (%)")).toHaveValue(5);
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(replaceOrder).toHaveBeenCalledWith(7, { quantity: 2, limitPrice: null, stopPrice: null, trailPercent: 5 });
    expect(screen.getByRole("alert")).toHaveTextContent("Insufficient shares");
  });
});
