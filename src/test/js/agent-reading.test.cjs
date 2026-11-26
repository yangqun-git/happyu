// NODE_PATH and CHROMIUM_EXECUTABLE are configured like content-mode.test.cjs.
const {test, before, after} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync, mkdirSync} = require('node:fs');
const {join} = require('node:path');
const {chromium} = require('playwright');
const source = readFileSync(join(__dirname, '../../main/resources/scripts/agent-reading.js'), 'utf8');
const contentMode = readFileSync(join(__dirname, '../../main/resources/scripts/content-mode.js'), 'utf8');
const dark = {bg:'#202124', fg:'#d4d4d4', muted:'#92979e', link:'#6ca8ff', border:'#414347', card:'#292b30'};
const light = {bg:'#f4f5f7', fg:'#24272b', muted:'#737981', link:'#1750a6', border:'#c8cdd2', card:'#ffffff'};
let browser;
before(async () => { browser = await chromium.launch({headless:true, executablePath:process.env.CHROMIUM_EXECUTABLE}); });
after(async () => { await browser?.close(); });
async function fixture(t, html, url='https://example.test/docs/article') {
  const page = await browser.newPage({viewport:{width:538, height:760}});
  t.after(() => page.close());
  await page.route(url, route => route.fulfill({contentType:'text/html; charset=utf-8',
    body:'<base href="https://example.test/docs/">' + html}));
  await page.goto(url);
  return page;
}
async function enable(page, theme=dark) {
  await page.evaluate(({source,theme}) => (0,eval)(source)(theme), {source,theme});
}
async function ready(page) {
  await page.waitForFunction(() => window.__happyuAgentReading && !__happyuAgentReading.cursor
    && !__happyuAgentReading.needsRefresh);
}
const text = page => page.evaluate(() => __happyuAgentReading.content.textContent);
const tiebaThread = readFileSync(join(__dirname,'fixtures/tieba-virtual-thread.html'),'utf8');
const classicThread = readFileSync(join(__dirname,'fixtures/tieba-classic-thread.html'),'utf8');

test('Tieba reply titles distinguish total from loaded and retain known totals across incremental loads', async t => {
  const page=await fixture(t,tiebaThread,'https://tieba.baidu.com/p/counts');
  await page.evaluate(()=>{
    document.querySelector('.show-more-lzl').onclick=()=>{
      const group=document.querySelector('.lzl-wrapper');
      group.insertAdjacentHTML('beforeend','<div class="pb-lzl-item"><div class="pb-rich-text">新增回复</div></div>');
      group.querySelector('.show-more-lzl').textContent='展开更多回复';
    };
  });
  await enable(page);await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  const heading=reader.locator('.replies-card>summary');
  assert.equal(await heading.textContent(),'65 条回复 · 已加载 2 条');
  await heading.click();
  await reader.getByRole('button',{name:'展开 65 条回复',exact:true}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.querySelectorAll('.reply-bubble').length===3);
  assert.equal(await heading.textContent(),'65 条回复 · 已加载 3 条');
  assert.equal(await reader.locator('.replies-card').evaluate(n=>n.open),true);
  await page.evaluate(()=>document.querySelector('.lzl-wrapper').setAttribute('data-total-count','54'));
  await page.waitForFunction(()=>__happyuAgentReading.content.querySelector('.replies-card>summary').textContent.includes('54 条回复'));
  assert.equal(await heading.textContent(),'54 条回复 · 已加载 3 条');
});

test('classic metadata provides the complete reply total and refreshes independently of loaded reply count', async t => {
  const page=await fixture(t,classicThread,'https://tieba.baidu.com/p/classic-counts');
  await enable(page);await ready(page);
  const heading=page.locator('#happyu-agent-reading .replies-card>summary');
  assert.equal(await heading.textContent(),'3 条回复 · 已加载 2 条');
  await page.evaluate(()=>document.querySelector('[data-pid="classic-3"] .j_lzl_container').setAttribute('data-field','{"total_num":53}'));
  await page.waitForFunction(()=>__happyuAgentReading.content.querySelector('.replies-card>summary').textContent.includes('53 条回复'));
  assert.equal(await heading.textContent(),'53 条回复 · 已加载 2 条');
});

test('remaining counts are added to loaded replies and unrelated nested/neighbor counts never become totals', async t => {
  const page=await fixture(t,'<main><div class="l_post" data-floor="1"><div class="reply-list">'
    +'<div class="reply-item">第一条</div><div class="reply-item">第二条</div>'
    +'<button id="more">还有51条回复，点击查看</button></div></div>'
    +'<div class="l_post" data-floor="2"><div class="reply-list"><div class="reply-item">正文提到999条回复'
    +'<button>查看 99 条回复</button></div></div></div></main>');
  await page.evaluate(()=>document.querySelector('#more').onclick=()=>{
    document.querySelector('.reply-list').insertAdjacentHTML('afterbegin','<div class="reply-item">新加载一条</div>');
    document.querySelector('#more').textContent='还有50条回复，点击查看';
  });
  await enable(page);await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  assert.deepEqual(await reader.locator('.replies-card>summary').allTextContents(),[
    '53 条回复 · 已加载 2 条','楼中楼回复 · 已加载 1 条']);
  await reader.locator('.replies-card').first().evaluate(n=>n.open=true);
  await reader.getByRole('button',{name:'还有51条回复，点击查看'}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.querySelectorAll('.reply-bubble').length===4);
  assert.deepEqual(await reader.locator('.replies-card>summary').allTextContents(),[
    '53 条回复 · 已加载 3 条','楼中楼回复 · 已加载 1 条']);
});

test('same-document links jump to copied IDs/named anchors, respect the toolbar and never scroll the original', async t => {
  const paragraphs=Array.from({length:50},(_,i)=>'<p>正文段落 '+i+'。阅读目录定位测试。</p>').join('');
  const page=await fixture(t,'<main id="article"><a href="#chapter">普通目录</a> <a href="#%E7%AB%A0%E8%8A%82%20%5B1%5D">命名目录</a> '
    +'<a href="#missing">缺失目录</a> <a href="#">返回顶部</a>'+paragraphs+'<h2 id="chapter">章节标题</h2><p>章节正文</p>'
    +paragraphs+'<a name="章节 [1]"></a><h2>命名章节</h2>'+paragraphs+'</main><nav id="missing">未提取的导航</nav>');
  await page.evaluate(()=>document.querySelector('base').href=location.href);
  await enable(page);await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  await reader.getByRole('link',{name:'普通目录',exact:true}).click();
  await page.waitForTimeout(100);
  assert.ok(await page.evaluate(()=>__happyuAgentReading.viewport.scrollTop>500));
  assert.equal(await page.evaluate(()=>window.scrollY),0);
  assert.equal(new URL(page.url()).hash,'');
  const offset=await page.evaluate(()=>__happyuAgentReading.anchorTargets.get('chapter').getBoundingClientRect().top
    -__happyuAgentReading.host.shadowRoot.querySelector('.toolbar').getBoundingClientRect().bottom);
  assert.ok(Math.abs(offset-12)<2, 'The heading should remain below the sticky toolbar');
  await reader.getByRole('link',{name:'命名目录',exact:true}).click();
  assert.equal(await page.evaluate(()=>__happyuAgentReading.host.shadowRoot.activeElement===__happyuAgentReading.anchorTargets.get('章节 [1]')),true);
  await reader.getByRole('link',{name:'缺失目录',exact:true}).click();
  assert.ok((await reader.locator('.status').textContent()).includes('未包含在阅读内容中'));
  assert.equal(await page.evaluate(()=>window.scrollY),0);
  await reader.getByRole('link',{name:'返回顶部',exact:true}).click();
  assert.equal(await page.evaluate(()=>__happyuAgentReading.viewport.scrollTop),0);
  await page.evaluate(()=>document.querySelector('h2').id='renamed');
  await page.waitForFunction(()=>__happyuAgentReading.anchorTargets.has('renamed'));
  assert.equal(await page.evaluate(()=>__happyuAgentReading.anchorTargets.has('chapter')),false);
});

test('anchor links expand closed cached floor cards after their source nodes are virtualized away', async t => {
  const page=await fixture(t,tiebaThread.replace('楼主的正文。','楼主的正文。<a href="#reply-target">查看目标楼中楼</a>')
    .replace('第一条楼中楼。','第一条楼中楼。<strong id="reply-target">目标内容</strong>'),'https://tieba.baidu.com/p/anchor');
  await page.evaluate(()=>document.querySelector('base').href=location.href);
  await enable(page);await ready(page);
  await page.evaluate(()=>{
    __happyuAgentReading.cards.get('post:post-3').open=false;
    document.querySelector('[data-key="post-3"]').replaceChildren();
  });
  await page.waitForTimeout(900);await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  await reader.getByRole('link',{name:'查看目标楼中楼'}).click();
  assert.equal(await page.evaluate(()=>{
    const target=__happyuAgentReading.anchorTargets.get('reply-target');
    return !!target&&[target.closest('.post-card'),target.closest('.replies-card')].every(n=>n.open);
  }),true);
  assert.equal(await reader.getByText('目标内容',{exact:true}).isVisible(),true);
});

