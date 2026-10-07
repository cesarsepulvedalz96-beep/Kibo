/* global Buffer, btoa, createImageBitmap, document, process */

import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from '@playwright/test';

const sourceDirectory = resolve(process.argv[2] ?? '.');
const publicDirectory = resolve('public');
const assets = [
  ['logo_claro.png', 'logos/logo-claro.webp', 384, 0.84, true],
  ['logo_oscuro.png', 'logos/logo-oscuro.webp', 384, 0.84, true],
  ['logo_completo_claro.png', 'logos/logo-completo-claro.webp', 1000, 0.84, true],
  ['logo_completo_oscuro.png', 'logos/logo-completo-oscuro.webp', 1000, 0.84, true],
  ['fondo_claro.png', 'fondos/fondo-claro.webp', 2048, 0.72, false],
  ['fondo_oscuro.png', 'fondos/fondo-oscuro.webp', 2048, 0.72, false],
];

await Promise.all([
  mkdir(resolve(publicDirectory, 'logos'), { recursive: true }),
  mkdir(resolve(publicDirectory, 'fondos'), { recursive: true }),
]);

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();
await page.setContent('<input id="asset" type="file" accept="image/png">');

try {
  for (const [sourceName, destination, maxWidth, quality, cropTransparency] of assets) {
    const input = page.locator('#asset');
    await input.setInputFiles(resolve(sourceDirectory, sourceName));
    const result = await page.evaluate(
      async ({ maxWidth, quality, cropTransparency }) => {
        const file = document.querySelector('#asset').files[0];
        const bitmap = await createImageBitmap(file);
        const source = document.createElement('canvas');
        source.width = bitmap.width;
        source.height = bitmap.height;
        const sourceContext = source.getContext('2d', { willReadFrequently: true });
        sourceContext.drawImage(bitmap, 0, 0);

        let left = 0;
        let top = 0;
        let width = bitmap.width;
        let height = bitmap.height;
        if (cropTransparency) {
          const pixels = sourceContext.getImageData(0, 0, source.width, source.height).data;
          let right = -1;
          let bottom = -1;
          left = source.width;
          top = source.height;
          for (let y = 0; y < source.height; y += 1) {
            for (let x = 0; x < source.width; x += 1) {
              if (pixels[(y * source.width + x) * 4 + 3] <= 8) continue;
              left = Math.min(left, x);
              right = Math.max(right, x);
              top = Math.min(top, y);
              bottom = Math.max(bottom, y);
            }
          }
          if (right >= left && bottom >= top) {
            const padding = 2;
            left = Math.max(0, left - padding);
            top = Math.max(0, top - padding);
            const paddedRight = Math.min(source.width - 1, right + padding);
            const paddedBottom = Math.min(source.height - 1, bottom + padding);
            width = paddedRight - left + 1;
            height = paddedBottom - top + 1;
          }
        }

        const scale = Math.min(1, maxWidth / width);
        const output = document.createElement('canvas');
        output.width = Math.max(1, Math.round(width * scale));
        output.height = Math.max(1, Math.round(height * scale));
        output
          .getContext('2d')
          .drawImage(source, left, top, width, height, 0, 0, output.width, output.height);
        const blob = await new Promise((resolveBlob) =>
          output.toBlob(resolveBlob, 'image/webp', quality),
        );
        const bytes = new Uint8Array(await blob.arrayBuffer());
        let binary = '';
        for (const byte of bytes) binary += String.fromCharCode(byte);
        return {
          base64: btoa(binary),
          sourceWidth: bitmap.width,
          sourceHeight: bitmap.height,
          width: output.width,
          height: output.height,
        };
      },
      { maxWidth, quality, cropTransparency },
    );

    const outputPath = resolve(publicDirectory, destination);
    await writeFile(outputPath, Buffer.from(result.base64, 'base64'));
    process.stdout.write(
      `${sourceName}: ${result.sourceWidth}x${result.sourceHeight} -> ${destination}: ${result.width}x${result.height}\n`,
    );
  }
} finally {
  await browser.close();
}
