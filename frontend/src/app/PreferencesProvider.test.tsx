import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { PreferencesProvider } from './PreferencesProvider';
import { usePreferences } from './preferences';

function PreferencesHarness() {
  const preferences = usePreferences();
  return (
    <div>
      <output aria-label="theme">{preferences.theme}</output>
      <button type="button" onClick={() => preferences.setTheme('dark')}>
        dark
      </button>
    </div>
  );
}

beforeEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset.theme;
});

describe('appearance preferences', () => {
  it('migrates the removed system theme to light and persists the explicit value', async () => {
    localStorage.setItem('stockflow.theme', 'system');

    render(
      <PreferencesProvider>
        <PreferencesHarness />
      </PreferencesProvider>,
    );

    expect(screen.getByLabelText('theme')).toHaveTextContent('light');
    await waitFor(() => expect(localStorage.getItem('stockflow.theme')).toBe('light'));
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('applies and persists an explicit theme', async () => {
    render(
      <PreferencesProvider>
        <PreferencesHarness />
      </PreferencesProvider>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'dark' }));

    expect(screen.getByLabelText('theme')).toHaveTextContent('dark');
    await waitFor(() => {
      expect(localStorage.getItem('stockflow.theme')).toBe('dark');
    });
    expect(document.documentElement.dataset.theme).toBe('dark');
  });
});
