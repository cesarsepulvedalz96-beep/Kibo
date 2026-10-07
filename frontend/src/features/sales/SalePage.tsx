import { useEffect, useReducer, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, ReceiptText } from 'lucide-react';
import { api, ApiError } from '../../shared/api/client';
import type { Product, SaleDetails } from '../../shared/types';
import { money } from '../../shared/format';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { EmptyState, ErrorState, Loading } from '../../shared/components/Feedback';
import { useSettings } from '../settings/queries';
import { ProductSearch } from '../products/ProductSearch';
import { useToast } from '../../app/toast';
import { useSession } from '../auth/session';
import { operationKey, usePendingOperation } from '../../shared/hooks/usePendingOperation';
import { cartReducer, estimateCart, type CartItem } from './cart';
import { CartTable } from './CartTable';
import { CheckoutPanel, type PaymentForm } from './CheckoutPanel';
import { SaleHistory } from './SaleHistory';

const emptyPayment: PaymentForm = {
  paymentMethod: 'CASH',
  cashReceived: '',
  paymentReference: '',
  externallyConfirmed: false,
};

interface CheckoutContext {
  items: CartItem[];
  payment: PaymentForm;
  totals: ReturnType<typeof estimateCart>;
}

type CheckoutDraft = Pick<CheckoutContext, 'items' | 'payment'>;

function readCheckoutDraft(key: string): CheckoutDraft | undefined {
  try {
    const draft = JSON.parse(sessionStorage.getItem(key) || 'null') as CheckoutDraft | null;
    if (!draft || !Array.isArray(draft.items) || !draft.payment) return undefined;
    return draft;
  } catch {
    return undefined;
  }
}

