import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TradePanel from "./TradePanel";
import { placeOrder } from "../lib/api";
import { showToast } from "../lib/toast";

vi.mock("../lib/api", () => ({
  getTickers: vi.fn(() => Promise.resolve([{ symbol: "NVDA", name: "NVIDIA Corporation" }])),
  getPrices: vi.fn(() => Promise.resolve([{ price: 120 }])),
  getWallets: vi.fn(() =>
    Promise.resolve([
      { currency: "USD", balance: 10000, reserved: 0, available: 10000 },
      { currency: "EUR", balance: 1000, reserved: 183.6456, available: 816.3544 },
      { currency: "GBP", balance: 0, reserved: 0, available: 0 },
    ])
  ),
  getFxRates: vi.fn(() => Promise.resolve([{ baseCurrency: "EUR", quoteCurrency: "USD", rate: 1.1 }])),
  placeOrder: vi.fn(),
}));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

beforeEach(() => vi.clearAllMocks());

// [symbol picker, wallet picker] and [quantity, limit/stop price] once loaded
const pickers = () => screen.getAllByRole("combobox");
const numbers = () => screen.getAllByRole("spinbutton");

async function renderPanel(props = {}) {
  const onOrderPlaced = vi.fn();
  render(<TradePanel onOrderPlaced={onOrderPlaced} refreshKey={0} {...props} />);
  await screen.findByRole("option", { name: /EUR/ }); // wallets have loaded
  return onOrderPlaced;
}

describe("TradePanel: paying from a wallet", () => {
  it("offers the wallet picker for limit orders too, listing AVAILABLE balances", async () => {
    await renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "Limit" }));

    const options = within(pickers()[1]).getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual([
      "USD — $10,000.00 available",
      "EUR — €816.35 available", // 1,000 minus 183.65 held by an open order
      "GBP — £0.00 available",
    ]);
    expect(screen.queryByText(/settle in USD/)).not.toBeInTheDocument();
  });

  it("says how much a EUR limit buy will reserve, and that the final amount follows the rate", async () => {
    await renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "Limit" }));
    await userEvent.selectOptions(pickers()[1], "EUR");
    await userEvent.clear(numbers()[0]);
    await userEvent.type(numbers()[0], "2");
    await userEvent.type(numbers()[1], "100");

    // $200 + $1.00 commission = $201; 201 / (1.10 x 0.995) = €183.65
    expect(screen.getByText(/Reserves/)).toBeInTheDocument();
    expect(screen.getByText("€183.65")).toBeInTheDocument();
    expect(screen.getByText(/Held until the order fills or you cancel it/)).toBeInTheDocument();
    expect(screen.getByText(/follows the exchange rate when it fills/)).toBeInTheDocument();
  });

  it("wording for a market order is an estimate, not a reservation", async () => {
    await renderPanel();
    await userEvent.selectOptions(pickers()[1], "EUR");

    expect(screen.getByText(/Est\. you pay/)).toBeInTheDocument();
    expect(screen.queryByText(/Reserves/)).not.toBeInTheDocument();
    expect(screen.queryByText(/follows the exchange rate when it fills/)).not.toBeInTheDocument();
  });

  it("shows no conversion estimate when paying in USD", async () => {
    await renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "Limit" }));
    await userEvent.type(numbers()[1], "100");

    expect(screen.queryByText(/Reserves/)).not.toBeInTheDocument();
    expect(screen.queryByText(/conversion spread/)).not.toBeInTheDocument();
  });

  it("places the order with the chosen wallet and confirms which wallet it is pending against", async () => {
    placeOrder.mockResolvedValue({ status: "PENDING", type: "BUY", quantity: 2, symbol: "NVDA", settlementCurrency: "EUR" });
    const onOrderPlaced = await renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "Limit" }));
    await userEvent.selectOptions(pickers()[1], "EUR");
    await userEvent.clear(numbers()[0]);
    await userEvent.type(numbers()[0], "2");
    await userEvent.type(numbers()[1], "100");

    await userEvent.click(screen.getByRole("button", { name: "Buy NVDA" }));

    expect(placeOrder).toHaveBeenCalledWith("NVDA", "BUY", 2, "LIMIT", 100, null, "EUR");
    expect(showToast).toHaveBeenCalledWith("success", "Limit order placed: buy 2 NVDA (from your EUR wallet)");
    expect(onOrderPlaced).toHaveBeenCalled();
  });

  it("sends no currency for a plain USD order", async () => {
    placeOrder.mockResolvedValue({ status: "FILLED", type: "BUY", quantity: 1, symbol: "NVDA", price: 120 });
    await renderPanel();

    await userEvent.click(screen.getByRole("button", { name: "Buy NVDA" }));

    expect(placeOrder).toHaveBeenCalledWith("NVDA", "BUY", 1, "MARKET", null, null, null);
  });

  it("shows the server's refusal, for example not enough money", async () => {
    placeOrder.mockRejectedValue(new Error("Insufficient EUR balance: need 183.6456 but only 100 available"));
    await renderPanel();
    await userEvent.selectOptions(pickers()[1], "EUR");

    await userEvent.click(screen.getByRole("button", { name: "Buy NVDA" }));

    expect(showToast).toHaveBeenCalledWith("error", "Insufficient EUR balance: need 183.6456 but only 100 available");
  });
});
