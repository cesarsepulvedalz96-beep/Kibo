import { useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import '../../app/i18n';
import { api } from '../../shared/api/client';
import type { Product } from '../../shared/types';
import { MovementProductSelector } from './MovementProductSelector';

vi.mock('../categories/queries', () => ({
  useCategories: () => ({
    data: [
      { id: 'food', name: 'Alimentos' },
      { id: 'tools', name: 'Herramientas' },
    ],
  }),
}));
vi.mock('../../shared/api/client', async (original) => ({
  ...(await original<typeof import('../../shared/api/client')>()),
  api: { get: vi.fn() },
}));
const get = vi.mocked(api.get);
const product: Product = {
  id: 'rice',
  businessId: 'business',
  categoryId: 'food',
  name: 'Arroz',
  barcode: '12345',
  description: null,
  salePrice: 1000,
  costPrice: null,
  stock: 10,
  minimumStock: 2,
  active: true,
  imageKey: null,
};
function Harness({ disabled = false, initial }: { disabled?: boolean; initial?: Product }) {
  const [selected, setSelected] = useState(initial);
  return <MovementProductSelector product={selected} onChange={setSelected} disabled={disabled} />;
}
function mount(props: { disabled?: boolean; initial?: Product } = {}) {
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <Harness {...props} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  get.mockReset();
  get.mockResolvedValue({ items: [product], total: 45, page: 0, size: 20 });
});

describe('inventory product selection', () => {
  it('does not submit the movement when Enter is pressed while searching a selected product', async () => {
    const submit = vi.fn();
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <form
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          <Harness />
          <button type="submit">Confirmar movimiento</button>
        </form>
      </QueryClientProvider>,
    );
    const selector = screen.getByLabelText('Producto');
    await waitFor(() => expect(selector).toBeEnabled());
    await userEvent.selectOptions(selector, 'rice');
    await userEvent.type(screen.getByLabelText('Buscar producto por nombre'), 'Arroz{Enter}');
    expect(selector).toHaveValue('rice');
    expect(submit).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar movimiento' }));
    expect(submit).toHaveBeenCalledOnce();
  });

  it('combines name and category filters and clears an unrelated selection', async () => {
    mount();
    const selector = screen.getByLabelText('Producto');
    await waitFor(() => expect(selector).toBeEnabled());
    await userEvent.selectOptions(selector, 'rice');
    expect(selector).toHaveValue('rice');
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), 'tools');
    expect(selector).toHaveValue('');
    await userEvent.type(screen.getByLabelText('Buscar producto por nombre'), 'Martillo');
    await waitFor(() =>
      expect(get).toHaveBeenLastCalledWith(
        expect.stringContaining('categoryId=tools&search=Martillo&status=active&page=0&size=20'),
      ),
    );
  });

  it('requests only one bounded page at a time and pagination does not submit the movement', async () => {
    mount();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled());
    expect(get).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Siguiente' })).toHaveAttribute('type', 'button');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await waitFor(() =>
      expect(get).toHaveBeenLastCalledWith(expect.stringContaining('page=1&size=20')),
    );
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('preserves an unresolved operation and locks all selection controls without another product request', () => {
    mount({ disabled: true, initial: product });
    expect(screen.getByLabelText('Producto')).toHaveValue('rice');
    expect(screen.getByLabelText('Producto')).toBeDisabled();
    expect(screen.getByLabelText('Categoría')).toBeDisabled();
    expect(screen.getByLabelText('Buscar producto por nombre')).toBeDisabled();
    expect(get).not.toHaveBeenCalled();
  });
});
