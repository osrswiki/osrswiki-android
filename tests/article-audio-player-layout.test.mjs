import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import test from "node:test";

const scriptPath = fileURLToPath(import.meta.url);
const repoRoot = path.resolve(path.dirname(scriptPath), "../../..");
const androidPkg = path.join(repoRoot, "platforms/android/package.json");
const androidRequire = createRequire(androidPkg);

const ANDROID_ASSETS = path.join(repoRoot, "platforms/android/app/src/main/assets");
const IOS_ASSETS = path.join(repoRoot, "platforms/ios/osrswiki/Assets");
const VIEWPORT = { width: 390, height: 844 };
const CONTENT_WIDTH_PX = 360;
const CHIP_MAX_PX = 120;
const THEMES = ["theme-osrs-light", "theme-osrs-dark"];
const PLATFORMS = {
  android: {
    assets: ANDROID_ASSETS,
    aesthetics: "styles/android-article-aesthetics.css",
    audioJs: "web/article_audio_player.js",
    extraUaCss: "",
  },
  ios: {
    assets: IOS_ASSETS,
    aesthetics: "styles/ios-article-aesthetics.css",
    audioJs: "web/article_audio_player.js",
    // Simulate ArticleWebView.swift's 44px button floor so aesthetics must beat it.
    extraUaCss: `
      a, button, .button, [role="button"] {
        min-height: 44px !important;
        min-width: 44px !important;
      }
    `,
  },
};

function evidenceDir() {
  return (
    process.env.OSRS_ARTICLE_AUDIO_LAYOUT_EVIDENCE_DIR ||
    process.env.OSRS_SESSION_ARTIFACT_DIR ||
    path.join(path.dirname(scriptPath), "output")
  );
}

async function readAsset(assetRoot, relativePath) {
  return readFile(path.join(assetRoot, relativePath), "utf8");
}

function fixtureBody() {
  return `
<div id="phoneViewport" style="width:${CONTENT_WIDTH_PX}px;max-width:${CONTENT_WIDTH_PX}px;box-sizing:border-box;">
  <div id="bodyContent" class="mw-body-content">
    <div class="mw-parser-output" id="articleColumn">
      <table class="infobox no-parenthesis-style infobox-music" id="seaShanty">
        <tbody>
          <tr><th class="infobox-header" colspan="5">Sea Shanty 2 <span style="font-size:80%">(#107)</span></th></tr>
          <tr>
            <td class="infobox-full-width-content infobox-media-player" colspan="5" id="mediaCell">
              <span class="mw-default-size" typeof="mw:File">
                <span>
                  <audio id="seaShantyAudio" class="mw-file-element" controls="" width="300" style="width:300px"
                    data-durationhint="128" preload="none">
                    <source src="https://example.test/Sea_Shanty_2.ogg" type="audio/ogg; codecs=&quot;vorbis&quot;">
                    <source src="https://example.test/Sea_Shanty_2.ogg.mp3" type="audio/mpeg">
                  </audio>
                </span>
              </span>
            </td>
          </tr>
          <tr><th colspan="2">Duration</th><td colspan="3">02:08</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</div>`;
}

async function buildDocument(platformKey, themeClass) {
  const platform = PLATFORMS[platformKey];
  const themes = await readAsset(platform.assets, "styles/themes.css");
  const components = await readAsset(platform.assets, "styles/components.css");
  const fixes = await readAsset(platform.assets, "styles/fixes.css");
  const aesthetics = await readAsset(platform.assets, platform.aesthetics);
  const audioJs = await readAsset(platform.assets, platform.audioJs);
  assert.equal(
    /(?:^|\n)\s*module\.exports/.test(audioJs),
    false,
    "shipped article_audio_player.js must not unguarded-export for Node",
  );
  const bodyBg = themeClass === "theme-osrs-dark" ? "#28221d" : "#e2dbc8";
  return `<!doctype html>
<html class="${themeClass}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Sea Shanty 2</title>
  <style>
    ${themes}
    ${components}
    ${fixes}
    ${aesthetics}
    ${platform.extraUaCss}
    body { margin: 16px; visibility: visible !important; font-size: 16px; background: ${bodyBg}; }
    table.infobox { width: fit-content; max-width: 100%; border-collapse: collapse; }
    td.infobox-media-player { padding: 8px; }
  </style>
</head>
<body class="${themeClass}">
  ${fixtureBody()}
  <script>${audioJs}</script>
</body>
</html>`;
}

function loadChromium() {
  try {
    const { chromium } = androidRequire("@playwright/test");
    return chromium;
  } catch (error) {
    error.playwrightMissing = true;
    throw error;
  }
}

