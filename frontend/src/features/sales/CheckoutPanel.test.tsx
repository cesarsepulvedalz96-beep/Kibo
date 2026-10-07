import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import '../../app/i18n';
import { CheckoutPanel, type PaymentForm } from './CheckoutPanel';

const payment: PaymentForm = {
  paymentMethod: 'CASH',
  cashReceived: '50',
  paymentReference: '',
  externallyConfirmed: false,
};
const totals = { subtotal: 100, tax: 19, total: 119 };
describe('checkout', () => {
  it('requires sufficient cash before opening confirmation', () => {
    render(
      <CheckoutPanel
        totals={totals}
        inventoryOnly={false}
        payment={payment}
        onPaymentChange={vi.fn()}
        onConfirm={vi.fn()}
        disabled={false}
      />,
    );
    expect(screen.getByRole('button', { name: 'Confirmar venta' })).toBeDisabled();
  });
  it('requires an external reference and explicit approval', () => {
    render(
      <CheckoutPanel
        totals={totals}
        inventoryOnly={false}
        payment={{ ...payment, paymentMethod: 'CARD', paymentReference: 'REF-1' }}
        onPaymentChange={vi.fn()}
        onConfirm={vi.fn()}
        disabled={false}
      />,
    );
    expect(screen.getByRole('button', { name: 'Confirmar venta' })).toBeDisabled();
  });
  it('inventory-only mode confirms an exit without payment inputs', async () => {
    const confirm = vi.fn();
    render(
      <CheckoutPanel
        totals={totals}
        inventoryOnly
        payment={payment}
        onPaymentChange={vi.fn()}
        onConfirm={confirm}
        disabled={false}
      />,
    );
    expect(screen.queryByLabelText('Medio de pago')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar salida de stock' }));
    expect(confirm).toHaveBeenCalledOnce();
  });
});
