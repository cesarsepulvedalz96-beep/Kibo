import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useTranslation } from 'react-i18next';
import { money } from '../../shared/format';
import type { ReportSummary } from '../../shared/types';

export function ReportChart({ series }: { series: ReportSummary['series'] }) {
  const { t, i18n } = useTranslation();
  const data = series.map((point) => ({
    ...point,
    label: new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'short' }).format(
      new Date(`${point.date}T12:00:00`),
    ),
  }));
  return (
    <div
      className="chart"
      role="img"
      aria-label={`${t('dashboard.chart')}: ${series.map((point) => `${point.date}: ${money(point.total, i18n.language)}`).join('; ')}`}
    >
      <ResponsiveContainer width="100%" height="100%" minWidth={0}>
        <LineChart data={data} margin={{ top: 12, right: 12, bottom: 8, left: 0 }}>
          <CartesianGrid vertical={false} stroke="var(--border)" strokeDasharray="3 5" />
          <XAxis
            dataKey="label"
            axisLine={false}
            tickLine={false}
            tick={{ fill: 'var(--muted)', fontSize: 11 }}
            dy={10}
            minTickGap={28}
          />
          <YAxis
            axisLine={false}
            tickLine={false}
            tick={{ fill: 'var(--muted)', fontSize: 11 }}
            width={58}
            tickFormatter={(value: number) =>
              new Intl.NumberFormat(i18n.language, { notation: 'compact' }).format(value)
            }
          />
          <Tooltip
            formatter={(value) => [money(Number(value), i18n.language), t('dashboard.revenue')]}
            contentStyle={{
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderRadius: 8,
              color: 'var(--text)',
              fontSize: 12,
            }}
          />
          <Line
            type="monotone"
            dataKey="total"
            stroke="var(--chart-line)"
            strokeWidth={2.5}
            dot={data.length <= 7 ? { r: 3, strokeWidth: 2, fill: 'var(--surface)' } : false}
            activeDot={{ r: 5 }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