test('reading isolates source inputs, Tab cannot enter hidden forms and disposal restores focus without submitting', async t => {
  const page=await fixture(t,'<main><p>正文</p><a href="next">正文链接</a><form><input id="draft" value="draft"><button>提交</button></form></main>');
  await page.evaluate(()=>{
    window.submits=0;
    document.querySelector('form').onsubmit=e=>{e.preventDefault();submits++};
    document.querySelector('input').focus();
  });
  await enable(page);await ready(page);
  assert.equal(await page.evaluate(()=>document.body.inert),true);
  assert.equal(await page.evaluate(()=>document.activeElement.id),'happyu-agent-reading');
  await page.keyboard.type('hidden-edit');
  // Move past the initial refresh button before Enter, avoiding an intentional reader refresh.
  await page.keyboard.press('Tab');
  await page.keyboard.press('Shift+Tab');
  const visited=[];
  for(let i=0;i<12;i++){
    await page.keyboard.press('Tab');
    visited.push(await page.evaluate(()=>document.activeElement.tagName));
  }
  assert.ok(!visited.includes('INPUT')&&!visited.includes('BUTTON'));
  await page.evaluate(()=>document.querySelector('input').focus());
  assert.notEqual(await page.evaluate(()=>document.activeElement.id),'draft');
  await page.keyboard.press('Enter');
  assert.equal(await page.evaluate(()=>document.querySelector('input').value),'draft');
  assert.equal(await page.evaluate(()=>submits),0);
  await page.evaluate(()=>{window.old=__happyuAgentReading;old.dispose()});
  assert.equal(await page.evaluate(()=>document.body.hasAttribute('inert')),false);
  assert.equal(await page.evaluate(()=>document.activeElement.id),'draft');
  assert.equal(await page.evaluate(()=>old.originalFocus===null&&old.inertRoots.size===0&&old.anchorTargets.size===0),true);
  await page.keyboard.type('-after');
  assert.ok((await page.locator('input').inputValue()).includes('-after'));
});

test('focus isolation covers replacement bodies and outside portals, preserving pre-existing inert values on exit', async t => {
  const page=await fixture(t,'<main><p>原正文</p></main>');
  await page.evaluate(()=>document.body.setAttribute('inert','original-value'));
  await enable(page);await ready(page);
  await enable(page,light);
  await page.evaluate(()=>{
    window.previousBody=document.body;
    const replacement=document.createElement('body');
    replacement.innerHTML='<main><p>替换后的正文</p><input id="replacement-input"></main>';
    document.body.replaceWith(replacement);
    const portal=document.createElement('div');portal.id='portal';
    portal.innerHTML='<button id="portal-button">新弹层</button>';
    document.documentElement.append(portal);
    portal.querySelector('button').focus();
  });
  await page.waitForFunction(()=>document.body.inert&&document.getElementById('portal').inert);
  assert.equal(await page.evaluate(()=>document.activeElement.id),'happyu-agent-reading');
  assert.equal(await page.evaluate(()=>previousBody.getAttribute('inert')),'original-value');
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('替换后的正文'));
  await page.evaluate(()=>document.body.inert=false);
  await page.waitForFunction(()=>document.body.inert);
  await page.evaluate(()=>__happyuAgentReading.dispose());
  assert.equal(await page.evaluate(()=>document.body.hasAttribute('inert')||document.getElementById('portal').hasAttribute('inert')),false);
  assert.equal(await page.evaluate(()=>previousBody.getAttribute('inert')),'original-value');
  await page.evaluate(()=>document.body.setAttribute('inert','keep-this'));
  await enable(page);await ready(page);
  await page.evaluate(()=>__happyuAgentReading.dispose());
  assert.equal(await page.evaluate(()=>document.body.getAttribute('inert')),'keep-this');
});

const previewPng = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aG9sAAAAASUVORK5CYII=', 'base64');
async function enablePreview(page, theme=dark) {
  await page.evaluate(({source,theme}) => (0,eval)(source)(theme, url => {
    (window.previewPermissions ||= []).push(url);
    window.allowedPreview = url;
    return Promise.resolve();
  }), {source,theme});
  await ready(page);
}

test('image file cards load only on click despite image blocking, preview follows theme and Escape preserves scroll', async t => {
  const page=await fixture(t,'<main><h1>图片阅读</h1><p style="height:800px">正文内容</p>'
    +'<a href="/leave"><picture><source media="not all" srcset="https://cdn.example.test/fallback.png 1x"><img data-src="https://cdn.example.test/saved-homepage.png?size=large" alt="网页截图"></picture></a>'
    +'<p>下一段文字</p></main>');
  const requests=[];
  await page.route('https://cdn.example.test/**',async route=>{
    requests.push(route.request().url());
    // Simulate the native resource gate: only the explicitly authorized image can load.
    if(await page.evaluate(url=>window.allowedPreview===url,route.request().url())) {
      await route.fulfill({contentType:'image/png',body:previewPng});
    } else await route.abort();
  });
  await page.evaluate(source=>(0,eval)(source)('img,picture {display:none!important;visibility:hidden!important}',true),contentMode);
  await enablePreview(page);
  const reader=page.locator('#happyu-agent-reading');
  const card=reader.getByRole('button',{name:'预览图片 saved-homepage.png',exact:true});
  assert.equal(await card.count(),1);
  assert.equal(await card.locator('.image-card-name').textContent(),'saved-homepage.png');
  assert.equal(await reader.locator('img').count(),0);
  assert.deepEqual(requests,[]);
  await card.scrollIntoViewIfNeeded();
  const scrollTop=await page.evaluate(()=>__happyuAgentReading.viewport.scrollTop);
  await card.click();
  const dialog=reader.getByRole('dialog');
  await dialog.locator('img').waitFor({state:'visible'});
  assert.deepEqual(requests,['https://cdn.example.test/saved-homepage.png?size=large']);
  assert.equal(await dialog.locator('img').evaluate(n=>n.naturalWidth),1);
  assert.equal(await page.locator('main img').evaluate(n=>getComputedStyle(n).display),'none');
  assert.equal(page.url(),'https://example.test/docs/article');
  await enablePreview(page,light);
  assert.equal(await dialog.evaluate(n=>getComputedStyle(n).backgroundColor),'rgb(244, 245, 247)');
  await page.keyboard.press('Escape');
  assert.equal(await dialog.count(),0);
  await page.waitForFunction(()=>window.previewPermissions.at(-1)==='');
  assert.equal(await page.evaluate(()=>__happyuAgentReading.viewport.scrollTop),scrollTop);
  assert.equal(await card.evaluate(n=>n.getRootNode().activeElement===n),true);
});

test('image preview zooms around the pointer, pans by dragging and keeps the reader and dialog size stable', async t => {
  const page=await fixture(t,'<main><p style="height:800px">正文</p><img data-src="https://cdn.example.test/zoom.svg"></main>');
  let requests=0;
  await page.route('https://cdn.example.test/**',route=>{
    requests++;
    return route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="1000"><rect width="1600" height="1000" fill="#4682b4"/></svg>'});
  });
  await enablePreview(page);
  const reader=page.locator('#happyu-agent-reading');
  await reader.locator('.image-card').click();
  const dialog=reader.getByRole('dialog'), stage=dialog.locator('.image-preview-stage'), image=dialog.locator('img');
  await image.waitFor({state:'visible'});
  const initial=await dialog.boundingBox();
  const fittedPercent=await dialog.locator('output').textContent();
  const readingPosition=await page.evaluate(()=>__happyuAgentReading.viewport.scrollTop);
  await dialog.getByRole('button',{name:'原始大小',exact:true}).click();
  assert.equal(await dialog.locator('output').textContent(),'100%');
  assert.equal(Math.round((await image.boundingBox()).width),1600);
  await dialog.getByRole('button',{name:'放大图片',exact:true}).click();
  assert.equal(await dialog.locator('output').textContent(),'125%');
  assert.equal(Math.round((await image.boundingBox()).width),2000);
  assert.equal((await dialog.boundingBox()).height,initial.height);

  const box=await stage.boundingBox(), point={x:box.x+box.width/2+35,y:box.y+box.height/2+20};
  const before=await image.boundingBox();
  const anchor={x:(point.x-before.x)/before.width,y:(point.y-before.y)/before.height};
  await page.mouse.move(point.x,point.y);
  await page.mouse.wheel(0,-200);
  await page.waitForFunction(()=>__happyuAgentReading.preview.zoom>1.5);
  const after=await image.boundingBox();
  assert.ok(Math.abs(after.x+anchor.x*after.width-point.x)<2);
  assert.ok(Math.abs(after.y+anchor.y*after.height-point.y)<2);
  const scrollBefore=await stage.evaluate(n=>({x:n.scrollLeft,y:n.scrollTop}));
  await page.mouse.down();
  await page.mouse.move(point.x-80,point.y-50,{steps:5});
  await page.mouse.up();
  const scrollAfter=await stage.evaluate(n=>({x:n.scrollLeft,y:n.scrollTop,dragging:n.classList.contains('dragging')}));
  assert.ok(scrollAfter.x>=scrollBefore.x+78);
  assert.ok(scrollAfter.y>=scrollBefore.y+48);
  assert.equal(scrollAfter.dragging,false);
  assert.equal(await page.evaluate(()=>__happyuAgentReading.viewport.scrollTop),readingPosition);

  await dialog.getByRole('button',{name:'适应窗口',exact:true}).click();
  assert.equal(await dialog.locator('output').textContent(),fittedPercent);
  assert.deepEqual(await stage.evaluate(n=>[n.scrollLeft,n.scrollTop]),[0,0]);
  await page.keyboard.press('1');
  assert.equal(await dialog.locator('output').textContent(),'100%');
  await page.keyboard.press('-');
  assert.equal(await dialog.locator('output').textContent(),'80%');
  await page.keyboard.press('0');
  assert.equal(await dialog.locator('output').textContent(),fittedPercent);
  assert.equal(requests,1);
  await page.keyboard.press('Escape');
  await page.waitForFunction(()=>window.allowedPreview==='');
  assert.equal(await page.evaluate(()=>__happyuAgentReading.viewport.scrollTop),readingPosition);
});

