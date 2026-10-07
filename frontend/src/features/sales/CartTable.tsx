import type { Dispatch } from 'react';
import { Minus, Plus, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { money } from '../../shared/format';
import type { CartAction, CartItem } from './cart';

export function CartTable({
  items,
  dispatch,
  disabled,
}: {
  items: CartItem[];
  dispatch: Dispatch<CartAction>;
  disabled: boolean;
}) {
  const { t, i18n } = useTranslation();
  return (
    <div className="table-scroll">
      <table className="cart-table">
        <thead>
          <tr>
            <th>{t('products.product')}</th>
            <th className="numeric">{t('products.price')}</th>
            <th>{t('inventory.quantity')}</th>
            <th className="numeric">{t('pos.subtotal')}</th>
            <th>
              <span className="sr-only">{t('common.actions')}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.product.id}>
              <td>
                <strong>{item.product.name}</strong>
                <span className="table-subtext mono">{item.product.barcode}</span>
              </td>
              <td className="numeric">{money(item.product.salePrice, i18n.language)}</td>
              <td>
                <div className="quantity-control">
                  <button
                    type="button"
                    aria-label={`− ${item.product.name}`}
                    disabled={disabled || item.quantity <= 1}
                    onClick={() =>
                      dispatch({
                        type: 'quantity',
                        id: item.product.id,
                        quantity: item.quantity - 1,
                      })
                    }
                  >
                    <Minus size={14} />
                  </button>
                  <input
                    type="number"
                    aria-label={`${t('inventory.quantity')} ${item.product.name}`}
                    min={1}
                    max={item.product.stock}
                    value={item.quantity}
                    disabled={disabled}
                    onChange={(event) =>
                      dispatch({
                        type: 'quantity',
                        id: item.product.id,
                        quantity: Number(event.target.value),
                      })
                    }
                  />
                  <button
                    type="button"
                    aria-label={`+ ${item.product.name}`}
                    disabled={disabled || item.quantity >= item.product.stock}
                    onClick={() =>
                      dispatch({
                        type: 'quantity',
                        id: item.product.id,
                        quantity: item.quantity + 1,
                      })
                    }
                  >
                    <Plus size={14} />
                  </button>
                </div>
              </td>
              <td className="numeric strong">
                {money(item.product.salePrice * item.quantity, i18n.language)}
              </td>
              <td>
                <button
                  type="button"
                  className="icon-button"
                  disabled={disabled}
                  aria-label={`${t('common.remove')} ${item.product.name}`}
                  onClick={() => dispatch({ type: 'remove', id: item.product.id })}
                >
                  <Trash2 size={16} />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