export default function SalePage() {
  const { t, i18n } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const settings = useSettings();
  const session = useSession();
  const operation = usePendingOperation<CheckoutContext>(operationKey(session.data!, 'checkout'));
  const draftKey = operationKey(session.data!, 'checkout-draft');
  const [draft] = useState(() => readCheckoutDraft(draftKey));
  const [params, setParams] = useSearchParams();
  const [items, dispatch] = useReducer(
    cartReducer,
    operation.pending?.context.items || draft?.items || [],
  );
  const [payment, setPayment] = useState<PaymentForm>(
    operation.pending?.context.payment || draft?.payment || emptyPayment,
  );
  const [confirming, setConfirming] = useState(Boolean(operation.pending));
  const [scanning, setScanning] = useState(false);
  const [quoteRequired, setQuoteRequired] = useState(false);
  const [completed, setCompleted] = useState<SaleDetails>();
  const [cartError, setCartError] = useState<unknown>();
  const inventoryOnly = operation.pending
    ? operation.pending.path === '/inventory/exits'
    : settings.data?.operatingMode === 'INVENTORY_ONLY';
  const totals =
    operation.pending?.context.totals ||
    estimateCart(items, settings.data || { taxEnabled: false, taxRate: 0 });
  const units = items.reduce((total, item) => total + item.quantity, 0);
  useEffect(() => {
    if (operation.pending) return;
    if (!items.length) {
      sessionStorage.removeItem(draftKey);
      return;
    }
    try {
      sessionStorage.setItem(draftKey, JSON.stringify({ items, payment } satisfies CheckoutDraft));
    } catch {
      // A draft improves navigation but must never block a sale.
    }
  }, [draftKey, items, operation.pending, payment]);
  const quote = useMutation({
    mutationFn: async () => {
      const [products, refreshed] = await Promise.all([
        Promise.all(items.map((item) => api.get<Product>(`/products/${item.product.id}`))),
        settings.refetch(),
      ]);
      if (refreshed.isError) throw refreshed.error;
      dispatch({
        type: 'restore',
        items: items.map((item, index) => ({ ...item, product: products[index] })),
      });
      await client.invalidateQueries({ queryKey: ['products'] });
    },
    onSuccess: () => setQuoteRequired(false),
  });
  const confirm = useMutation({
    mutationFn: async () => {
      const payload = {
        items: items.map((item) => ({ productId: item.product.id, quantity: item.quantity })),
        ...(!inventoryOnly
          ? {
              paymentMethod: payment.paymentMethod,
              cashReceived: payment.paymentMethod === 'CASH' ? Number(payment.cashReceived) : null,
              paymentReference: payment.paymentReference || null,
              externallyConfirmed: payment.externallyConfirmed,
              expectedTotal: totals.total,
            }
          : {}),
      };
      return operation.execute<SaleDetails | { id: string }>(
        inventoryOnly ? '/inventory/exits' : '/sales',
        payload,
        { items, payment, totals },
      );
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'PRICE_CHANGED') {
        setConfirming(false);
        setQuoteRequired(true);
        setPayment({ ...payment, cashReceived: '', externallyConfirmed: false });
        setCartError(error);
        quote.mutate();
      }
    },
    onSuccess: async (result) => {
      await Promise.all(
        ['products', 'sales', 'inventory', 'reports', 'notifications'].map((key) =>
          client.invalidateQueries({ queryKey: [key] }),
        ),
      );
      setConfirming(false);
      dispatch({ type: 'clear' });
      setPayment(emptyPayment);
      if ('sale' in result) setCompleted(result);
      else toast(t('pos.exitSuccess'));
    },
  });
  const locked = confirm.isPending || Boolean(operation.pending);

  function add(product: Product) {
    if (locked || confirming) return;
    const quantity = items.find((item) => item.product.id === product.id)?.quantity || 0;
    if (quantity >= product.stock) {
      setCartError(new ApiError('INSUFFICIENT_STOCK', 409));
      return;
    }
    setCartError(undefined);
    setCompleted(undefined);
    dispatch({ type: 'add', product });
  }

  if (operation.recoveryError)
    return <ErrorState error={operation.recoveryError} retry={() => window.location.reload()} />;
  if (settings.isPending) return <Loading />;
  if (settings.isError)
    return <ErrorState error={settings.error} retry={() => void settings.refetch()} />;

  return (
    <>
      <PageHeader
        title={t(inventoryOnly ? 'pos.inventoryTitle' : 'pos.title')}
        description={t('pos.description')}
      />
      <div className="tabs" role="group" aria-label={t('nav.sale')}>
        <button aria-pressed={params.get('tab') !== 'history'} onClick={() => setParams({})}>
          {t('pos.current')}
        </button>
        <button
          aria-pressed={params.get('tab') === 'history'}
          onClick={() => setParams({ tab: 'history' })}
        >
          {t('pos.history')}
        </button>
      </div>
      {params.get('tab') === 'history' ? (
        <SaleHistory />
      ) : (
        <>
          {completed && (
            <div className="sale-success" role="status">
              <CheckCircle2 size={24} />
              <div>
                <strong>{t('pos.success')}</strong>
                <span>{money(completed.sale.total, i18n.language)}</span>
              </div>
              {settings.data.generateReceipt && (
                <Link className="button button--secondary" to={`/receipts/${completed.sale.id}`}>
                  <ReceiptText size={16} />
                  {t('pos.receipt')}
                </Link>
              )}
              <Button variant="ghost" onClick={() => setCompleted(undefined)}>
                {t('pos.newSale')}
              </Button>
            </div>
          )}
          <div className="pos-grid">
            <div className="pos-main">
              <ProductSearch
                onSelect={add}
                scanner={settings.data.scannerEnabled}
                disabled={locked || confirming || quoteRequired}
                onScanningChange={setScanning}
              />
              {cartError !== undefined && <ErrorState error={cartError} />}
              {quote.isError && <ErrorState error={quote.error} retry={() => quote.mutate()} />}
              <section className="panel cart-panel">
                <div className="panel-header">
                  <h2>{t('pos.cart')}</h2>
                  <span className="muted small">{t('pos.cartItems', { count: units })}</span>
                </div>
                {items.length ? (
                  <CartTable items={items} dispatch={dispatch} disabled={locked || quoteRequired} />
                ) : (
                  <EmptyState title={t('pos.empty')} description={t('pos.emptyDescription')} />
                )}
              </section>
            </div>
            <CheckoutPanel
              totals={totals}
              inventoryOnly={inventoryOnly}
              payment={payment}
              onPaymentChange={setPayment}
              onConfirm={() => {
                confirm.reset();
                setConfirming(true);
              }}
              disabled={!items.length || locked || scanning || quoteRequired}
            />
          </div>
        </>
      )}
      {confirming && (
        <Dialog
          title={t('pos.confirmTitle')}
          onClose={() => {
            if (!locked) setConfirming(false);
          }}
        >
          <p>
            {t(inventoryOnly ? 'pos.exitDescription' : 'pos.confirmDescription', { count: units })}
          </p>
          {!inventoryOnly && (
            <div className="confirmation-amount">{money(totals.total, i18n.language)}</div>
          )}
          {confirm.isError && <ErrorState error={confirm.error} />}
          {operation.pending && !confirm.isPending && (
            <p role="status">{t('operation.uncertain')}</p>
          )}
          <div className="dialog-actions">
            <Button variant="secondary" disabled={locked} onClick={() => setConfirming(false)}>
              {t('common.back')}
            </Button>
            <Button busy={confirm.isPending} onClick={() => confirm.mutate()}>
              {t(
                operation.pending
                  ? 'operation.retry'
                  : inventoryOnly
                    ? 'pos.confirmExit'
                    : 'pos.confirm',
              )}
            </Button>
          </div>
        </Dialog>
      )}
    </>
  );
}