test('preview zoom respects limits, adapts to a narrow resized window and resets when reopened', async t => {
  const page=await fixture(t,'<main><img data-src="https://cdn.example.test/large.svg"></main>');
  await page.route('https://cdn.example.test/**',route=>route.fulfill({contentType:'image/svg+xml',
    body:'<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1800"><rect width="1200" height="1800" fill="#4682b4"/></svg>'}));
  await enablePreview(page);
  const reader=page.locator('#happyu-agent-reading'), card=reader.locator('.image-card');
  await card.click();
  const dialog=reader.getByRole('dialog'), stage=dialog.locator('.image-preview-stage'), image=dialog.locator('img');
  await image.waitFor({state:'visible'});
  await page.setViewportSize({width:340,height:500});
  await page.waitForFunction(()=>{
    const preview=__happyuAgentReading.preview, image=preview.image.getBoundingClientRect();
    const area=preview.image.closest('.image-preview-stage').getBoundingClientRect();
    // CSS width changes immediately; the browser dispatches the resize handler on a later frame.
    return preview.fitted && preview.dialog.getBoundingClientRect().width<340
      && image.width<=area.width-30 && image.height<=area.height-30;
  });
  const fit=await image.boundingBox(), area=await stage.boundingBox(), bounds=await dialog.boundingBox();
  assert.ok(fit.width<=area.width-30 && fit.height<=area.height-30);
  assert.ok(bounds.y>=0 && bounds.y+bounds.height<=500);
  await dialog.getByRole('button',{name:'原始大小',exact:true}).click();
  await page.setViewportSize({width:538,height:760});
  await page.waitForFunction(()=>__happyuAgentReading.preview.dialog.getBoundingClientRect().width>500);
  assert.equal(await dialog.locator('output').textContent(),'100%');
  for (const [delta,percent,button] of [[-500,'800%','放大图片'],[500,'10%','缩小图片']]) {
    await stage.evaluate((n,delta)=>{
      const rect=n.getBoundingClientRect();
      for(let i=0;i<20;i++) n.dispatchEvent(new WheelEvent('wheel',{deltaY:delta,clientX:rect.x+50,clientY:rect.y+50,cancelable:true,bubbles:true}));
    },delta);
    assert.equal(await dialog.locator('output').textContent(),percent);
    assert.equal(await dialog.getByRole('button',{name:button,exact:true}).isDisabled(),true);
  }
  await page.keyboard.press('Escape');
  await card.click();
  await image.waitFor({state:'visible'});
  assert.equal(await page.evaluate(()=>__happyuAgentReading.preview.fitted),true);
  assert.notEqual(await dialog.locator('output').textContent(),'10%');
  await page.evaluate(()=>__happyuAgentReading.dispose());
  await page.setViewportSize({width:340,height:500});
  assert.equal(await reader.count(),0);
});

test('image sources update dynamically, picture srcset is supported and failed previews can retry', async t => {
  const page=await fixture(t,'<main><p>图片</p><img data-original="https://cdn.example.test/old.png">'
    +'<picture><source media="not all" srcset="https://cdn.example.test/responsive.png 1x, https://cdn.example.test/large.png 2x"><img></picture>'
    +'<img data-src="javascript:window.executed=true"><img src="data:image/png;base64,"></main>');
  let requests=0;
  await page.route('https://cdn.example.test/**',route=>++requests===1?route.abort():route.fulfill({contentType:'image/png',body:previewPng}));
  await enablePreview(page);
  const reader=page.locator('#happyu-agent-reading');
  assert.equal(await reader.locator('.image-card').count(),2);
  assert.equal(await reader.getByRole('button',{name:'预览图片 responsive.png',exact:true}).count(),1);
  await page.evaluate(()=>document.querySelector('main > img').setAttribute('data-original','https://cdn.example.test/new.png'));
  const card=reader.getByRole('button',{name:'预览图片 new.png',exact:true});
  await card.waitFor();
  await card.click();
  const dialog=reader.getByRole('dialog');
  await dialog.getByText('图片加载失败，可重试或切回原网页查看。').waitFor();
  await dialog.getByRole('button',{name:'重试',exact:true}).click();
  await dialog.locator('img').waitFor({state:'visible'});
  assert.equal(requests,2);
  await dialog.getByRole('button',{name:'关闭 · Esc'}).click();
  assert.equal(await dialog.count(),0);
});

test('cached floor images preview after source virtualization and disposal cancels pending image permissions', async t => {
  const page=await fixture(t,tiebaThread.replace('楼主的正文。','楼主的正文。<img data-src="https://cdn.example.test/retained.png">'),
    'https://tieba.baidu.com/p/preview');
  let requests=0;
  await page.route('https://cdn.example.test/**',route=>{ requests++;return route.fulfill({contentType:'image/png',body:previewPng}); });
  await enablePreview(page);
  await page.evaluate(()=>document.querySelector('.pb-content-wrap').remove());
  await page.waitForTimeout(900);await ready(page);
  const card=page.locator('#happyu-agent-reading').getByRole('button',{name:'预览图片 retained.png',exact:true});
  assert.equal(await card.count(),1);
  await card.click();
  await page.locator('#happyu-agent-reading .image-preview img').waitFor({state:'visible'});
  assert.equal(requests,1);
  await page.keyboard.press('Escape');
  await page.evaluate(()=>{
    __happyuAgentReading.previewBridge=url=>new Promise(resolve=>{
      (window.pendingPermissions ||= []).push(url);
      window.releasePermission=resolve;
    });
  });
  await card.click();
  await page.waitForFunction(()=>window.pendingPermissions?.length===1);
  await page.evaluate(()=>{window.old=__happyuAgentReading;old.dispose();releasePermission()});
  await page.waitForFunction(()=>window.pendingPermissions?.at(-1)==='');
  await page.evaluate(()=>releasePermission());
  assert.equal(requests,1);
  assert.equal(await page.locator('#happyu-agent-reading').count(),0);
  assert.equal(await page.evaluate(()=>old.preview),null);
});

test('Tieba video posts include the OP and every loaded reply, then retain them while loading more', async t => {
  // Video posts replace pb-content-wrap with a sibling Artplayer mount. Hot replies have no floor labels.
  const html=tiebaThread.replace('<div class="pb-content-wrap"><div class="richtext-item">楼主的正文。</div></div>',
    '<div id="artplayer-app"><video></video><div>00:08 / 00:50</div><div>倍速 播放 全屏</div></div>')
    .replace(/第[35]楼\s*/g,'');
  const page=await fixture(t,html,'https://tieba.baidu.com/p/video');
  await enable(page);await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  assert.deepEqual(await reader.locator('.post-card>summary').allTextContents(),['楼主 · 正文楼主甲','楼层回复作者乙','楼层回复作者丙']);
  assert.ok((await text(page)).includes('原网页包含视频'));
  for(const word of ['00:08','倍速','侧栏推荐','贴吧成长等级']) assert.ok(!(await text(page)).includes(word));
  assert.equal(await reader.locator('video').count(),0);
  await reader.locator('.replies-card>summary').click();
  assert.equal(await reader.locator('.reply-bubble').first().isVisible(),true);
  const lastRefresh=await page.evaluate(()=>{
    const last=__happyuAgentReading.lastRefresh;
    document.querySelector('#artplayer-app > div').firstChild.data='00:09 / 00:50';
    document.querySelector('#artplayer-app > div').classList.add('art-control-show');
    return last;
  });
  await page.waitForTimeout(900);
  assert.equal(await page.evaluate(()=>__happyuAgentReading.lastRefresh),lastRefresh);
  await page.evaluate(()=>{
    const surface=document.querySelector('.pc-pb-box');
    surface.addEventListener('scroll',()=>{
      const next=document.querySelector('[data-key="post-6"]');
      if(!next.children.length && surface.scrollTop>0){
        document.querySelector('[data-key="post-3"]').replaceChildren();
        document.querySelector('[data-key="post-3"]').style.height='260px';
        next.style.height='';
        next.innerHTML='<div class="pb-comment-item" data-id="post-6"><div class="head-line"><a class="head-name">新作者</a></div><div class="pb-rich-text">视频帖新加载回复。</div></div>';
      }
    });
    __happyuAgentReading.host.shadowRoot.querySelector('.load-more').click();
  });
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('视频帖新加载回复'));
  assert.equal(await reader.locator('.post-card').count(),4);
  assert.ok((await text(page)).includes('第三楼正文'));
  assert.equal(await reader.locator('.replies-card').evaluate(n=>n.open),true);
});

test('Tieba recognizes the whole reply section before the OP media has mounted', async t => {
  const html=tiebaThread.replace('<div class="pb-content-wrap"><div class="richtext-item">楼主的正文。</div></div>','');
  const page=await fixture(t,html,'https://tieba.baidu.com/p/video');
  await enable(page);await ready(page);
  assert.equal(await page.evaluate(()=>__happyuAgentReading.liveRoot.className),'image-text');
  assert.equal(await page.locator('#happyu-agent-reading .post-card').count(),2);
  await page.evaluate(()=>document.querySelector('.pc-pb-title').insertAdjacentHTML('afterend',
    '<div id="artplayer-app"><video></video><span>播放器控制栏</span></div>'));
  await page.waitForFunction(()=>__happyuAgentReading.cards.has('post:op'));
  assert.equal(await page.locator('#happyu-agent-reading .post-card').count(),3);
  assert.ok(!(await text(page)).includes('播放器控制栏'));
});

