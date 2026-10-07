import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import es from '../locales/es/translation.json';
import en from '../locales/en/translation.json';
import zh from '../locales/zh/translation.json';

const saved = localStorage.getItem('stockflow.language');
void i18n.use(initReactI18next).init({
  resources: { es: { translation: es }, en: { translation: en }, zh: { translation: zh } },
  lng: saved && ['es', 'en', 'zh'].includes(saved) ? saved : 'es',
  fallbackLng: 'es',
  keySeparator: false,
  interpolation: { escapeValue: false },
});
i18n.on('languageChanged', (language) => {
  localStorage.setItem('stockflow.language', language);
  document.documentElement.lang = language;
});
document.documentElement.lang = i18n.language;
export default i18n;
