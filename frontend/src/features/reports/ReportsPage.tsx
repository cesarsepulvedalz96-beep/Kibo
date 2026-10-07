import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api } from '../../shared/api/client';
import type { ReportSummary } from '../../shared/types';
import { money } from '../../shared/format';
import { PageHeader } from '../../shared/components/PageHeader';
import { ErrorState, Loading, EmptyState } from '../../shared/components/Feedback';
import { RangeSelector, type ReportRange } from './RangeSelector';
import { ReportChart } from './ReportChart';

export default function ReportsPage() {
  const { t, i18n } = useTranslation();
  const [range, setRange] = useState<ReportRange>('WEEK');
  const report = useQuery({
    queryKey: ['reports', range],
    queryFn: () => api.get<ReportSummary>(`/reports/summary?range=${range}`),
  });
  return (
    <>
      <PageHeader
        title={t('reports.title')}
        description={t('reports.description')}
        action={<RangeSelector value={range} onChange={setRange} />}
      />
      {report.isPending ? (
        <Loading />
      ) : report.isError ? (
        <ErrorState error={report.error} retry={() => void report.refetch()} />
      ) : (
        <>
          <div className="stats-row">
            <div className="stat">
              <span className="stat-label">{t('dashboard.revenue')}</span>
              <strong className="stat-value">{money(report.data.revenue, i18n.language)}</strong>
              <p>{t('dashboard.salesCount', { count: report.data.saleCount })}</p>
            </div>
            <div className="stat">
              <span className="stat-label">{t('dashboard.sold')}</span>
              <strong className="stat-value">{report.data.unitsSold}</strong>
              <p>{t('common.units')}</p>
            </div>
            <div className="stat">
              <span className="stat-label">{t('reports.inventoryExits')}</span>
              <strong className="stat-value">{report.data.inventoryExits}</strong>
              <p>{t('common.units')}</p>
            </div>
            <div className="stat">
              <span className="stat-label">{t('dashboard.low')}</span>
              <strong className="stat-value">{report.data.lowStockCount}</strong>
              <p>{t('dashboard.attention')}</p>
            </div>
          </div>
          <section className="panel">
            <div className="panel-header">
              <h2>{t('dashboard.chart')}</h2>
              <span className="small muted">
                {report.data.from} — {report.data.to}
              </span>
            </div>
            <ReportChart series={report.data.series} />
            <div className="chart-footer">
              <div>
                <span>{t('dashboard.top')}</span>
                <strong>{report.data.topProduct || '—'}</strong>
              </div>
              <div>
                <span>{t('dashboard.topCategory')}</span>
                <strong>{report.data.topCategory || '—'}</strong>
              </div>
            </div>
          </section>
          <div className="two-column">
            <section className="panel">
              <div className="panel-header">
                <h2>{t('reports.byDay')}</h2>
              </div>
              <div className="table-scroll report-table">
                <table>
                  <thead>
                    <tr>
                      <th>{t('reports.period')}</th>
                      <th className="numeric">{t('dashboard.sold')}</th>
                      <th className="numeric">{t('dashboard.revenue')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.data.series.map((point) => (
                      <tr key={point.date}>
                        <td>{point.date}</td>
                        <td className="numeric">{point.units}</td>
                        <td className="numeric strong">{money(point.total, i18n.language)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
            <section className="panel">
              <div className="panel-header">
                <h2>{t('reports.hourly')}</h2>
              </div>
              {report.data.hours.length ? (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>{t('reports.hour')}</th>
                        <th className="numeric">{t('dashboard.revenue')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.data.hours.map((hour) => (
                        <tr key={hour.hour}>
                          <td>{String(hour.hour).padStart(2, '0')}:00</td>
                          <td className="numeric strong">{money(hour.total, i18n.language)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <EmptyState title={t('common.noData')} />
              )}
            </section>
          </div>
        </>
      )}
    </>
  );
}