test('Tieba homepage selects the entire recommendation stream and retains virtualized cards while loading', async t => {
  const topic=(id)=>'<div class="virtual-list-item" data-key="'+id+'"><div class="thread-card virtual-list-item"><div class="thread-card-wrapper"><a class="thread-content-link" href="https://tieba.baidu.com/p/'+id+'?fr=personalize_page"></a><div class="forum-info-warp forum-info-wrapper"><span class="forum-name">来源吧 '+id+'</span><span>关注 100W 贴子 10W</span><div>关注</div></div><div class="thread-link-zone"><div class="thread-title">首页推荐 '+id+'</div><div>推荐摘要 '+id+'</div></div></div></div></div>';
  const page=await fixture(t,'<div class="home-page-wrapper"><div class="container styled-scrollbar" style="height:600px;overflow:auto"><aside>热门吧推荐</aside><div class="center-content virtual-container"><div><div class="thread-container">'
    +[101,102,103,104].map(topic).join('')+'<div class="virtual-list-item" data-key="105" style="height:1800px"></div>'
    +'</div></div></div><aside>右侧热门帖子</aside></div></div>','https://tieba.baidu.com/');
  await enable(page);await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  assert.equal(await reader.locator('.post-card').count(),4);
  assert.equal(await reader.locator('.topic-title').count(),4);
  assert.match(await reader.locator('.post-card>summary').first().textContent(),/阅读帖子.*来源吧 101/);
  assert.ok(!(await text(page)).includes('关注 100W'));
  for(const id of [101,102,103,104]) assert.ok((await text(page)).includes('推荐摘要 '+id));
  assert.ok(!(await text(page)).includes('热门吧推荐'));
  assert.ok(!(await text(page)).includes('右侧热门帖子'));
  assert.equal(await page.evaluate(()=>__happyuAgentReading.scrollTarget.classList.contains('container')),true);
  await page.evaluate(next=>{
    const surface=document.querySelector('.container');
    surface.addEventListener('scroll',()=>{
      if(surface.scrollTop>0 && !window.loadedHomepage){
        window.loadedHomepage=true;
        document.querySelector('[data-key="101"]').replaceChildren();
        document.querySelector('[data-key="101"]').style.height='300px';
        document.querySelector('[data-key="105"]').outerHTML=next;
      }
    });
    __happyuAgentReading.host.shadowRoot.querySelector('.load-more').click();
  },topic(105));
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('首页推荐 105'));
  assert.equal(await reader.locator('.post-card').count(),5);
  assert.ok((await text(page)).includes('首页推荐 101'));
});

test('classic Tieba preserves data-author body wrappers and omits author sidebar decorations', async t => {
  const page=await fixture(t,classicThread,'https://tieba.baidu.com/p/11211436915');
  await enable(page);await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  assert.deepEqual(await reader.locator('.post-card>summary').allTextContents(),['第 1 楼楼主甲','第 3 楼读者乙']);
  const body=await text(page);
  for(const word of ['旧版楼主的正文','加粗文字','正文链接','第三楼正文','2026-11-19 22:41']) assert.ok(body.includes(word));
  for(const word of ['站点导航','声名远扬','等级9','送礼物','违规提示','我也说一句']) assert.ok(!body.includes(word));
  assert.equal(await reader.locator('.post-body ul, .post-body li').count(),0);
  assert.equal(await reader.locator('.replies-card').count(),1);
  assert.equal(await reader.locator('.reply-bubble').count(),2);
  await reader.locator('.replies-card>summary').click();
  assert.equal(await reader.locator('.reply-bubble').first().isVisible(),true);
  assert.match(await reader.locator('.replies-card>summary').textContent(),/2 条/);
  assert.equal(await reader.getByRole('link',{name:'正文链接'}).getAttribute('href'),'https://example.test/reference');
  assert.equal(await page.evaluate(()=>__happyuAgentReading.pageAction.node.closest('.pb_list_pager')!==null),true);
});

test('classic Tieba more-reply and pager controls call the original handlers', async t => {
  const page=await fixture(t,classicThread,'https://tieba.baidu.com/p/11211436915');
  await page.evaluate(()=>{
    window.moreReplies=0;window.replyPages=0;
    document.querySelector('.j_lzl_m').onclick=event=>{
      event.preventDefault();moreReplies++;
      document.querySelector('.lzl_pager').style.display='block';
      document.querySelector('.j_lzl_m').parentElement.remove();
    };
    document.querySelector('.lzl_pager a').onclick=event=>{
      event.preventDefault();replyPages++;
      document.querySelector('.lzl_content_main').textContent='第二页的新回复。';
    };
  });
  await enable(page);await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  await reader.locator('.replies-card>summary').click();
  await reader.getByRole('button',{name:'展开更多楼中楼回复'}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.querySelector('button[data-reader-action]')?.textContent==='2');
  await reader.getByRole('button',{name:'2',exact:true}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('第二页的新回复'));
  assert.deepEqual(await page.evaluate(()=>[moreReplies,replyPages]),[1,1]);
  assert.equal(await reader.locator('.reply-bubble').count(),2);
});

test('classic floor tails remain authoritative when large metadata cannot be parsed', async t => {
  const page=await fixture(t,classicThread,'https://tieba.baidu.com/p/11211436915');
  await page.evaluate(()=>{
    const post=document.querySelector('[data-pid="classic-3"]');
    post.setAttribute('data-field','x'.repeat(9000));
    post.querySelector('.d_post_content').textContent='第1楼只是引用的正文。';
  });
  await enable(page);await ready(page);
  assert.equal(await page.evaluate(()=>__happyuAgentReading.cards.get('post:classic-3').querySelector('summary').textContent),'第 3 楼读者乙');
});

test('classic Tieba list renders topic titles and excerpts without treating data-floor as reply floors', async t => {
  const topic=(id,top)=>'<li class="j_thread_list '+(top?'thread_top':'')+'" data-tid="'+id+'" data-floor="'+id+'" data-field=\'{"reply_num":12}\'><div class="t_con"><div class="j_threadlist_li_left">12</div><div class="threadlist_lz"><div class="threadlist_title"><a class="j_th_tit" href="https://tieba.baidu.com/p/'+id+'" target="_blank">'+(top?'置顶公告':'帖子标题')+'</a></div><div class="threadlist_author"><a class="frs-author-name">主题作者</a></div></div><div class="threadlist_detail"><div class="threadlist_abs">帖子摘要。</div><div class="j_small_wrap"><ul><li>缩略图区域</li></ul></div><div class="threadlist_author">最后回复人</div></div></div></li>';
  const page=await fixture(t,'<aside>侧栏</aside><ul id="thread_list">'+topic(100,true)+topic(101,false)+'</ul><div class="pagination-default"><a class="next pagination-item" href="https://tieba.baidu.com/f?kw=test&pn=50">下一页&gt;</a></div>','https://tieba.baidu.com/f?kw=test');
  await enable(page);await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  assert.deepEqual(await reader.locator('.post-card>summary').allTextContents(),['置顶帖子主题作者','阅读帖子主题作者']);
  for(const word of ['帖子标题','帖子摘要','回复 12']) assert.ok((await text(page)).includes(word));
  for(const word of ['侧栏','缩略图区域','最后回复人']) assert.ok(!(await text(page)).includes(word));
  assert.equal(await reader.locator('.topic-title').last().getAttribute('href'),'https://tieba.baidu.com/p/101');
  assert.equal(await page.evaluate(()=>__happyuAgentReading.pageAction.node.classList.contains('next')),true);
});

test('saved Tieba structure has separate OP, floor cards and locally expandable reply bubbles', async t => {
  const page=await fixture(t,tiebaThread,'https://tieba.baidu.com/p/11211436915');
  await page.evaluate(()=>{
    window.expands=0;
    document.querySelector('.show-more-lzl').onclick=()=>{
      expands++;
      document.querySelector('.lzl-wrapper').insertAdjacentHTML('beforeend','<div class="pb-lzl-item"><div class="head-line"><a class="head-name">回复者三</a></div><div class="pb-rich-text">新加载的回复。</div></div>');
    };
  });
  await enable(page);await ready(page);
  const root=page.locator('#happyu-agent-reading');
  assert.deepEqual(await root.locator('.post-card>summary').allTextContents(),['楼主 · 正文楼主甲','第 3 楼作者乙','第 5 楼作者丙']);
  assert.equal(await root.locator('.reply-bubble').count(),2);
  for(const word of ['侧栏推荐','贴吧成长等级','本吧头衔','转发 123','去APP','3137']) assert.ok(!(await text(page)).includes(word));
  await root.locator('.replies-card>summary').click();
  assert.equal(await page.evaluate(()=>expands),0);
  assert.equal(await root.locator('.reply-bubble').first().isVisible(),true);
  assert.equal(await root.locator('.reply-bubble .pb-content-item').count(),0);
  // Inline pieces must remain on one text line rather than becoming separate DIV blocks.
  assert.equal(await root.locator('.reply-bubble').first().locator('a').evaluate(n=>getComputedStyle(n.parentElement).display),'inline');
  await root.getByRole('button',{name:'展开 65 条回复'}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.querySelectorAll('.reply-bubble').length===3);
  assert.equal(await page.evaluate(()=>expands),1);
  assert.equal(await root.locator('.replies-card').evaluate(n=>n.open),true);
});

