import { Languages, Moon, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { usePreferences } from '../../app/preferences';

export function AuthPreferences() {
  const { t, i18n } = useTranslation();
  const preferences = usePreferences();
  const dark = preferences.theme === 'dark';

  return (
    <div className="login-preferences">
      <button
        type="button"
        className="login-theme-toggle"
        aria-label={t('settings.theme')}
        title={t(dark ? 'settings.dark' : 'settings.light')}
        onClick={() => preferences.setTheme(dark ? 'light' : 'dark')}
      >
        {dark ? <Moon size={17} /> : <Sun size={17} />}
      </button>
      <label className="login-language-control" htmlFor="login-language">
        <Languages size={17} aria-hidden="true" />
        <span className="sr-only">{t('settings.language')}</span>
        <select
          id="login-language"
          aria-label={t('settings.language')}
          value={i18n.language}
          onChange={(event) => void i18n.changeLanguage(event.target.value)}
        >
          <option value="es">Español</option>
          <option value="en">English</option>
          <option value="zh">中文</option>
        </select>
      </label>
    </div>
  );
}
