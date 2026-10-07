import type { Product, Settings } from '../../shared/types';

export interface CartItem {
  product: Product;
  quantity: number;
}
export type CartAction =
  | { type: 'add'; product: Product }
  | { type: 'quantity'; id: string; quantity: number }
  | { type: 'remove'; id: string }
  | { type: 'restore'; items: CartItem[] }
  | { type: 'clear' };

export function cartReducer(state: CartItem[], action: CartAction): CartItem[] {
  switch (action.type) {
    case 'restore':
      return action.items;
    case 'add': {
      const existing = state.find((item) => item.product.id === action.product.id);
      if (!action.product.active || (existing?.quantity || 0) >= action.product.stock) return state;
      if (existing)
        return state.map((item) =>
          item.product.id === action.product.id
            ? { product: action.product, quantity: item.quantity + 1 }
            : item,
        );
      return [...state, { product: action.product, quantity: 1 }];
    }
    case 'quantity':
      if (!Number.isInteger(action.quantity) || action.quantity < 1) return state;
      return state.map((item) =>
        item.product.id === action.id
          ? { ...item, quantity: Math.min(action.quantity, item.product.stock) }
          : item,
      );
    case 'remove':
      return state.filter((item) => item.product.id !== action.id);
    case 'clear':
      return [];
  }
}

/** Preview only. The backend re-reads prices, validates stock and uses BigDecimal. */
export function estimateCart(
  items: CartItem[],
  settings: Pick<Settings, 'taxEnabled' | 'taxRate'>,
) {
  let subtotalCents = 0;
  let taxCents = 0;
  for (const item of items) {
    const lineCents = Math.round(item.product.salePrice * 100) * item.quantity;
    subtotalCents += lineCents;
    taxCents += settings.taxEnabled ? Math.round(lineCents * settings.taxRate) : 0;
  }
  return {
    subtotal: subtotalCents / 100,
    tax: taxCents / 100,
    total: (subtotalCents + taxCents) / 100,
  };
}
