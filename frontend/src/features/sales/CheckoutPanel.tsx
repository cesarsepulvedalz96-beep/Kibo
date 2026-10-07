import { useTranslation } from 'react-i18next';
import { LockKeyhole } from 'lucide-react';
import { Field } from '../../shared/components/Field';
import { Button } from '../../shared/components/Button';
import { money } from '../../shared/format';

export interface PaymentForm {
  paymentMethod: string;
  cashReceived: string;
  paymentReference: string;
  externallyConfirmed: boolean;
}
interface Props {
  totals: { subtotal: number; tax: number; total: number };
  inventoryOnly: boolean;
  payment: PaymentForm;
  onPaymentChange: (payment: PaymentForm) => void;
  onConfirm: () => void;
  disabled: boolean;
}

export function CheckoutPanel({
  totals,
  inventoryOnly,
  payment,
  onPaymentChange,
  onConfirm,
  disabled,
}: Props) {
  const { t, i18n } = useTranslation();
  const update = (changes: Partial<PaymentForm>) => onPaymentChange({ ...payment, ...changes });
  const cashValid = payment.cashReceived !== '' && Number(payment.cashReceived) >= totals.total;
  const externalValid = payment.externallyConfirmed && payment.paymentReference.trim().length > 0;
  return (
    <aside className="panel checkout-panel">
      <h2>{t(inventoryOnly ? 'pos.inventoryTitle' : 'pos.summary')}</h2>
      {inventoryOnly ? (
        <p className="inline-note">{t('pos.inventoryHint')}</p>
      ) : (
        <>
          <dl className="totals">
            <div>
              <dt>{t('pos.subtotal')}</dt>
              <dd>{money(totals.subtotal, i18n.language)}</dd>
            </div>
            <div>
              <dt>{t('pos.tax')}</dt>
              <dd>{money(totals.tax, i18n.language)}</dd>
            </div>
            <div className="total-line">
              <dt>{t('pos.total')}</dt>
              <dd>{money(totals.total, i18n.language)}</dd>
            </div>
          </dl>
          <Field id="payment-method" label={t('pos.payment')}>
            <select
              id="payment-method"
              value={payment.paymentMethod}
              disabled={disabled}
              onChange={(event) =>
                update({ paymentMethod: event.target.value, externallyConfirmed: false })
              }
            >
              {['CASH', 'CARD', 'TRANSFER', 'EXTERNAL'].map((method) => (
                <option key={method} value={method}>
                  {t(`payment.${method}`)}
                </option>
              ))}
            </select>
          </Field>
          {payment.paymentMethod === 'CASH' ? (
            <>
              <Field id="cash-received" label={t('pos.cashReceived')}>
                <input
                  id="cash-received"
                  type="number"
                  min="0"
                  step="0.01"
                  value={payment.cashReceived}
                  disabled={disabled}
                  onChange={(event) => update({ cashReceived: event.target.value })}
                />
              </Field>
              <div className="change-line">
                <span>{t('pos.change')}</span>
                <strong>
                  {money(Math.max(0, Number(payment.cashReceived) - totals.total), i18n.language)}
                </strong>
              </div>
            </>
          ) : (
            <>
              <p className="field-hint">{t('pos.externalHint')}</p>
              <Field id="payment-reference" label={t('pos.reference')}>
                <input
                  id="payment-reference"
                  maxLength={120}
                  value={payment.paymentReference}
                  disabled={disabled}
                  onChange={(event) => update({ paymentReference: event.target.value })}
                />
              </Field>
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={payment.externallyConfirmed}
                  disabled={disabled}
                  onChange={(event) => update({ externallyConfirmed: event.target.checked })}
                />
                {t('pos.externalConfirmed')}
              </label>
            </>
          )}
        </>
      )}
      <Button
        className="button--full confirm-sale-button"
        onClick={onConfirm}
        disabled={
          disabled ||
          (!inventoryOnly && !(payment.paymentMethod === 'CASH' ? cashValid : externalValid))
        }
      >
        <LockKeyhole size={16} />
        {t(inventoryOnly ? 'pos.confirmExit' : 'pos.confirm')}
      </Button>
      {!inventoryOnly && <p className="checkout-footnote">{t('pos.estimated')}</p>}
    </aside>
  );
}
