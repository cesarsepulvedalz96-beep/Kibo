import { Moon, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { usePreferences } from '../../app/preferences';
import { Field } from '../../shared/components/Field';

export function AppearancePreferences() {
  const { t, i18n } = useTranslation();
  const preferences = usePreferences();

  return (
    <>
      <section className="settings-section">
        <div>
          <h2>{t('settings.appearance')}</h2>
          <p>{t('settings.preferenceHint')}</p>
        </div>
        <div className="settings-fields">
          <label className="field-label">{t('settings.theme')}</label>
          <div className="theme-options" role="group" aria-label={t('settings.theme')}>
            {(
              [
                { key: 'light', icon: Sun },
                { key: 'dark', icon: Moon },
              ] as const
            ).map(({ key, icon: Icon }) => (
              <button
                type="button"
                key={key}
                className={`theme-option ${preferences.theme === key ? 'selected' : ''}`}
                aria-pressed={preferences.theme === key}
                onClick={() => preferences.setTheme(key)}
              >
                <Icon size={21} strokeWidth={1.4} />
                {t(`settings.${key}`)}
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="settings-section">
        <div>
          <h2>{t('settings.language')}</h2>
        </div>
        <div className="settings-fields">
          <Field id="language" label={t('settings.language')}>
            <select
              id="language"
              value={i18n.language}
              onChange={(event) => void i18n.changeLanguage(event.target.value)}
            >
              <option value="es">Español</option>
              <option value="en">English</option>
              <option value="zh">中文</option>
            </select>
          </Field>
        </div>
      </section>
    </>
  );
}