function assertPillChrome(metrics, label) {
  assert.equal(metrics.hasEnhance, true, `${label}: shipped script must attach OSRSArticleAudio.enhance on window`);
  assert.ok(metrics.chrome, `${label}: shipped player chrome must exist`);
  assert.ok(metrics.playInChrome, `${label}: play/pause must live inside the player chrome`);
  assert.equal(metrics.playLabel, "Play audio", label);
  assert.equal(metrics.playHasSvg, true, `${label}: play control must be an icon, not the word Play`);
  assert.notEqual(
    metrics.playVisibleText.toLowerCase(),
    "play",
    `${label}: visible play control must not be a literal Play label`,
  );
  assert.ok(metrics.time, `${label}: runtime/time must live inside the player chrome`);
  assert.ok(metrics.duration, `${label}: duration must live inside the player chrome`);
  assert.ok(metrics.seek, `${label}: progress/seek must live inside the player chrome`);
  assert.equal(metrics.seekType, "range", `${label}: seek control must be a range input, not native audio chrome`);
  assert.equal(
    metrics.stackedVisibleCount,
    0,
    `${label}: extra stacked play above the player must not be displayed when chrome is present`,
  );
  assert.equal(
    metrics.audioControls,
    false,
    `${label}: native <audio controls> must be dropped so compact chrome is not the surface`,
  );

  const hostWidth = metrics.infobox?.w || 0;
  assert.ok(hostWidth >= 200, `${label}: infobox should be a content column, got ${hostWidth}`);
  assert.equal(metrics.cell?.display, "table-cell", `${label}: media cell must stay a table-cell so it can use infobox width`);
  assert.ok(
    metrics.chrome.w >= hostWidth * 0.85,
    `${label}: chrome width ${metrics.chrome.w} must track infobox width ${hostWidth}, not a shrink-wrapped chip remainder`,
  );
  assert.ok(
    metrics.chrome.w > CHIP_MAX_PX,
    `${label}: chrome width ${metrics.chrome.w} is still chip-sized (≤ ${CHIP_MAX_PX}px)`,
  );
  assert.ok(metrics.playInChrome.h >= 16 && metrics.playInChrome.w >= 16, `${label}: play control must be visible in chrome`);
  assert.ok(metrics.playInChrome.h < 44, `${label}: play control height ${metrics.playInChrome.h} must shrink with the pill`);
  assert.ok(metrics.seek.w > 80, `${label}: seek width ${metrics.seek.w} must be a progress bar, not a leftover thumb`);
  assert.match(metrics.timeText, /\d+:\d+/);

  assert.ok(
    metrics.chrome.h < 44,
    `${label}: chrome height ${metrics.chrome.h} must be strictly shorter than the 44px box`,
  );
  const halfHeight = metrics.chrome.h / 2;
  for (const corner of ["rtl", "rtr", "rbr", "rbl"]) {
    assert.ok(
      Math.abs(metrics.chrome[corner] - halfHeight) <= 1,
      `${label}: ${corner}=${metrics.chrome[corner]} must equal half height ${halfHeight} within 1px`,
    );
  }
  const borderW = Math.max(
    metrics.chrome.borderTopWidth,
    metrics.chrome.borderRightWidth,
    metrics.chrome.borderBottomWidth,
    metrics.chrome.borderLeftWidth,
  );
  const borderMatchesFill =
    normalizeColor(metrics.chrome.borderTopColor) === normalizeColor(metrics.chrome.backgroundColor);
  assert.ok(
    borderW === 0 || borderMatchesFill,
    `${label}: outer border must be absent (width ${borderW}) or match fill (${metrics.chrome.borderTopColor} vs ${metrics.chrome.backgroundColor})`,
  );
}

function normalizeColor(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/\s+/g, "");
}