test('Tieba virtual-list loading retains old floors, stable IDs, expanded replies and reader position', async t => {
  const page=await fixture(t,tiebaThread,'https://tieba.baidu.com/p/11211436915');
  await page.evaluate(()=>{
    const surface=document.querySelector('.pc-pb-box');
    window.sourceMoves=[];
    surface.addEventListener('scroll',()=>{
      sourceMoves.push(surface.scrollTop);
      const next=document.querySelector('[data-key="post-6"]');
      if(!next.children.length && surface.scrollTop>100){
        const previous=document.querySelector('[data-id="post-5"]').cloneNode(true);
        previous.querySelector('.pb-rich-text').textContent='第五楼更新。';
        document.querySelector('[data-id="post-5"]').replaceWith(previous);
        document.querySelector('[data-key="post-3"]').replaceChildren();
        document.querySelector('[data-key="post-3"]').style.height='260px';
        next.style.height='';
        next.innerHTML='<div class="pb-comment-item" data-id="post-6"><div class="head-line"><a class="head-name">作者丁</a></div><div class="pb-rich-text">第六楼新内容。</div><div class="comment-desc-left">第6楼 刚刚</div></div>';
      }
    });
  });
  await enable(page);await ready(page);
  const before=await page.evaluate(()=>{
    const s=__happyuAgentReading;
    s.content.querySelector('.replies-card').open=true;
    s.viewport.scrollTop=120;
    s.lastScroll=120;
    window.originalFloor=s.cards.get('post:post-3');
    window.removedRetainedFloor=false;
    window.retentionObserver=new MutationObserver(records=>{
      if(records.some(r=>[...r.removedNodes].includes(originalFloor))) removedRetainedFloor=true;
    });
    retentionObserver.observe(s.content,{childList:true,subtree:true});
    return s.viewport.scrollTop;
  });
  assert.ok(before>0);
  await page.evaluate(()=>__happyuAgentReading.host.shadowRoot.querySelector('.load-more').click());
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('第六楼新内容'));
  const result=await page.evaluate(()=>({
    count:__happyuAgentReading.content.querySelectorAll('.post-card').length,
    text:__happyuAgentReading.content.textContent,
    top:__happyuAgentReading.viewport.scrollTop,
    same:originalFloor===__happyuAgentReading.cards.get('post:post-3'),
    detached:removedRetainedFloor,
    open:originalFloor.querySelector('.replies-card').open,
    sourceTop:document.querySelector('.pc-pb-box').scrollTop,
    sourceRange:document.querySelector('.pc-pb-box').scrollHeight-600,
  }));
  assert.equal(result.count,4);
  assert.ok(result.text.includes('第三楼正文') && result.text.includes('第五楼更新'));
  assert.equal(result.top,before);assert.equal(result.same,true);assert.equal(result.open,true);
  assert.equal(result.detached,false);
  assert.ok(result.sourceTop>0 && result.sourceTop<result.sourceRange);
  await page.evaluate(()=>__happyuAgentReading.refresh());await ready(page);
  assert.equal(await page.locator('#happyu-agent-reading .post-card').count(),4);
});

test('Tieba filters reset retained history instead of mixing old and filtered floors', async t => {
  const page=await fixture(t,tiebaThread,'https://tieba.baidu.com/p/11211436915');
  await page.evaluate(()=>document.getElementById('only').onclick=event=>{
    event.preventDefault();document.querySelector('.thread-container').replaceChildren();
  });
  await enable(page);await ready(page);
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'只看楼主'}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.querySelectorAll('.post-card').length===1);
  assert.equal(await page.evaluate(()=>__happyuAgentReading.entries.size),1);
});

test('new virtual batches use source floor labels even when those labels have no layout boxes', async t => {
  const page=await fixture(t,tiebaThread,'https://tieba.baidu.com/p/11211436915');
  await enable(page);await ready(page);
  await page.evaluate(()=>{
    document.querySelector('.thread-container').innerHTML=[105,108].map((floor,index)=>
      '<div class="virtual-list-item" data-index="'+index+'"><div class="pb-comment-item" data-id="new-'+floor+'">'
      +'<div class="head-line"><a class="head-name">新作者</a></div><div class="pb-rich-text">新一批正文</div>'
      +'<div class="pc-pb-comments-desc"><div class="comment-desc-left" style="display:none">第 <span>'+floor+'</span> 楼 刚刚</div></div></div></div>').join('');
    __happyuAgentReading.refresh();
  });
  await ready(page);
  assert.deepEqual(await page.locator('#happyu-agent-reading .post-card>summary').allTextContents(),
    ['楼主 · 正文楼主甲','第 3 楼作者乙','第 5 楼作者丙','第 105 楼新作者','第 108 楼新作者']);
});

test('a recreated post retains its known floor until authoritative metadata changes', async t => {
  const page=await fixture(t,tiebaThread,'https://tieba.baidu.com/p/11211436915');
  await enable(page);await ready(page);
  await page.evaluate(()=>{
    const previous=document.querySelector('[data-id="post-5"]');
    const next=previous.cloneNode(true);
    next.querySelector('.comment-desc-left').textContent='刚刚';
    next.querySelector('.pb-rich-text').textContent='第2楼只是正文中引用的楼层，不是本楼编号。';
    previous.replaceWith(next);
    __happyuAgentReading.refresh();
  });
  await ready(page);
  assert.equal(await page.evaluate(()=>__happyuAgentReading.cards.get('post:post-5').querySelector('summary').textContent),'第 5 楼作者丙');
  await page.evaluate(()=>document.querySelector('[data-id="post-5"]').setAttribute('data-field','{"content":{"post_no":205}}'));
  await page.waitForFunction(()=>__happyuAgentReading.cards.get('post:post-5').querySelector('summary').textContent==='第 205 楼作者丙');
});

test('unknown floors do not fabricate numbers from batch indexes, body quotations or nested replies', async t => {
  const page=await fixture(t,tiebaThread,'https://tieba.baidu.com/p/11211436915');
  await enable(page);await ready(page);
  await page.evaluate(()=>{
    document.querySelector('.thread-container').innerHTML='<div class="virtual-list-item" data-index="20"><div class="pb-comment-item" data-id="unknown">'
      +'<div class="head-line"><a class="head-name">未知作者</a></div><div class="pb-rich-text">第1楼只是引用</div>'
      +'<div class="lzl-wrapper"><div class="pb-lzl-item"><div class="comment-desc-left">第9楼也是引用</div></div></div></div></div>';
    __happyuAgentReading.refresh();
  });
  await ready(page);
  assert.equal(await page.evaluate(()=>__happyuAgentReading.cards.get('post:unknown').querySelector('summary').textContent),'楼层回复未知作者');
  await page.evaluate(()=>document.querySelector('[data-id="unknown"]').setAttribute('data-floor','260'));
  await page.waitForFunction(()=>__happyuAgentReading.cards.get('post:unknown').querySelector('summary').textContent==='第 260 楼未知作者');
});

test('Tieba forum listing keeps pinned topics, safe title links and virtualized topic history', async t => {
  const topic=(id,title)=>'<div class="virtual-list-item" data-key="'+id+'"><div class="thread-card"><div class="thread-card-wrapper"><a class="thread-content-link" href="https://tieba.baidu.com/p/'+id+'?fr=frs"></a><div class="head-line"><a class="head-name">作者</a>关注</div><div class="thread-link-zone"><div class="thread-title"><span>'+title+'</span></div><div>帖子摘要</div></div><div class="thread-action-area">分享123</div></div></div></div>';
  const page=await fixture(t,'<div class="frs-page-wrap" style="height:600px;overflow:auto"><div class="frs-feed-list"><div class="top-thread-card"><a class="top-thread-card-item" href="https://tieba.baidu.com/p/100"><div class="thread-title">置顶公告</div></a></div><div class="feed-list-container">'+topic(101,'第一个帖子')+topic(102,'第二个帖子')+'</div></div></div>','https://tieba.baidu.com/f?kw=test');
  await enable(page);await ready(page);
  assert.equal(await page.locator('#happyu-agent-reading .post-card').count(),3);
  assert.equal(await page.locator('#happyu-agent-reading .topic-title').first().getAttribute('href'),'https://tieba.baidu.com/p/100');
  assert.ok(!(await text(page)).includes('分享123'));
  await page.evaluate(html=>document.querySelector('.feed-list-container').innerHTML=html,topic(103,'后续帖子'));
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('后续帖子'));
  assert.equal(await page.locator('#happyu-agent-reading .post-card').count(),4);
  assert.ok((await text(page)).includes('第一个帖子'));
});

test('Tieba retained history is bounded and disposal releases cached cards', async t => {
  const page=await fixture(t,tiebaThread,'https://tieba.baidu.com/p/11211436915');
  await enable(page);await ready(page);
  for(let batch=0;batch<6;batch++) {
    await page.evaluate(batch=>{
      document.querySelector('.thread-container').innerHTML=Array.from({length:100},(_,i)=>
        '<div class="pb-comment-item" data-id="batch-'+(batch*100+i)+'"><div class="pb-rich-text">正文 '+i+'</div></div>').join('');
      __happyuAgentReading.refresh();
    },batch);
    await ready(page);
  }
  assert.equal(await page.evaluate(()=>__happyuAgentReading.entries.size),500);
  assert.equal(await page.locator('#happyu-agent-reading').getByRole('button',{name:'已达到保留上限'}).isDisabled(),true);
  assert.match(await page.locator('#happyu-agent-reading .status').textContent(),/保留上限/);
  assert.deepEqual(await page.evaluate(()=>{
    const s=__happyuAgentReading;s.dispose();return {entries:s.entries.size,cards:s.cards.size,root:s.tiebaRoot};
  }),{entries:0,cards:0,root:null});
});

