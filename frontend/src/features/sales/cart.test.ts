import { describe, expect, it } from 'vitest';
import { cartReducer, estimateCart } from './cart';
import type { Product } from '../../shared/types';

const product: Product = {
  id: '1',
  businessId: 'b1',
  categoryId: 'c1',
  name: 'Coffee',
  description: '',
  barcode: 'DEMO1',
  salePrice: 10.5,
  costPrice: null,
  stock: 2,
  minimumStock: 1,
  imageKey: null,
  active: true,
};
describe('cart', () => {
  it('combines repeated scans without changing the product stock', () => {
    const once = cartReducer([], { type: 'add', product });
    const twice = cartReducer(once, { type: 'add', product });
    expect(twice).toHaveLength(1);
    expect(twice[0].quantity).toBe(2);
    expect(product.stock).toBe(2);
  });
  it('does not exceed available stock or accept invalid quantities', () => {
    const full = [{ product, quantity: 2 }];
    expect(cartReducer(full, { type: 'add', product })).toBe(full);
    expect(cartReducer(full, { type: 'quantity', id: '1', quantity: 0 })).toBe(full);
    expect(cartReducer(full, { type: 'quantity', id: '1', quantity: 1.5 })).toBe(full);
  });
  it('rounds estimated tax per line and supports disabled tax', () => {
    const lines = [{ product, quantity: 3 }];
    expect(estimateCart(lines, { taxEnabled: true, taxRate: 0.19 })).toEqual({
      subtotal: 31.5,
      tax: 5.99,
      total: 37.49,
    });
    expect(estimateCart(lines, { taxEnabled: false, taxRate: 0.19 }).total).toBe(31.5);
  });
});
