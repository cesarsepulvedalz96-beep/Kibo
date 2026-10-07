export function money(value: number, language = 'es') {
  return new Intl.NumberFormat(
    language === 'es' ? 'es-CL' : language === 'zh' ? 'zh-CN' : 'en-US',
    {
      style: 'currency',
      currency: 'CLP',
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    },
  ).format(value);
}

export function dateTime(value: string, language: string, timezone = 'America/Santiago') {
  return new Intl.DateTimeFormat(language, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: timezone,
  }).format(new Date(value));
}

export function shortId(id: string) {
  return id.slice(0, 8).toUpperCase();
}