test('extracts only the main content, preserving semantic blocks and safe links', async t => {
  const page = await fixture(t, '<title>阅读示例</title><header>Navigation</header><main><article>'
    + '<h1>正文标题</h1><p>这是正文。<a href="next">下一页</a></p><h2>步骤</h2><ol><li>第一项</li></ol>'
    + '<pre><code>const answer = 42;</code></pre><table><tr><td>表格内容</td></tr></table>'
    + '<div hidden>Hidden content</div></article></main><aside>Sidebar</aside><footer>Footer</footer>');
  await enable(page); await ready(page);
  const result = await text(page);
  for (const word of ['正文标题','这是正文','第一项','const answer = 42;','表格内容']) assert.ok(result.includes(word));
  for (const word of ['Navigation','Sidebar','Footer','Hidden content']) assert.ok(!result.includes(word));
  assert.equal(await page.evaluate(() => __happyuAgentReading.content.querySelector('a').href),
    'https://example.test/docs/next');
  await page.route('https://example.test/docs/next', route => route.fulfill({body:'<h1>Next page</h1>'}));
  await Promise.all([page.waitForURL('https://example.test/docs/next'),
    page.locator('#happyu-agent-reading').getByRole('link', {name:'下一页'}).click()]);
});

test('copies no executable attributes, scripts, forms or media', async t => {
  const page = await fixture(t, '<article><p onclick="window.executed=true">Safe text</p>'
    + '<a href="javascript:window.executed=true" onclick="window.executed=true">Unsafe link</a>'
    + '<form><input value="Secret value"><button>Submit</button></form>'
    + '<script>window.executed=false</script><img src="data:image/png;base64," onerror="window.media=true">'
    + '<iframe src="about:blank"></iframe></article>');
  await enable(page); await ready(page);
  const result = await page.evaluate(() => {
    const content = __happyuAgentReading.content;
    content.querySelector('p').click(); content.querySelector('a').click();
    return {executable:content.querySelectorAll('script,iframe,img,input,form,button,[onclick],[onerror]').length,
      unsafe:content.querySelector('a').hasAttribute('href'), executed:window.executed, text:content.textContent};
  });
  assert.equal(result.executable,0); assert.equal(result.unsafe,false); assert.equal(result.executed,false);
  assert.ok(!result.text.includes('Secret value'));
});

test('disabling restores the same live page and preserves input and event handlers', async t => {
  const page = await fixture(t, '<article><p>Original</p><input id="draft" value="draft"><button id="action">Action</button></article>');
  await page.evaluate(() => {
    window.original = document.querySelector('article'); window.clicked=0;
    document.getElementById('action').addEventListener('click', () => clicked++);
    document.documentElement.style.overflow='auto';
  });
  await enable(page); await ready(page);
  await page.evaluate(() => __happyuAgentReading.dispose());
  assert.deepEqual(await page.evaluate(() => {
    document.getElementById('action').click();
    return {same:original===document.querySelector('article'), draft:document.getElementById('draft').value,
      clicked, host:document.getElementById('happyu-agent-reading'),
      marker:document.documentElement.hasAttribute('data-happyu-agent-reading'), overflow:document.documentElement.style.overflow};
  }), {same:true,draft:'draft',clicked:1,host:null,marker:false,overflow:'auto'});
});

test('theme updates do not rebuild text and source mutations refresh automatically', async t => {
  const page = await fixture(t, '<article><p>First snapshot</p></article>');
  await enable(page); await ready(page);
  await page.evaluate(() => { window.copied = __happyuAgentReading.content.firstChild; document.querySelector('article p').textContent='Changed'; });
  await enable(page,light);
  assert.equal(await page.evaluate(() => copied===__happyuAgentReading.content.firstChild),true);
  assert.ok((await text(page)).includes('First snapshot'));
  assert.equal(await page.evaluate(() => getComputedStyle(__happyuAgentReading.host).backgroundColor),'rgb(244, 245, 247)');
  await page.waitForFunction(() => __happyuAgentReading.content.textContent.includes('Changed'));
  assert.ok((await text(page)).includes('Changed'));
});

test('coalesces source changes, ignores unrelated sidebars and does not observe its own rendering', async t => {
  const page = await fixture(t, '<main><p>Initial</p></main><aside id="ticker">Sidebar</aside>');
  await enable(page); await ready(page);
  await page.evaluate(() => {
    window.refreshes=0;
    const refresh=__happyuAgentReading.refresh;
    __happyuAgentReading.refresh=()=>{refreshes++;refresh()};
    for(let i=0;i<100;i++) document.querySelector('main p').textContent='Update '+i;
    document.getElementById('ticker').textContent='Ignored';
  });
  await page.waitForFunction(() => __happyuAgentReading.content.textContent.includes('Update 99'));
  await page.waitForTimeout(1000);
  assert.equal(await page.evaluate(() => refreshes),1);
  await page.evaluate(() => document.getElementById('ticker').textContent='Still ignored');
  await page.waitForTimeout(800);
  assert.equal(await page.evaluate(() => refreshes),1);
});

const forum = '<title>讨论：在 IDE 中阅读论坛</title><main id="thread">'
  + '<div class="l_post" data-floor="1"><span class="p_author_name">楼主</span>'
  + '<div class="d_post_content"><p>这是一楼正文，回复用不同气泡显示。</p></div>'
  + '<button id="view-replies">查看 2 条回复</button><div class="j_lzl_container" hidden></div>'
  + '<form><button id="publish">发表回复</button></form></div>'
  + '<div class="l_post" data-floor="2"><span class="p_author_name">读者 A</span>'
  + '<div class="d_post_content"><p>二楼内容</p></div></div></main>';

test('forum floors have independent collapsible cards and lazy replies expand into separate bubbles', async t => {
  const page = await fixture(t,forum);
  await page.evaluate(() => {
    window.replyLoads=0; window.published=0;
    document.getElementById('view-replies').onclick=()=>{
      replyLoads++;
      const group=document.querySelector('.j_lzl_container'); group.hidden=false;
      group.innerHTML='<div class="lzl_single_post"><a class="lzl_p_p">读者 B</a>：第一条楼中楼回复</div>'
        + '<div class="lzl_single_post"><a class="lzl_p_p">楼主</a>：第二条楼中楼回复</div>';
    };
    document.getElementById('publish').onclick=()=>published++;
  });
  await enable(page); await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  assert.equal(await reader.locator('.post-card').count(),2);
  assert.equal(await reader.locator('.replies-card').evaluate(el=>el.open),false);
  await reader.locator('.replies-card > summary').click();
  await page.waitForFunction(()=>__happyuAgentReading.content.querySelectorAll('.reply-bubble').length===2);
  assert.equal(await reader.locator('.replies-card').evaluate(el=>el.open),true);
  assert.ok((await reader.locator('.replies-card > summary').textContent()).includes('2 条'));
  assert.ok((await reader.locator('.post-card > summary').first().textContent()).includes('楼主'));
  assert.deepEqual(await page.evaluate(()=>({loads:replyLoads,published})),{loads:1,published:0});
  await reader.locator('.post-card > summary').nth(1).click();
  await page.evaluate(()=>document.querySelectorAll('.d_post_content')[1].append('动态追加的二楼内容'));
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('动态追加的二楼内容'));
  assert.equal(await reader.locator('.post-card').nth(1).evaluate(el=>el.open),false);
  assert.equal(await reader.locator('.replies-card').evaluate(el=>el.open),true);
  assert.equal(await reader.getByRole('button',{name:'发表回复'}).count(),0);
  await page.evaluate(()=>{ window.unchanged=__happyuAgentReading.content.querySelector('.post-card');
    document.querySelectorAll('.d_post_content')[1].append('，仅二楼再次更新'); });
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('仅二楼再次更新'));
  assert.equal(await page.evaluate(()=>unchanged===__happyuAgentReading.content.querySelector('.post-card')),true);
  const output=join(__dirname,'../../../build/tmp/agent-reading-preview'); mkdirSync(output,{recursive:true});
  for (const [name,theme] of [['dark',dark],['light',light]]) {
    await enable(page,theme);
    for(const width of [538,280]) {
      await page.setViewportSize({width,height:760});
      assert.equal(await page.evaluate(()=>__happyuAgentReading.viewport.scrollWidth<=__happyuAgentReading.viewport.clientWidth),true);
      await page.screenshot({path:join(output,`forum-${name}-${width}.png`)});
    }
  }
});

