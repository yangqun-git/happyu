// Optional browser regression suite: make Playwright available via NODE_PATH and run
// node --test src/test/js/content-mode.test.cjs. CHROMIUM_EXECUTABLE can select a local browser.
const {test, before, after} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {join} = require('node:path');
const {chromium} = require('playwright');

const script = readFileSync(join(__dirname, '../../main/resources/scripts/content-mode.js'), 'utf8');
const css = `* { background-color: #202124 !important; color: #cccccc !important; }
  img, video, canvas, iframe[src*='video'] { display: none !important; visibility: hidden !important; }`;
let browser;
before(async () => {
  browser = await chromium.launch({headless: true, executablePath: process.env.CHROMIUM_EXECUTABLE});
});
after(async () => { await browser?.close(); });

async function fixture(t, html = '<div id="root"></div>') {
  const page = await browser.newPage();
  t.after(() => page.close());
  await page.setContent(html);
  await page.evaluate(() => {
    // Use real DOM/CSS/MutationObserver, with deterministic visibility and scheduling.
    window.testHidden = false;
    Object.defineProperty(document, 'hidden', {get: () => testHidden});
    Object.defineProperty(performance, 'now', {configurable: true, value: () => 0});
    window.framesPending = new Map();
    window.timersPending = new Map();
    let id = 0;
    window.requestAnimationFrame = fn => { framesPending.set(++id, fn); return id; };
    window.cancelAnimationFrame = key => framesPending.delete(key);
    window.setTimeout = fn => { timersPending.set(++id, fn); return id; };
    window.clearTimeout = key => timersPending.delete(key);
    window.tick = (timer = false) => {
      const entry = (timer ? timersPending : framesPending).entries().next().value;
      if (entry) entry[1]();
    };
    window.hide = value => {
      window.testHidden = value;
      document.dispatchEvent(new Event('visibilitychange'));
    };
  });
  await install(page, css, true);
  return page;
}

async function install(page, styles, blocked) {
  await page.evaluate(({source, styles, blocked}) => {
    (0, eval)(source)(styles, blocked);
  }, {source: script, styles, blocked});
}

async function drain(page) {
  for (let i = 0; i < 200; i++) {
    const pending = await page.evaluate(() => {
      tick();
      return Boolean(__happyuContentMode.hasWork());
    });
    if (!pending && !await page.evaluate(() => Boolean(__happyuContentMode.hasWork()))) return;
  }
  assert.fail('DOM work did not finish');
}

test('large initial DOM yields between bounded batches and preserves themed presentation', async t => {
  const html = '<div style="background-color: white !important; color: black !important">Text</div>'.repeat(5000);
  const page = await fixture(t, html);
  await page.evaluate(() => {
    const state = __happyuContentMode;
    const normalize = state.normalize;
    window.visited = 0;
    state.normalize = element => { visited++; normalize(element); };
    tick();
  });
  const first = await page.evaluate(() => ({visited, pending: Boolean(__happyuContentMode.hasWork())}));
  assert.ok(first.visited > 0 && first.visited <= 256);
  assert.ok(first.pending);
  await drain(page);
  const failures = await page.evaluate(() => [...document.querySelectorAll('div')].filter(element => {
    const style = getComputedStyle(element);
    return style.backgroundColor !== 'rgb(32, 33, 36)' || style.color !== 'rgb(204, 204, 204)';
  }).slice(0, 3).map(element => ({html: element.outerHTML,
    color: getComputedStyle(element).color, background: getComputedStyle(element).backgroundColor,
    rules: [...document.getElementById('happyu-style').sheet.cssRules].map(rule => rule.cssText)})));
  assert.deepEqual(failures, []);
});

test('time budget yields even before reaching the node limit', async t => {
  const page = await fixture(t, '<div style="color:black !important"></div>'.repeat(1000));
  const first = await page.evaluate(() => {
    let time = 0;
    Object.defineProperty(performance, 'now', {value: () => time++});
    const state = __happyuContentMode;
    const normalize = state.normalize;
    let count = 0;
    state.normalize = element => { count++; normalize(element); };
    tick();
    return {count, pending: Boolean(state.hasWork())};
  });
  assert.ok(first.count > 0 && first.count <= 4);
  assert.ok(first.pending);
});

