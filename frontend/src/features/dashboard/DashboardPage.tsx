import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  ArrowRight,
  Plus,
  TrendingUp,
  Package,
  CircleAlert,
  Trophy,
  CalendarDays,
} from 'lucide-react';
import { api } from '../../shared/api/client';
import type { Page, Product, Sale, ReportSummary } from '../../shared/types';
import { money, dateTime, shortId } from '../../shared/format';
import { PageHeader } from '../../shared/components/PageHeader';
import { ErrorState, Loading, EmptyState } from '../../shared/components/Feedback';
import { RangeSelector, type ReportRange } from '../reports/RangeSelector';
import { ReportChart } from '../reports/ReportChart';
import { useSettings } from '../settings/queries';

export default function DashboardPage() {
  const { t, i18n } = useTranslation();
  const [range, setRange] = useState<ReportRange>('TODAY');
  const settings = useSettings();
  const summary = useQuery({
    queryKey: ['reports', range],
    queryFn: () => api.get<ReportSummary>(`/reports/summary?range=${range}`),
  });
  const week = useQuery({
    queryKey: ['reports', 'WEEK'],
    queryFn: () => api.get<ReportSummary>('/reports/summary?range=WEEK'),
  });
  const low = useQuery({
    queryKey: ['products', 'low', 0],
    queryFn: () => api.get<Page<Product>>('/products?status=low&size=5'),
  });
  const sales = useQuery({
    queryKey: ['sales', 'recent'],
    queryFn: () => api.get<Page<Sale>>('/sales?size=5'),
  });
  if (summary.isPending) return <Loading />;
  if (summary.isError)
    return <ErrorState error={summary.error} retry={() => void summary.refetch()} />;
  const data = summary.data;
  const chart = range === 'TODAY' ? week : summary;
  const stats = [
    {
      label: 'dashboard.revenue',
      value: money(data.revenue, i18n.language),
      helper: t('dashboard.salesCount', { count: data.saleCount }),
      icon: TrendingUp,
    },
    {
      label: 'dashboard.sold',
      value: data.unitsSold.toLocaleString(i18n.language),
      helper: t(`range.${range}`),
      icon: Package,
    },
    {
      label: 'dashboard.low',
      value: data.lowStockCount,
      helper: t('dashboard.attention'),
      icon: CircleAlert,
    },
    {
      label: 'dashboard.top',
      value: data.topProduct || '—',
      helper: t(`range.${range}`),
      icon: Trophy,
    },
  ];
  return (
    <>
      <PageHeader
        eyebrow={t('login.eyebrow')}
        title={t('dashboard.title')}
        description={t('dashboard.description')}
        action={
          <Link className="button button--primary" to="/sale">
            <Plus size={18} />
            {t(
              settings.data?.operatingMode === 'INVENTORY_ONLY'
                ? 'pos.inventoryTitle'
                : 'dashboard.newSale',
            )}
          </Link>
        }
      />
      <div className="section-toolbar">
        <span className="date-label">
          <CalendarDays size={16} />
          {new Intl.DateTimeFormat(i18n.language, {
            dateStyle: 'long',
            timeZone: settings.data?.timezone || 'America/Santiago',
          }).format(new Date())}
        </span>
        <RangeSelector value={range} onChange={setRange} />
      </div>
      <section className="stats-row" aria-label={t('dashboard.title')}>
        {stats.map(({ label, value, helper, icon: Icon }) => (
          <div className="stat" key={label}>
            <div className="stat-label">
              {t(label)}
              <Icon size={17} strokeWidth={1.5} />
            </div>
            <div className={`stat-value ${label === 'dashboard.top' ? 'stat-value--name' : ''}`}>
              {value}
            </div>
            <p>{helper}</p>
          </div>
        ))}
      </section>
      <div className="dashboard-grid">
        <section className="panel chart-panel">
          <div className="panel-header">
            <div>
              <h2>{t('dashboard.chart')}</h2>
              <p>{range === 'TODAY' ? t('dashboard.week') : t(`range.${range}`)}</p>
            </div>
            <span className="chart-legend">
              <i />
              {t('dashboard.revenue')}
            </span>
          </div>
          {chart.isPending ? (
            <Loading />
          ) : chart.isError ? (
            <ErrorState error={chart.error} />
          ) : (
            <ReportChart series={chart.data.series} />
          )}
          <div className="chart-footer">
            <div>
              <span>{t('dashboard.topCategory')}</span>
              <strong>{data.topCategory || '—'}</strong>
            </div>
            <div>
              <span>{t('dashboard.peakHour')}</span>
              <strong>
                {data.peakHour === null ? '—' : `${String(data.peakHour).padStart(2, '0')}:00`}
              </strong>
            </div>
          </div>
        </section>
        <section className="panel low-stock-panel">
          <div className="panel-header">
            <div>
              <h2>
                {t('dashboard.lowTitle')}
                <span className="count-badge">{data.lowStockCount}</span>
              </h2>
              <p>{t('dashboard.lowDescription')}</p>
            </div>
          </div>
          {low.isPending ? (
            <Loading />
          ) : low.isError ? (
            <ErrorState error={low.error} />
          ) : low.data.items.length ? (
            <ul className="low-stock-list">
              {low.data.items.map((product) => (
                <li key={product.id}>
                  <span className="product-symbol">
                    <Package size={18} strokeWidth={1.4} />
                  </span>
                  <div>
                    <strong>{product.name}</strong>
                    <span>{t('dashboard.minimum', { count: product.minimumStock })}</span>
                  </div>
                  <b>
                    {product.stock}
                    <small>{t('common.units')}</small>
                  </b>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState
              title={t('dashboard.healthy')}
              description={t('dashboard.healthyDescription')}
            />
          )}
          <Link to="/products?status=low" className="panel-bottom-link">
            {t('nav.inventory')}
            <ArrowRight size={16} />
          </Link>
        </section>
      </div>
      <section className="panel">
        <div className="panel-header">
          <h2>{t('dashboard.recent')}</h2>
          <Link className="text-link" to="/sale?tab=history">
            {t('common.viewAll')}
            <ArrowRight size={15} />
          </Link>
        </div>
        {sales.isPending ? (
          <Loading />
        ) : sales.isError ? (
          <ErrorState error={sales.error} />
        ) : sales.data.items.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>{t('sales.id')}</th>
                  <th>{t('common.date')}</th>
                  <th>{t('pos.payment')}</th>
                  <th>{t('common.status')}</th>
                  <th className="numeric">{t('pos.total')}</th>
                </tr>
              </thead>
              <tbody>
                {sales.data.items.map((sale) => (
                  <tr key={sale.id}>
                    <td className="mono">#{shortId(sale.id)}</td>
                    <td>{dateTime(sale.createdAt, i18n.language, settings.data?.timezone)}</td>
                    <td>{t(`payment.${sale.paymentMethod}`)}</td>
                    <td>
                      <span className="badge">{t(`sales.${sale.status}`)}</span>
                    </td>
                    <td className="numeric strong">{money(sale.total, i18n.language)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState
            title={t('dashboard.emptySales')}
            description={t('dashboard.emptyDescription')}
          />
        )}
      </section>
    </>
  );
}