test('downward scrolling drives source infinite loading and preserves the current floor on updates', async t => {
  const page = await fixture(t,'<main id="thread">'+Array.from({length:12},(_,i)=>
    `<div class="l_post" data-floor="${i+1}"><p>${('第'+(i+1)+'楼，正文。').repeat(40)}</p></div>`).join('')+'</main>');
  await page.evaluate(()=>{
    window.loaded=false; window.sourceScrollEvents=0;
    window.addEventListener('scroll',()=>{
      sourceScrollEvents++;
      if(!loaded && window.scrollY+innerHeight>=document.scrollingElement.scrollHeight-10) {
        loaded=true;
        const floor=document.createElement('div'); floor.className='l_post'; floor.dataset.floor='13';
        floor.textContent='下滑加载的第十三楼'; document.querySelector('main').append(floor);
      }
    });
  });
  await enable(page); await ready(page);
  await page.evaluate(()=>{
    const state=__happyuAgentReading;
    state.viewport.scrollTop=state.viewport.scrollHeight/4;
  });
  await page.waitForFunction(()=>scrollY>0);
  await page.evaluate(()=>{
    const state=__happyuAgentReading;
    state.viewport.scrollTop=state.viewport.scrollHeight;
  });
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('下滑加载的第十三楼'));
  assert.ok(await page.evaluate(()=>sourceScrollEvents>0 && scrollY>0));
  await page.evaluate(()=>{
    const state=__happyuAgentReading;
    const target=state.content.querySelectorAll('.post-card')[7];
    state.viewport.scrollTop+=target.getBoundingClientRect().top-100;
  });
  await page.waitForTimeout(150);
  const before=await page.evaluate(()=>__happyuAgentReading.content.querySelectorAll('.post-card')[7].getBoundingClientRect().top);
  await page.evaluate(()=>document.querySelector('.l_post p').append('在上方新增加的长内容。'.repeat(100)));
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('在上方新增加的长内容'));
  const after=await page.evaluate(()=>__happyuAgentReading.content.querySelectorAll('.post-card')[7].getBoundingClientRect().top);
  assert.ok(Math.abs(before-after)<2,`anchor moved from ${before} to ${after}`);
  await page.evaluate(()=>__happyuAgentReading.dispose());
  assert.equal(await page.evaluate(()=>scrollY),0);
});

test('short readers can retry bottom loading, and safe load-more controls use original handlers', async t => {
  const page=await fixture(t,'<main><p>Initial short page</p><button id="more">加载更多</button></main>');
  await page.evaluate(()=>{
    window.bottomEvents=0;
    window.addEventListener('scroll',()=>bottomEvents++);
    document.getElementById('more').onclick=()=>{
      document.querySelector('main p').append('，点击加载后的内容'); document.getElementById('more').remove();
    };
  });
  await enable(page); await ready(page);
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'加载更多',exact:true}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('点击加载后的内容'));
  await page.locator('#happyu-agent-reading').hover(); await page.mouse.wheel(0,500);
  await page.waitForFunction(()=>bottomEvents>0);
});

test('counted forum expand controls and inline reply pagination preserve original behavior', async t => {
  const page=await fixture(t,'<div class="l_post" data-field=\'{"content":{"post_no":51}}\'>'
    + '<span class="p_author_name">分页作者</span><p>第五十一楼</p>'
    + '<a class="j_lzl_link_unfold" href="javascript:void(0)">回复(2)</a>'
    + '<div class="j_lzl_container" hidden><div class="lzl_single_post">第一页回复</div>'
    + '<div class="lzl_pager"><a href="#">2</a></div></div><button id="compose">回复</button></div>');
  await page.evaluate(()=>{
    window.composeCount=0;
    document.getElementById('compose').onclick=()=>composeCount++;
    document.querySelector('.j_lzl_link_unfold').onclick=event=>{
      event.preventDefault();document.querySelector('.j_lzl_container').hidden=false;
    };
    document.querySelector('.lzl_pager a').onclick=event=>{
      event.preventDefault();document.querySelector('.lzl_single_post').textContent='第二页回复';
    };
  });
  await enable(page); await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  assert.ok((await reader.locator('.post-card > summary').textContent()).includes('第 51 楼'));
  await reader.locator('.replies-card > summary').click();
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('第一页回复'));
  await reader.getByRole('button',{name:'2',exact:true}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('第二页回复'));
  assert.equal(await page.evaluate(()=>composeCount),0);
  assert.equal(await reader.getByRole('button',{name:'回复',exact:true}).count(),0);
});

test('uses the original inner scroll container for infinite loading', async t => {
  const page=await fixture(t,'<main style="height:200px;overflow:auto"><p style="height:1200px">Inner content</p></main>');
  await page.evaluate(()=>{
    document.querySelector('main').addEventListener('scroll',()=>{
      const main=document.querySelector('main');
      if(main.scrollTop>0 && !document.getElementById('loaded')) {
        const p=document.createElement('p');p.id='loaded';p.textContent='Inner lazy content';main.append(p);
      }
    });
  });
  await enable(page); await ready(page);
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'尝试加载更多'}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('Inner lazy content'));
  await page.evaluate(()=>__happyuAgentReading.dispose());
  assert.equal(await page.evaluate(()=>document.querySelector('main').scrollTop),0);
});

const modernFloor = (number, author, body) => '<section class="b7ef2"><div class="a91c">'
  + `<a href="/home/main?un=${encodeURIComponent(author)}">${author}</a></div><div class="c81e"><p>${body}</p></div>`
  + `<div class="f50b"><span>第${number}楼6小时前江西</span><div role="button" class="d263"><span>1 回复</span></div></div></section>`;

test('anonymous modern forum markup uses actual floor numbers, author headers and counted replies', async t => {
  const page=await fixture(t,'<main><div class="f872">全部回复 (47)</div><div class="a052">'
    + modernFloor(2,'THExq','分析内容') + modernFloor(3,'workbuddy','第二条讨论') + '</div></main>');
  await page.evaluate(()=>{
    document.querySelector('[role="button"]').onclick=()=>{
      const group=document.createElement('div');group.className='sub-reply-list';
      group.innerHTML='<div class="d198"><a href="/home/main?un=读者">读者</a><p>实际楼中楼内容</p></div>';
      document.querySelector('section').append(group);
    };
  });
  await enable(page); await ready(page);
  const reader=page.locator('#happyu-agent-reading');
  assert.equal(await reader.locator('.post-card').count(),2);
  assert.match(await reader.locator('.post-card > summary').nth(0).textContent(),/第 2 楼.*THExq/);
  assert.match(await reader.locator('.post-card > summary').nth(1).textContent(),/第 3 楼.*workbuddy/);
  await reader.getByRole('button',{name:'展开 1 条楼中楼回复'}).first().click();
  await page.waitForFunction(()=>__happyuAgentReading.content.querySelector('.reply-bubble')?.textContent.includes('实际楼中楼内容'));
  assert.equal(await reader.locator('.replies-card').evaluate(el=>el.open),true);
  const output=join(__dirname,'../../../build/tmp/agent-reading-preview');mkdirSync(output,{recursive:true});
  await page.screenshot({path:join(output,'modern-forum-dark.png')});
});

test('split floor labels and a single remaining floor retain card styling after filtering', async t => {
  const page=await fixture(t,'<main><button id="only">只看楼主</button><div>'
    + modernFloor(2,'A','楼主内容').replace('第2楼6小时前江西','第<span>2</span>楼6小时前江西')
    + modernFloor(3,'B','其他回复')+'</div></main>','https://tieba.baidu.com/p/fixture');
  await page.evaluate(()=>document.getElementById('only').onclick=()=>document.querySelectorAll('section')[1].remove());
  await enable(page);await ready(page);
  assert.equal(await page.locator('#happyu-agent-reading .post-card').count(),2);
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'只看楼主'}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.querySelectorAll('.post-card').length===1);
  assert.match(await page.locator('#happyu-agent-reading .post-card > summary').textContent(),/第 2 楼.*A/);
});

test('bottom load button calls source pagination outside the extracted main content', async t => {
  const page=await fixture(t,'<main><div>'+modernFloor(2,'A','第一页')+modernFloor(3,'B','第一页')+'</div></main>'
    + '<footer><nav><button id="next">下一页</button></nav></footer>');
  await page.evaluate(()=>{
    window.nextClicks=0;
    document.getElementById('next').onclick=()=>{
      nextClicks++;document.querySelector('main > div').insertAdjacentHTML('beforeend',
        '<section><a href="/home/main?un=C">C</a><p>分页加载的后续内容</p><span>第4楼刚刚</span></section>');
    };
  });
  await enable(page); await ready(page);
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'下一页',exact:true}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('分页加载的后续内容'));
  assert.equal(await page.evaluate(()=>nextClicks),1);
  assert.equal(await page.locator('#happyu-agent-reading .post-card').count(),3);
  assert.ok((await page.locator('#happyu-agent-reading .load-status').textContent()).includes('已更新'));
  assert.equal(await page.evaluate(()=>__happyuAgentReading.viewport.scrollTop),0);
});

test('downward loading calls a source load-more button and reports a no-content result', async t => {
  const page=await fixture(t,'<main><p>Short feed</p></main><nav><button id="more">加载更多</button></nav>');
  await page.evaluate(()=>{
    window.moreClicks=0;
    document.getElementById('more').onclick=()=>{
      moreClicks++;
      if(moreClicks===1) document.querySelector('main p').append('，新的内容');
    };
  });
  await enable(page); await ready(page);
  await page.locator('#happyu-agent-reading').hover();await page.mouse.wheel(0,600);
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('新的内容'));
  assert.equal(await page.evaluate(()=>moreClicks),1);
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'尝试加载更多'}).click();
  assert.equal(await page.locator('#happyu-agent-reading').getByRole('button',{name:'正在加载…'}).isDisabled(),true);
  await page.waitForFunction(()=>!__happyuAgentReading.loading);
  assert.ok((await page.locator('#happyu-agent-reading .load-status').textContent()).includes('未加载到新内容'));
  assert.equal(await page.evaluate(()=>moreClicks),2);
});