test('overlapping subtree additions and attribute changes normalize a target once', async t => {
  const page = await fixture(t);
  await drain(page);
  await page.evaluate(() => {
    const normalize = __happyuContentMode.normalize;
    window.targetVisits = 0;
    __happyuContentMode.normalize = element => {
      if (element.id === 'target') targetVisits++;
      normalize(element);
    };
    const parent = document.createElement('section');
    document.body.append(parent);
    const target = document.createElement('div');
    target.id = 'target';
    parent.append(target);
    target.style.setProperty('color', 'black', 'important');
  });
  await drain(page);
  assert.equal(await page.evaluate(() => targetVisits), 1);
  assert.equal(await page.$eval('#target', el => getComputedStyle(el).color), 'rgb(204, 204, 204)');
});

test('queue overflow recovers all live nodes without retaining an unbounded queue', async t => {
  const page = await fixture(t);
  await drain(page);
  await page.evaluate(() => {
    for (let i = 0; i < 2000; i++) {
      const element = document.createElement('div');
      element.style.setProperty('color', 'black', 'important');
      document.body.append(element);
    }
  });
  assert.ok(await page.evaluate(() => __happyuContentMode.pending.size <= 512));
  await drain(page);
  assert.equal(await page.evaluate(() => [...document.querySelectorAll('div')]
    .every(el => el.style.getPropertyPriority('color') === '')), true);
});

test('hidden pages release pending nodes and normalize new content on return', async t => {
  const page = await fixture(t);
  await drain(page);
  await page.evaluate(() => {
    hide(true);
    for (let i = 0; i < 2000; i++) {
      const node = document.createElement('div');
      node.style.setProperty('background-color', 'white', 'important');
      document.body.append(node);
      node.remove();
    }
    document.body.insertAdjacentHTML('beforeend', '<canvas id="media" style="display:block !important"></canvas>');
  });
  assert.deepEqual(await page.evaluate(() => ({pending: __happyuContentMode.pending.size,
    walker: __happyuContentMode.walker, frames: framesPending.size, timers: timersPending.size})),
  {pending: 0, walker: null, frames: 0, timers: 0});
  await page.evaluate(() => hide(false));
  await drain(page);
  assert.equal(await page.$eval('#media', el => getComputedStyle(el).display), 'none');
});

test('timer fallback finishes work if painting stops without a visibility event', async t => {
  const page = await fixture(t, '<div id="target" style="color:black !important"></div>');
  await page.evaluate(() => tick(true));
  assert.equal(await page.$eval('#target', el => getComputedStyle(el).color), 'rgb(204, 204, 204)',
    JSON.stringify(await page.evaluate(() => ({html: document.body.innerHTML,
      pending: [...__happyuContentMode.pending].map(([node, subtree]) => [node.tagName, subtree]),
      current: __happyuContentMode.current?.tagName, rescan: __happyuContentMode.rescan}))));
  await drain(page);
  assert.equal(await page.evaluate(() => framesPending.size + timersPending.size), 0);
});

test('removed traversal cursors recover remaining content', async t => {
  const page = await fixture(t, '<div style="color:black !important"></div>'.repeat(1000));
  await page.evaluate(() => {
    tick();
    __happyuContentMode.current.remove();
    document.body.firstElementChild.style.setProperty('color', 'red', 'important');
  });
  await drain(page);
  assert.equal(await page.evaluate(() => [...document.querySelectorAll('div')]
    .every(el => getComputedStyle(el).color === 'rgb(204, 204, 204)')), true);
});

test('media toggles, src changes, theme changes and stylesheet restoration still work', async t => {
  const page = await fixture(t, '<img id="image" style="display:block !important;visibility:visible !important">' +
    '<video style="display:block !important"></video><canvas style="display:block !important"></canvas>' +
    '<iframe id="frame" src="about:blank" style="display:block !important"></iframe>');
  await drain(page);
  assert.equal(await page.evaluate(() => [...document.querySelectorAll('img,video,canvas')]
    .every(el => getComputedStyle(el).display === 'none')), true);
  await install(page, '* { color: #00ff00 !important; }', false);
  await drain(page);
  assert.equal(await page.$eval('#image', el => getComputedStyle(el).display), 'block');
  await page.$eval('#image', el => el.style.setProperty('display', 'block', 'important'));
  await install(page, css, true);
  await drain(page);
  assert.equal(await page.$eval('#image', el => getComputedStyle(el).display), 'none');
  await page.$eval('#frame', el => el.src = 'about:blank#video');
  await drain(page);
  assert.equal(await page.$eval('#frame', el => getComputedStyle(el).display), 'none');
  await page.evaluate(() => {
    document.getElementById('happyu-style').remove();
    document.body.style.setProperty('color', 'black', 'important');
  });
  await drain(page);
  assert.equal(await page.evaluate(() => getComputedStyle(document.body).color), 'rgb(204, 204, 204)');
  assert.equal(await page.evaluate(() => document.querySelectorAll('#happyu-style').length), 1);
});