async function measurePage(page) {
  return page.evaluate(() => {
    const px = (value) => {
      const n = parseFloat(value);
      return Number.isFinite(n) ? n : 0;
    };
    const rect = (el) => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      const cs = getComputedStyle(el);
      return {
        w: r.width,
        h: r.height,
        x: r.left,
        y: r.top,
        display: cs.display,
        visibility: cs.visibility,
        opacity: cs.opacity,
        backgroundColor: cs.backgroundColor,
        borderTopColor: cs.borderTopColor,
        borderTopWidth: px(cs.borderTopWidth),
        borderRightWidth: px(cs.borderRightWidth),
        borderBottomWidth: px(cs.borderBottomWidth),
        borderLeftWidth: px(cs.borderLeftWidth),
        rtl: px(cs.borderTopLeftRadius),
        rtr: px(cs.borderTopRightRadius),
        rbr: px(cs.borderBottomRightRadius),
        rbl: px(cs.borderBottomLeftRadius),
      };
    };
    const cell = document.getElementById("mediaCell");
    const infobox = document.getElementById("seaShanty");
    const wrap = document.querySelector(".osrs-article-audio");
    const chrome = document.querySelector(".osrs-article-audio-chrome");
    const playInChrome = chrome && chrome.querySelector(".osrs-article-audio-play");
    const time = chrome && chrome.querySelector(".osrs-article-audio-time");
    const duration = chrome && chrome.querySelector(".osrs-article-audio-duration");
    const seek = chrome && chrome.querySelector(".osrs-article-audio-seek");
    const stackedPlay = [...document.querySelectorAll(".osrs-article-audio-play")].filter(
      (btn) => !chrome || !chrome.contains(btn),
    );
    const stackedVisible = stackedPlay.filter((btn) => {
      const cs = getComputedStyle(btn);
      const r = btn.getBoundingClientRect();
      return cs.display !== "none" && cs.visibility !== "hidden" && Number(cs.opacity) > 0.05 && r.height > 1 && r.width > 1;
    });
    return {
      title: document.title,
      theme: document.documentElement.className,
      hasEnhance: typeof window.OSRSArticleAudio !== "undefined" && typeof window.OSRSArticleAudio.enhance === "function",
      moduleExports: typeof globalThis.module !== "undefined" && globalThis.module && globalThis.module.exports,
      vw: window.innerWidth,
      cell: rect(cell),
      infobox: rect(infobox),
      wrap: rect(wrap),
      chrome: rect(chrome),
      playInChrome: rect(playInChrome),
      playLabel: playInChrome ? playInChrome.getAttribute("aria-label") : "",
      playVisibleText: playInChrome ? String(playInChrome.innerText || playInChrome.textContent || "").trim() : "",
      playHasSvg: !!(playInChrome && playInChrome.querySelector("svg")),
      time: rect(time),
      timeText: time ? String(time.textContent || "").trim() : "",
      duration: rect(duration),
      durationText: duration ? String(duration.textContent || "").trim() : "",
      seek: rect(seek),
      seekType: seek ? seek.getAttribute("type") || seek.type : "",
      stackedVisibleCount: stackedVisible.length,
      audioControls: document.querySelector("audio") && document.querySelector("audio").hasAttribute("controls"),
    };
  });
}

test("Sea Shanty 2 infobox player is a short full-width pill with fill-matched outline", async (t) => {
  const dir = evidenceDir();
  await mkdir(dir, { recursive: true });
  let chromium;
  try {
    chromium = loadChromium();
  } catch (error) {
    const logPath = path.join(dir, "playwright-unavailable.txt");
    await writeFile(
      logPath,
      `${error && error.stack ? error.stack : String(error)}\nplaywrightMissing=${Boolean(error && error.playwrightMissing)}\n`,
    );
    t.diagnostic(`wrote ${logPath}`);
    throw error;
  }

  const browser = await chromium.launch({ headless: true });
  const allMetrics = [];
  try {
    // Two full passes in one test so a flaky first layout cannot hide a 44px/outline regression.
    for (let pass = 1; pass <= 2; pass += 1) {
      for (const platformKey of Object.keys(PLATFORMS)) {
        for (const themeClass of THEMES) {
          const label = `${platformKey}/${themeClass}/pass${pass}`;
          const context = await browser.newContext({
            viewport: VIEWPORT,
            deviceScaleFactor: 2,
            isMobile: true,
          });
          const page = await context.newPage();
          await page.setContent(await buildDocument(platformKey, themeClass), { waitUntil: "load" });
          const metrics = await measurePage(page);
          metrics.platform = platformKey;
          metrics.pass = pass;
          allMetrics.push(metrics);

          const stem = `${platformKey}-${themeClass}-pass${pass}`;
          await writeFile(path.join(dir, `${stem}.json`), `${JSON.stringify(metrics, null, 2)}\n`);
          await page.locator("#seaShanty").screenshot({
            path: path.join(dir, `${stem}.png`),
          });

          assertPillChrome(metrics, label);
          await context.close();
        }
      }
    }

    const logPath = path.join(dir, "article-audio-player-layout.json");
    await writeFile(logPath, `${JSON.stringify({ runs: allMetrics }, null, 2)}\n`);
    t.diagnostic(`wrote ${logPath} (${allMetrics.length} runs)`);
  } finally {
    await browser.close();
  }
});