test('finds an anonymous nested scroll surface without old forum class names', async t => {
  const page=await fixture(t,'<main><div id="surface" class="e38c" style="height:600px;overflow:auto">'
    + '<div style="height:2000px">'+modernFloor(2,'A','内容')+modernFloor(3,'B','内容')+'</div></div></main>');
  await page.evaluate(()=>{
    const surface=document.getElementById('surface');
    surface.addEventListener('scroll',()=>{
      if(surface.scrollTop>0 && !document.getElementById('new-content')){
        const p=document.createElement('p');p.id='new-content';p.textContent='内层滚动追加内容';surface.firstElementChild.append(p);
      }
    });
  });
  await enable(page); await ready(page);
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'尝试加载更多'}).click();
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('内层滚动追加内容'));
  assert.equal(await page.evaluate(()=>__happyuAgentReading.scrollTarget.id),'surface');
});

test('reply dialogs mounted outside main are extracted and observed after opening a counted reply', async t => {
  const page=await fixture(t,'<main><div>'+modernFloor(2,'A','内容')+modernFloor(3,'B','内容')+'</div></main>');
  await page.evaluate(()=>{
    document.querySelector('[role="button"]').onclick=()=>{
      const pane=document.createElement('div');pane.setAttribute('role','dialog');
      pane.innerHTML='<header><button aria-label="关闭">×</button></header>'
        +'<div class="f31a"><a href="/home/main?un=读者A">读者 A</a><p>弹窗第一条回复</p></div>'
        +'<div class="a3e1"><a href="/home/main?un=读者B">读者 B</a><p>弹窗第二条回复</p></div>';
      pane.querySelector('button').onclick=()=>pane.remove();
      document.body.append(pane);
    };
  });
  await enable(page); await ready(page);
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'展开 1 条楼中楼回复'}).first().click();
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('弹窗第二条回复'));
  assert.equal(await page.locator('#happyu-agent-reading .reply-bubble').count(),2);
  await page.evaluate(()=>document.querySelector('[role="dialog"] p').textContent='弹窗内容动态更新');
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('弹窗内容动态更新'));
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'关闭回复'}).click();
  await page.waitForFunction(()=>!__happyuAgentReading.content.textContent.includes('弹窗内容动态更新'));
  assert.equal(await page.locator('[role="dialog"]').count(),0);
});

test('bottom retry notifies document scroll listeners and cancels load feedback on disposal', async t => {
  const page=await fixture(t,'<main><p>Short page</p></main>');
  await page.evaluate(()=>{
    window.documentScrolls=0;document.addEventListener('scroll',()=>documentScrolls++);
  });
  await enable(page);await ready(page);
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'尝试加载更多'}).click();
  await page.waitForFunction(()=>documentScrolls>0);
  await page.evaluate(()=>{window.old=__happyuAgentReading;old.dispose()});
  assert.equal(await page.evaluate(()=>old.loadTimer==null),true);
  const label=await page.evaluate(()=>old.host.shadowRoot.querySelector('.load-status').textContent);
  await page.waitForTimeout(2700);
  assert.equal(await page.evaluate(()=>old.host.shadowRoot.querySelector('.load-status').textContent),label);
});

test('source replacement and style-only reveal update the reader, and disabling cancels pending refreshes', async t => {
  const page=await fixture(t,'<main><p>Old main</p><div style="display:none">Revealed by style</div></main>');
  await enable(page); await ready(page);
  await page.evaluate(()=>document.querySelector('main div').style.display='block');
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('Revealed by style'));
  await page.evaluate(()=>document.querySelector('main').outerHTML='<main><p>Replacement main</p></main>');
  await page.waitForFunction(()=>__happyuAgentReading.content.textContent.includes('Replacement main'));
  await page.evaluate(()=>{document.querySelector('main p').textContent='Pending';});
  await page.waitForFunction(()=>!!__happyuAgentReading.updateTimer);
  await page.evaluate(()=>{window.old=__happyuAgentReading;old.dispose();document.querySelector('main p').textContent='After disposal'});
  await page.waitForTimeout(850);
  assert.deepEqual(await page.evaluate(()=>({disposed:old.disposed,root:old.liveRoot,actions:old.actions.size,cards:old.cards.size,
    reader:!!window.__happyuAgentReading})),{disposed:true,root:null,actions:0,cards:0,reader:false});
});

test('a finished hidden reader schedules no work and resumes the latest changes once visible', async t => {
  const page=await fixture(t,'<main><p>Visible content</p></main>');
  await enable(page); await ready(page);
  await deterministic(page);
  await page.evaluate(()=>{hide(true);for(let i=0;i<100;i++)document.querySelector('main p').textContent='Hidden update '+i});
  assert.deepEqual(await page.evaluate(()=>({timers:timersPending.size,frames:framesPending.size,needs:__happyuAgentReading.needsRefresh})),
    {timers:0,frames:0,needs:true});
  await page.evaluate(()=>{hide(false);while(__happyuAgentReading.cursor)tick()});
  assert.ok((await text(page)).includes('Hidden update 99'));
});

async function deterministic(page, hidden=false) {
  await page.evaluate(hidden => {
    window.testHidden=hidden;
    Object.defineProperty(document,'hidden',{get:()=>testHidden});
    Object.defineProperty(performance,'now',{value:()=>0});
    window.framesPending=new Map(); window.timersPending=new Map(); let id=0;
    window.requestAnimationFrame=fn=>{framesPending.set(++id,fn);return id};
    window.cancelAnimationFrame=id=>framesPending.delete(id);
    window.setTimeout=fn=>{timersPending.set(++id,fn);return id};
    window.clearTimeout=id=>timersPending.delete(id);
    window.tick=()=>{const entry=framesPending.entries().next().value;if(entry)entry[1]()};
    window.hide=value=>{testHidden=value;document.dispatchEvent(new Event('visibilitychange'))};
  },hidden);
}

test('large extraction yields in bounded batches and cancels pending work when disabled', async t => {
  const page = await fixture(t,'<main>'+'<p>Text</p>'.repeat(5000)+'</main>');
  await deterministic(page); await enable(page);
  const first = await page.evaluate(() => {tick();return {nodes:__happyuAgentReading.nodes,pending:!!__happyuAgentReading.cursor}});
  assert.ok(first.nodes>0 && first.nodes<=256); assert.equal(first.pending,true);
  await page.evaluate(() => {window.old=__happyuAgentReading;old.dispose()});
  assert.deepEqual(await page.evaluate(() => ({frames:framesPending.size,timers:timersPending.size,
    cursor:old.cursor,fragment:old.fragment,root:old.root,stack:old.stack.length})),
  {frames:0,timers:0,cursor:null,fragment:null,root:null,stack:0});
});

test('hidden pages release traversal state and resume with current content', async t => {
  const page = await fixture(t,'<main>'+'<p>Old</p>'.repeat(1000)+'</main>');
  await deterministic(page); await enable(page);
  await page.evaluate(() => {tick();hide(true);document.querySelector('main').innerHTML='<p>Newest</p>'});
  assert.deepEqual(await page.evaluate(() => ({frames:framesPending.size,timers:timersPending.size,
    cursor:__happyuAgentReading.cursor,root:__happyuAgentReading.root})),{frames:0,timers:0,cursor:null,root:null});
  await page.evaluate(() => {hide(false);while(__happyuAgentReading.cursor)tick()});
  assert.ok((await text(page)).includes('Newest'));
});

test('iframe callbacks create no nested readers and existing page theming still applies', async t => {
  const page = await fixture(t,'<article><p style="color:black !important">Readable</p></article><iframe src="about:blank"></iframe>');
  await page.evaluate(({contentMode,source,theme}) => {
    (0,eval)(contentMode)('* {color:#cccccc !important}',true); (0,eval)(source)(theme);
  },{contentMode,source,theme:dark});
  await ready(page);
  const frame = page.frames().find(frame=>frame!==page.mainFrame());
  await frame.evaluate(({source,theme})=>(0,eval)(source)(theme),{source,theme:dark});
  assert.equal(await frame.evaluate(()=>!!window.__happyuAgentReading),false);
  assert.equal(await page.evaluate(()=>getComputedStyle(__happyuAgentReading.content).color),'rgb(212, 212, 212)');
});

test('shows a bounded-length notice and renders a readable conversation at wide and narrow widths', async t => {
  const page = await fixture(t,'<title>在 IDE 中整理网页内容</title><main><h1>让阅读像一段 Agent 对话</h1>'
    + '<p>网页正文按对话样式呈现，保持清晰的段落、链接与代码格式。</p><h2>阅读流程</h2><ol>'
    + '<li>提取正文，去除导航和侧栏</li><li>在本地阅读，无需发送给模型</li><li>切回原网页继续交互</li></ol>'
    + '<pre><code>const mode = "agent-reading";\nconsole.log(mode);</code></pre><p><a href="next">继续阅读相关内容</a></p></main>');
  await enable(page); await ready(page);
  const output=join(__dirname,'../../../build/tmp/agent-reading-preview'); mkdirSync(output,{recursive:true});
  for(const [name,theme] of [['dark',dark],['light',light]]) {
    await enable(page,theme);
    for(const width of [538,280]) {
      await page.setViewportSize({width,height:760});
      assert.equal(await page.evaluate(()=>__happyuAgentReading.viewport.scrollWidth<=__happyuAgentReading.viewport.clientWidth),true);
      await page.screenshot({path:join(output,`${name}-${width}.png`)});
    }
  }
  await page.evaluate(()=>document.querySelector('main p').textContent='x'.repeat(130000));
  await page.locator('#happyu-agent-reading').getByRole('button',{name:'刷新正文'}).click(); await ready(page);
  assert.ok(await page.evaluate(()=>__happyuAgentReading.status.textContent.includes('内容较长')));
  assert.ok((await text(page)).length<=120000);
});
