import { act, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, expect, it, vi } from 'vitest';
import '../../app/i18n';
import { API_ACTIVITY_EVENT } from '../api/apiActivity';
import { GlobalActivityOverlay } from './ActivityOverlay';

afterEach(() => {
  vi.useRealTimers();
});

it('keeps a completed transfer visible for the minimum operation time', () => {
  vi.useFakeTimers();
  const client = new QueryClient();
  render(
    <QueryClientProvider client={client}>
      <GlobalActivityOverlay />
    </QueryClientProvider>,
  );

  act(() => {
    window.dispatchEvent(
      new CustomEvent(API_ACTIVITY_EVENT, { detail: { pending: 1, writes: 1 } }),
    );
  });
  expect(screen.getByRole('status')).toBeVisible();

  act(() => {
    window.dispatchEvent(
      new CustomEvent(API_ACTIVITY_EVENT, { detail: { pending: 0, writes: 0 } }),
    );
    vi.advanceTimersByTime(1_799);
  });
  expect(screen.getByRole('status')).toBeVisible();

  act(() => vi.advanceTimersByTime(1));
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
});
