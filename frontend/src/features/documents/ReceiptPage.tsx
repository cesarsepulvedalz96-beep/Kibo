import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Printer, ArrowLeft } from 'lucide-react';
import { api } from '../../shared/api/client';
import type { Receipt } from '../../shared/types';
import { money, dateTime, shortId } from '../../shared/format';
import { Button } from '../../shared/components/Button';
import { Loading, ErrorState } from '../../shared/components/Feedback';
import { Brand } from '../../shared/components/Brand';

export default function ReceiptPage() {
  const { saleId } = useParams();
  const { t, i18n } = useTranslation();
  const receipt = useQuery({
    queryKey: ['receipts', saleId],
    queryFn: () => api.get<Receipt>(`/documents/sales/${saleId}`),
  });
  if (receipt.isPending) return <Loading />;
  if (receipt.isError) return <ErrorState error={receipt.error} />;
  const data = receipt.data;
  return (
    <>
      <div className="receipt-toolbar no-print">
        <Link className="text-link" to="/sale?tab=history">
          <ArrowLeft size={16} />
          {t('pos.history')}
        </Link>
        <Button onClick={() => window.print()}>
          <Printer size={16} />
          {t('receipt.print')}
        </Button>
      </div>
      <article className="receipt">
        <header>
          <div className="receipt-brand">
            <Brand complete />
          </div>
          <h1>{data.businessName}</h1>
          {data.taxId && <p>{data.taxId}</p>}
          <p>
            {t('receipt.title')} · #{shortId(data.sale.id)}
          </p>
          <p>{dateTime(data.sale.createdAt, i18n.language, data.timezone)}</p>
          {data.sale.status === 'CANCELLED' && <strong>{t('sales.CANCELLED')}</strong>}
        </header>
        <table>
          <thead>
            <tr>
              <th>{t('products.product')}</th>
              <th className="numeric">{t('pos.subtotal')}</th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((item) => (
              <tr key={item.id}>
                <td>
                  <strong>{item.productNameSnapshot}</strong>
                  <span className="table-subtext">
                    {item.quantity} × {money(item.unitPrice, i18n.language)}
                  </span>
                </td>
                <td className="numeric">{money(item.subtotal, i18n.language)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <dl className="totals">
          <div>
            <dt>{t('pos.subtotal')}</dt>
            <dd>{money(data.sale.subtotal, i18n.language)}</dd>
          </div>
          <div>
            <dt>{t('pos.tax')}</dt>
            <dd>{money(data.sale.taxAmount, i18n.language)}</dd>
          </div>
          <div className="total-line">
            <dt>{t('pos.total')}</dt>
            <dd>{money(data.sale.total, i18n.language)}</dd>
          </div>
          <div>
            <dt>{t('pos.payment')}</dt>
            <dd>{t(`payment.${data.sale.paymentMethod}`)}</dd>
          </div>
        </dl>
        <footer>
          <p>{t('receipt.thanks')}</p>
          <p>{t('receipt.notFiscal')}</p>
        </footer>
      </article>
    </>
  );
}
