import { expect, it } from 'vitest';
import es from '../locales/es/translation.json';
import en from '../locales/en/translation.json';
import zh from '../locales/zh/translation.json';

it('all supported languages have the same translated keys', () => {
  expect(Object.keys(en).sort()).toEqual(Object.keys(es).sort());
  expect(Object.keys(zh).sort()).toEqual(Object.keys(es).sort());
  expect(Object.values(zh).every((value) => value.trim().length > 0)).toBe(true);
});
