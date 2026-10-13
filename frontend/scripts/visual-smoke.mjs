import { chromium } from "playwright";
import fs from "node:fs/promises";

const routes = ["/", "/products", "/order", "/contact", "/news", "/login", "/register"];
const baseUrl = process.env.SITE_URL ?? "http://localhost:3000";
const viewports = [
  { name: "desktop", width: 1440, height: 1000 },
  { name: "mobile", width: 390, height: 844 },
];

await fs.mkdir("qa-artifacts", { recursive: true });
const browser = await chromium.launch({ headless: true });
let failures = [];
try {
  for (const viewport of viewports) {
    const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height } });
    const page = await context.newPage();
    for (const route of routes) {
      const response = await page.goto(`${baseUrl}${route}`, { waitUntil: "networkidle", timeout: 30000 });
      if (!response || response.status() >= 400) failures.push(`${viewport.name} ${route}: HTTP ${response?.status()}`);
      const result = await page.evaluate(() => ({
        dir: document.documentElement.dir,
        lang: document.documentElement.lang,
        overflow: document.documentElement.scrollWidth - window.innerWidth,
        text: document.body.innerText.trim().length,
      }));
      if (result.dir !== "rtl") failures.push(`${viewport.name} ${route}: html dir=${result.dir}`);
      if (result.lang !== "fa") failures.push(`${viewport.name} ${route}: html lang=${result.lang}`);
      if (result.overflow > 4) failures.push(`${viewport.name} ${route}: horizontal overflow ${result.overflow}px`);
      if (result.text < 20) failures.push(`${viewport.name} ${route}: page content is unexpectedly empty`);
      const safe = route === "/" ? "home" : route.slice(1).replaceAll("/", "-");
      await page.screenshot({ path: `qa-artifacts/${viewport.name}-${safe}.png`, fullPage: true });
    }
    await context.close();
  }
} finally {
  await browser.close();
}
if (failures.length) {
  console.error("Visual smoke failures:\n" + failures.join("\n"));
  process.exit(1);
}
console.log(`Visual smoke passed for ${routes.length} routes at ${viewports.length} viewport sizes.`);
