((theme, previewImage) => {
  if (window !== window.top || !document.body) return;
  const key = '__happyuAgentReading';
  let state = window[key];
  if (state && !state.host.isConnected) {
    state.dispose();
    state = null;
  }
  if (!state) {
    const host = document.createElement('div');
    host.id = 'happyu-agent-reading';
    host.setAttribute('role', 'region');
    host.setAttribute('aria-label', 'Agent 阅读模式');
    const shadow = host.attachShadow({mode: 'open'});
    const style = document.createElement('style');
    style.textContent = `
      :host { all: initial !important; display: block !important; position: fixed !important;
        inset: 0 !important; z-index: 2147483647 !important; background: var(--reader-bg) !important;
        color: var(--reader-fg) !important; font: 13px/1.8 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important; }
      * { box-sizing: border-box; }
      /* Style every reading surface, including horizontal code/table scrollbars. */
      ::-webkit-scrollbar { width: 10px; height: 10px; background: var(--reader-bg); }
      ::-webkit-scrollbar-track, ::-webkit-scrollbar-corner { background: var(--reader-bg); }
      ::-webkit-scrollbar-thumb { background: var(--reader-muted); border: 2px solid var(--reader-bg);
        border-radius: 8px; min-height: 28px; background-clip: padding-box; }
      ::-webkit-scrollbar-thumb:hover { background-color: var(--reader-fg); }
      ::-webkit-scrollbar-button { display: none; width: 0; height: 0; }
      .viewport { height: 100%; overflow: auto; overscroll-behavior: contain; overflow-anchor: none; }
      .toolbar { position: sticky; top: 0; z-index: 1; background: var(--reader-bg);
        border-bottom: 1px solid var(--reader-border); display: flex; align-items: center; gap: 12px; padding: 12px 20px; }
      .toolbar strong { font-size: 12px; letter-spacing: .3px; }
      .toolbar span { color: var(--reader-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
      button { font: inherit; font-size: 12px; color: var(--reader-muted); background: transparent;
        border: 1px solid var(--reader-border); border-radius: 6px; padding: 2px 9px; cursor: pointer; white-space: nowrap; }
      button:hover { color: var(--reader-fg); background: var(--reader-card); }
      .thread { max-width: 860px; margin: auto; padding: 24px 28px 64px; }
      .request { margin: 0 0 26px 44px; padding: 12px 16px; border: 1px solid var(--reader-border);
        background: var(--reader-card); border-radius: 12px 12px 3px 12px; overflow-wrap: anywhere; }
      .request small { display: block; color: var(--reader-muted); font-size: 11px; margin-bottom: 4px; }
      .request strong { font-size: 14px; font-weight: 500; }
      .response { display: grid; grid-template-columns: 26px minmax(0, 1fr); gap: 12px; }
      .avatar { width: 26px; height: 26px; border: 1px solid var(--reader-border); border-radius: 8px;
        text-align: center; color: var(--reader-link); line-height: 24px; }
      .role { font-size: 11px; letter-spacing: 1px; margin-bottom: 12px; color: var(--reader-muted); }
      .content { font-size: 13px; overflow-wrap: anywhere; }
      .content h1 { font-size: 21px; } .content h2 { font-size: 18px; } .content h3 { font-size: 15px; }
      .content h1, .content h2, .content h3, .content h4 { line-height: 1.5; margin: 22px 0 12px; }
      .content > :first-child { margin-top: 0; }
      .content p { margin: 0 0 14px; } .content li { margin: 5px 0; }
      .content ul, .content ol { padding-left: 24px; }
      .content a, .source a { color: var(--reader-link); text-decoration: none; }
      .content a:hover, .source a:hover { text-decoration: underline; }
      .content pre { overflow: auto; padding: 14px; border: 1px solid var(--reader-border); border-radius: 8px; background: var(--reader-card); }
      .content code { font: 12px/1.7 Consolas, "JetBrains Mono", monospace; white-space: pre-wrap; }
      .content blockquote { margin: 14px 0; padding-left: 14px; border-left: 3px solid var(--reader-border); color: var(--reader-muted); }
      .content table { display: block; max-width: 100%; overflow: auto; border-collapse: collapse; }
      .content td, .content th { padding: 6px 10px; border: 1px solid var(--reader-border); }
      .content hr { border: 0; border-top: 1px solid var(--reader-border); margin: 20px 0; }
      .source { color: var(--reader-muted); font-size: 11px; margin-top: 24px; overflow-wrap: anywhere; }
      .status { color: var(--reader-muted); font-size: 12px; margin-bottom: 12px; }
      .post-card, .replies-card { border: 1px solid var(--reader-border); border-radius: 9px; margin: 0 0 12px; overflow: hidden; }
      summary { cursor: pointer; padding: 9px 12px; background: var(--reader-card); font-size: 12px; }
      summary::marker { color: var(--reader-muted); }
      .post-author { color: var(--reader-link); margin-left: 10px; }
      .post-body { padding: 12px; }
      .post-body > :first-child { margin-top: 0; }
      .post-meta { color: var(--reader-muted); font-size: 11px; margin: 8px 0; }
      .reader-controls { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
      .reply-author { color: var(--reader-link); font-size: 12px; margin-bottom: 6px; }
      .topic-title { display: block; font-size: 14px; margin-bottom: 8px; }
      .replies-card { margin-top: 12px; }
      .replies-body { padding: 10px; }
      .reply-bubble { padding: 9px 12px; margin: 0 0 8px; border-left: 2px solid var(--reader-link);
        background: var(--reader-card); border-radius: 3px 9px 9px 3px; }
      .reply-bubble p:last-child { margin-bottom: 0; }
      .image-card { display: flex; align-items: center; gap: 9px; width: 100%; min-width: 0;
        margin: 10px 0; padding: 8px 12px; text-align: left; background: var(--reader-card); border-radius: 8px; }
      .image-card-icon, .image-card-name { color: var(--reader-link); }
      .image-card-label { color: var(--reader-fg); }
      .image-card-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
      .image-card-dot { width: 7px; height: 7px; border-radius: 50%; background: #55b45d; flex: none; }
      .image-preview { color: var(--reader-fg); background: var(--reader-bg); border: 1px solid var(--reader-border);
        border-radius: 10px; padding: 0; width: min(1100px, calc(100vw - 24px)); max-height: calc(100vh - 24px); }
      .image-preview::backdrop { background: #0009; }
      .image-preview-header { display: flex; gap: 12px; align-items: center; padding: 10px 14px;
        border-bottom: 1px solid var(--reader-border); }
      .image-preview-header strong { flex: 1; min-width: 0; overflow-wrap: anywhere; font-size: 12px; }
      .image-preview-controls { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; padding: 8px 14px; }
      .image-preview-controls output { min-width: 48px; text-align: center; font-size: 12px; font-variant-numeric: tabular-nums; }
      .image-preview-controls small { color: var(--reader-muted); font-size: 11px; }
      .image-preview-controls button:disabled { cursor: default; }
      .image-preview-stage { padding: 16px; overflow: auto; overscroll-behavior: contain; touch-action: none;
        text-align: center; background: var(--reader-card); }
      .image-preview-stage.pannable { cursor: grab; }
      .image-preview-stage.dragging { cursor: grabbing; }
      .image-preview-canvas { position: relative; min-width: 100%; min-height: 100%; }
      .image-preview img { display: block; visibility: visible; max-width: none; max-height: none;
        position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); user-select: none; }
      .image-preview-status { color: var(--reader-muted); padding: 12px; font-size: 12px; }
      .image-preview [hidden] { display: none; }
      .load-more { display: block; margin: 18px auto 0; }
      .load-status { color: var(--reader-muted); font-size: 12px; text-align: center; margin-top: 8px; }
      button:disabled { opacity: .65; cursor: wait; }
      @media (max-width: 480px) { .thread { padding: 18px 12px 40px; }
        .request { margin-left: 28px; } .response { grid-template-columns: 20px minmax(0, 1fr); gap: 8px; }
        .avatar { width: 20px; height: 20px; line-height: 18px; } .toolbar { padding: 10px 12px; } }
    `;
    shadow.append(style);
    const make = (tag, className, text, parent) => {
      const element = document.createElement(tag);
      if (className) element.className = className;
      if (text) element.textContent = text;
      if (parent) parent.append(element);
      return element;
    };
    const viewport = make('div', 'viewport', '', shadow);
    const toolbar = make('div', 'toolbar', '', viewport);
    make('strong', '', 'Agent · 阅读', toolbar);
    const domain = make('span', '', '', toolbar);
    const refresh = make('button', '', '刷新正文', toolbar);
    refresh.type = 'button';
    const thread = make('main', 'thread', '', viewport);
    const request = make('div', 'request', '', thread);
    make('small', '', 'YOU · 阅读以下内容', request);
    const title = make('strong', '', '', request);
    const response = make('section', 'response', '', thread);
    make('div', 'avatar', '✦', response);
    const message = make('div', '', '', response);
    make('div', 'role', 'AGENT', message);
    const status = make('div', 'status', '', message);
    status.setAttribute('role', 'status');
    const content = make('article', 'content', '', message);
    content.tabIndex = -1;
    const loadMore = make('button', 'load-more', '尝试加载更多', message);
    loadMore.type = 'button';
    loadMore.disabled = true;
    loadMore.title = '滚动原网页以触发后续内容加载；网页提供加载按钮时可在正文中操作';
    const loadStatus = make('div', 'load-status', '', message);
    loadStatus.setAttribute('role', 'status');
    const source = make('div', 'source', '', message);
    const pageStyle = document.createElement('style');
    pageStyle.textContent = `
      html[data-happyu-agent-reading] { overflow: hidden !important; scrollbar-width: none !important; }
      html[data-happyu-agent-reading]::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; }
    `;
    state = {host, viewport, status, content, title, domain, source, pageStyle, disposed: false,
      opened: new Map(), actions: new Map(), cards: new Map(), entries: new Map(), scrollPositions: new Map(), lastRefresh: 0,
      anchorTargets: new Map(), inertRoots: new Map(), originalFocus: document.activeElement,
      originalScroll: {x: window.scrollX, y: window.scrollY}};
    while (state.originalFocus?.shadowRoot?.activeElement) state.originalFocus = state.originalFocus.shadowRoot.activeElement;
    const restoreInert = (element, value) => {
      if (value === null) element.removeAttribute('inert');
      else element.setAttribute('inert', value);
    };
    state.isolatePage = () => {
      // The reader is a sibling of body. Cover replacement bodies and portals outside body
      // without walking the site's DOM or retaining roots that have been detached.
      if (state.originalFocus && !state.originalFocus.isConnected) state.originalFocus = null;
      for (const [element, value] of state.inertRoots) {
        if (element.parentElement !== document.documentElement) {
          restoreInert(element, value);
          state.inertRoots.delete(element);
        }
      }
      for (const element of document.documentElement.children) {
        if (element === host || element === pageStyle || element.matches('head, script, style, link, meta')) continue;
        if (!state.inertRoots.has(element)) state.inertRoots.set(element, element.getAttribute('inert'));
        if (!element.inert) element.inert = true;
      }
    };
    state.guardFocus = event => {
      if (!state.disposed && !event.composedPath().includes(host)) refresh.focus({preventScroll: true});
    };
    const markTarget = (node, output) => {
      const names = [node.id, node.tagName === 'A' ? node.getAttribute('name') : ''].filter(name => name && name.length <= 1024);
      if (!names.length || output === state.parent || output.nodeType !== Node.ELEMENT_NODE) return;
      output.dataset.readerTarget = JSON.stringify(names);
      if (!output.matches('a[href], button, summary, [tabindex]')) output.tabIndex = -1;
      state.characters += output.dataset.readerTarget.length;
    };
    const jumpToAnchor = link => {
      try {
        const url = new URL(link.href), current = new URL(location.href);
        if (!url.hash && !url.href.endsWith('#')) return false;
        let name = url.hash.slice(1);
        try { name = decodeURIComponent(name); } catch (_) { /* Match literal malformed escapes. */ }
        url.hash = current.hash = '';
        if (url.href !== current.href) return false;
        const target = state.anchorTargets.get(name);
        if (!target && name && name.toLowerCase() !== 'top') {
          status.textContent = '该位置未包含在阅读内容中，可切回原网页查看。';
          return true;
        }
        for (let parent = target?.parentElement; parent && parent !== content; parent = parent.parentElement) {
          if (parent.tagName === 'DETAILS') parent.open = true;
        }
        clearTimeout(state.sourceScrollTimer);
        state.sourceScrollTimer = null;
        viewport.scrollTo({top: target ? viewport.scrollTop + target.getBoundingClientRect().top
          - viewport.getBoundingClientRect().top - toolbar.offsetHeight - 12 : 0, behavior: 'instant'});
        // A table-of-contents jump is not a request to advance the original virtual list.
        state.lastScroll = viewport.scrollTop;
        (target || refresh).focus({preventScroll: true});
        return true;
      } catch (_) { return false; }
    };
    const nodeKeys = new WeakMap();
    let nextKey = 0;
    const imageUrl = value => {
      if (!value || value.length > 8192) return '';
      try {
        const url = new URL(value, document.baseURI);
        if (!['http:', 'https:'].includes(url.protocol)) return '';
        url.hash = '';
        return url.href;
      } catch (_) { return ''; }
    };
    const imageSource = node => {
      for (const value of [node.getAttribute('data-src'), node.getAttribute('data-original'),
        node.getAttribute('data-original-src'), node.getAttribute('data-lazy-src'), node.getAttribute('data-actualsrc'),
        node.currentSrc, node.getAttribute('src')]) {
        const url = imageUrl(value);
        if (url) return url;
      }
      const srcset = node.getAttribute('data-srcset') || node.getAttribute('srcset')
        || node.closest('picture')?.querySelector('source[srcset]')?.getAttribute('srcset') || '';
      return srcset.length <= 8192 ? imageUrl(srcset.split(',')[0].trim().split(/\s+/)[0]) : '';
    };
    const imageName = url => {
      let name = new URL(url).pathname.split('/').pop() || '图片';
      try { name = decodeURIComponent(name); } catch (_) { /* Keep malformed escapes readable. */ }
      return name.slice(0, 100);
    };
    const imageCard = (node, output) => {
      const url = imageSource(node);
      if (!url) return;
      // Keep Tieba's inline expressions compact; avatars/badges are excluded with their author wrappers.
      if (node.matches('.emoji-img, .BDE_Smiley') || node.closest('.pb-emot')) {
        make('span', '', node.alt || '[表情]', output);
        return;
      }
      const name = imageName(url);
      const button = make('button', 'image-card', '', output);
      button.type = 'button';
      button.dataset.readerImage = url;
      button.setAttribute('aria-label', '预览图片 ' + name);
      button.title = node.alt || name;
      make('span', 'image-card-icon', '▧', button).setAttribute('aria-hidden', 'true');
      make('span', 'image-card-label', 'imageView', button);
      make('span', 'image-card-name', name, button);
      make('span', 'image-card-dot', '', button).setAttribute('aria-hidden', 'true');
      state.characters += url.length + name.length;
      return button;
    };
    // Serialize permission changes so a slow acknowledgement cannot re-enable a closed preview.
    state.previewPermission = Promise.resolve();
    const authorizeImage = url => state.previewPermission = state.previewPermission.catch(() => {})
      .then(() => state.previewBridge?.(url));
    state.closeImagePreview = () => {
      const preview = state.preview;
      if (!preview) return;
      state.preview = null;
      clearTimeout(preview.timer);
      window.removeEventListener('resize', preview.resize);
      preview.image.onload = preview.image.onerror = null;
      preview.image.removeAttribute('src');
      preview.dialog.close();
      preview.dialog.remove();
      authorizeImage('').catch(() => {});
      if (preview.trigger.isConnected) preview.trigger.focus({preventScroll: true});
    };
    state.openImagePreview = trigger => {
      const url = imageUrl(trigger.dataset.readerImage);
      if (!url) return;
      state.closeImagePreview();
      const dialog = make('dialog', 'image-preview', '', shadow);
      dialog.setAttribute('aria-label', '图片预览 ' + imageName(url));
      const header = make('div', 'image-preview-header', '', dialog);
      make('strong', '', 'imageView · ' + imageName(url), header);
      const close = make('button', '', '关闭 · Esc', header);
      close.type = 'button';
      const controls = make('div', 'image-preview-controls', '', dialog);
      const zoomOut = make('button', '', '−', controls);
      zoomOut.setAttribute('aria-label', '缩小图片');
      const percent = make('output', '', '—', controls);
      percent.setAttribute('aria-label', '图片缩放比例');
      const zoomIn = make('button', '', '+', controls);
      zoomIn.setAttribute('aria-label', '放大图片');
      const fit = make('button', '', '适应窗口', controls);
      const actual = make('button', '', '原始大小', controls);
      const zoomButtons = [zoomOut, zoomIn, fit, actual];
      for (const button of zoomButtons) { button.type = 'button'; button.disabled = true; }
      make('small', '', '滚轮缩放 · 放大后拖动查看', controls);
      const stage = make('div', 'image-preview-stage', '', dialog);
      stage.tabIndex = 0;
      stage.setAttribute('aria-label', '图片查看区域，可滚轮缩放或拖动查看');
      const canvas = make('div', 'image-preview-canvas', '', stage);
      canvas.hidden = true;
      const image = make('img', '', '', canvas);
      image.alt = trigger.title || imageName(url);
      image.draggable = false;
      image.hidden = true;
      const feedback = make('div', 'image-preview-status', '正在加载图片…', stage);
      feedback.setAttribute('role', 'status');
      const retry = make('button', '', '重试', stage);
      retry.type = 'button';
      retry.hidden = true;
      const preview = {dialog, image, trigger, zoom: 1, fitZoom: 1, fitted: true, loaded: false};
      state.preview = preview;
      const updateZoom = (value, point, fitted = false) => {
        if (state.preview !== preview || !preview.loaded) return;
        const stageRect = stage.getBoundingClientRect();
        point ||= {x: stageRect.left + stage.clientLeft + stage.clientWidth / 2,
          y: stageRect.top + stage.clientTop + stage.clientHeight / 2};
        const before = image.getBoundingClientRect();
        const anchorX = before.width ? Math.max(0, Math.min(1, (point.x - before.left) / before.width)) : 0.5;
        const anchorY = before.height ? Math.max(0, Math.min(1, (point.y - before.top) / before.height)) : 0.5;
        preview.zoom = Math.max(Math.min(0.1, preview.fitZoom), Math.min(8, value));
        preview.fitted = fitted;
        const width = image.naturalWidth * preview.zoom, height = image.naturalHeight * preview.zoom;
        canvas.style.width = Math.ceil(width) + 'px';
        canvas.style.height = Math.ceil(height) + 'px';
        image.style.width = width + 'px';
        image.style.height = height + 'px';
        if (fitted) stage.scrollTo(0, 0);
        else {
          const after = image.getBoundingClientRect();
          stage.scrollLeft += after.left + anchorX * after.width - point.x;
          stage.scrollTop += after.top + anchorY * after.height - point.y;
        }
        percent.value = Math.round(preview.zoom * 100) + '%';
        zoomOut.disabled = preview.zoom <= Math.min(0.1, preview.fitZoom);
        zoomIn.disabled = preview.zoom >= 8;
        fit.disabled = actual.disabled = false;
        stage.classList.toggle('pannable', stage.scrollWidth > stage.clientWidth || stage.scrollHeight > stage.clientHeight);
      };
      preview.resize = () => {
        if (state.preview !== preview || !preview.loaded) return;
        const availableHeight = Math.max(1, window.innerHeight - header.offsetHeight - controls.offsetHeight - 58);
        preview.fitZoom = Math.min(1, Math.max(1, stage.clientWidth - 32) / image.naturalWidth,
          availableHeight / image.naturalHeight);
        // Keep the viewing area stable while zooming; only window resizing recalculates its height.
        stage.style.height = Math.ceil(image.naturalHeight * preview.fitZoom) + 32 + 'px';
        updateZoom(preview.fitted ? preview.fitZoom : preview.zoom, null, preview.fitted);
      };
      zoomOut.onclick = () => updateZoom(preview.zoom / 1.25);
      zoomIn.onclick = () => updateZoom(preview.zoom * 1.25);
      fit.onclick = () => updateZoom(preview.fitZoom, null, true);
      actual.onclick = () => updateZoom(1);
      stage.addEventListener('wheel', event => {
        event.preventDefault();
        event.stopPropagation();
        if (!preview.loaded || !event.deltaY) return;
        const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? stage.clientHeight : 1);
        updateZoom(preview.zoom * Math.exp(-Math.max(-500, Math.min(500, delta)) * 0.002),
          {x: event.clientX, y: event.clientY});
      }, {passive: false});
      dialog.addEventListener('keydown', event => {
        if (event.ctrlKey || event.metaKey || event.altKey || !preview.loaded) return;
        const action = {'+': zoomIn, '=': zoomIn, '-': zoomOut, '0': fit, '1': actual}[event.key];
        if (!action) return;
        event.preventDefault();
        event.stopPropagation();
        action.click();
      });
      stage.addEventListener('pointerdown', event => {
        if (event.button !== 0 || !preview.loaded || !stage.classList.contains('pannable')) return;
        event.preventDefault();
        stage.focus({preventScroll: true});
        preview.drag = {id: event.pointerId, x: event.clientX, y: event.clientY,
          left: stage.scrollLeft, top: stage.scrollTop};
        stage.setPointerCapture(event.pointerId);
        stage.classList.add('dragging');
      });
      stage.addEventListener('pointermove', event => {
        const drag = preview.drag;
        if (!drag || drag.id !== event.pointerId) return;
        stage.scrollLeft = drag.left + drag.x - event.clientX;
        stage.scrollTop = drag.top + drag.y - event.clientY;
      });
      const endDrag = event => {
        if (preview.drag?.id !== event.pointerId) return;
        preview.drag = null;
        stage.classList.remove('dragging');
        if (stage.hasPointerCapture(event.pointerId)) stage.releasePointerCapture(event.pointerId);
      };
      for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) stage.addEventListener(type, endDrag);
      window.addEventListener('resize', preview.resize);
      const failed = () => {
        if (state.preview !== preview) return;
        clearTimeout(preview.timer);
        preview.loaded = false;
        canvas.hidden = true;
        for (const button of zoomButtons) button.disabled = true;
        image.hidden = true;
        feedback.hidden = false;
        feedback.textContent = '图片加载失败，可重试或切回原网页查看。';
        retry.hidden = false;
      };
      const load = () => {
        clearTimeout(preview.timer);
        preview.loaded = false;
        preview.fitted = true;
        canvas.hidden = true;
        for (const button of zoomButtons) button.disabled = true;
        image.hidden = true;
        image.removeAttribute('src');
        feedback.hidden = false;
        feedback.textContent = '正在加载图片…';
        retry.hidden = true;
        preview.timer = setTimeout(failed, 20000);
        authorizeImage(url).then(() => {
          if (state.preview === preview && !state.disposed) image.src = url;
        }, failed);
      };
      image.onload = () => {
        if (state.preview !== preview) return;
        clearTimeout(preview.timer);
        preview.loaded = true;
        canvas.hidden = false;
        image.hidden = false;
        feedback.hidden = true;
        preview.resize();
      };
      image.onerror = failed;
      close.onclick = state.closeImagePreview;
      retry.onclick = load;
      dialog.addEventListener('cancel', event => { event.preventDefault(); state.closeImagePreview(); });
      dialog.addEventListener('close', () => { if (state.preview === preview) state.closeImagePreview(); });
      dialog.addEventListener('click', event => { if (event.target === dialog) state.closeImagePreview(); });
      dialog.showModal();
      load();
    };
    const nodeKey = node => {
      // Vue's virtual list destroys/recreates nodes. Use content identity for cards, never for actions.
      if (state.tiebaRoot && node.nodeType === Node.ELEMENT_NODE) {
        if (node.matches('.l_post[data-pid]')) return 'post:' + node.getAttribute('data-pid');
        if (node.matches('.j_thread_list[data-tid]')) return 'topic:/p/' + node.getAttribute('data-tid');
        if (node.matches('.pb-comment-item[data-id]')) return 'post:' + node.getAttribute('data-id');
        if (node.matches(tiebaOp)) return 'post:op';
        if (node.matches('.thread-card-wrapper, .top-thread-card-item')) {
          const href = node.matches('a') ? node.getAttribute('href')
            : node.querySelector('a.thread-content-link[href]')?.getAttribute('href');
          if (href) {
            try { return 'topic:' + new URL(href, document.baseURI).pathname; }
            catch (_) { /* A malformed link still gets a local card identity. */ }
          }
        }
        if (node.matches('.lzl-wrapper')) {
          const owner = node.closest('.pb-comment-item');
          if (owner) return nodeKey(owner) + ':replies';
        }
      }
      if (!nodeKeys.has(node)) nodeKeys.set(node, String(++nextKey));
      return nodeKeys.get(node);
    };
    state.cancel = () => {
      cancelAnimationFrame(state.frame);
      clearTimeout(state.timer);
      state.scheduled = false;
    };
    state.dispose = () => {
      state.disposed = true;
      state.closeImagePreview();
      state.cancel();
      clearTimeout(state.updateTimer);
      state.updateTimer = null;
      clearTimeout(state.sourceScrollTimer);
      state.sourceScrollTimer = null;
      clearTimeout(state.loadTimer);
      state.loadTimer = null;
      state.loading = false;
      state.loadStarted = 0;
      state.observer.disconnect();
      state.cursor = state.fragment = state.root = state.parent = null;
      state.liveRoot = state.scrollTarget = null;
      state.buildScrollTarget = state.firstPost = null;
      state.pageAction = state.buildPageAction = state.inferredPosts = state.floorCounts = state.selectedRoot = null;
      state.activeReplyOwner = state.replyPane = state.buildReplyPane = state.buildPostBodies = null;
      state.replyCounts = state.inferredReplies = null;
      state.replyMarkers = [];
      state.floorMarkers = state.pageCandidates = [];
      state.stack = [];
      state.actions.clear();
      state.cards.clear();
      state.entries.clear();
      state.anchorTargets.clear();
      state.tiebaRoot = null;
      state.tiebaClassic = false;
      state.buildActions = state.buildCards = null;
      state.opened.clear();
      host.remove();
      pageStyle.remove();
      document.documentElement.removeAttribute('data-happyu-agent-reading');
      document.removeEventListener('visibilitychange', state.visibility);
      document.removeEventListener('focusin', state.guardFocus, true);
      for (const [element, value] of state.inertRoots) restoreInert(element, value);
      state.inertRoots.clear();
      if (state.originalFocus?.isConnected) state.originalFocus.focus?.({preventScroll: true});
      state.originalFocus = null;
      for (const [element, top] of state.scrollPositions) if (element.isConnected) element.scrollTop = top;
      state.scrollPositions.clear();
      window.scrollTo(state.originalScroll.x, state.originalScroll.y);
      if (window[key] === state) delete window[key];
    };
    const skip = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'NAV', 'HEADER', 'FOOTER', 'ASIDE',
      'FORM', 'INPUT', 'BUTTON', 'TEXTAREA', 'SELECT', 'IFRAME', 'SVG', 'CANVAS', 'VIDEO', 'AUDIO', 'IMG', 'PICTURE']);
    const allowed = new Set(['P', 'DIV', 'SECTION', 'ARTICLE', 'MAIN', 'SPAN', 'A', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
      'UL', 'OL', 'LI', 'DL', 'DT', 'DD', 'BLOCKQUOTE', 'PRE', 'CODE', 'STRONG', 'B', 'EM', 'I', 'S', 'U', 'SMALL',
      'SUB', 'SUP', 'MARK', 'BR', 'HR', 'TABLE', 'THEAD', 'TBODY', 'TFOOT', 'TR', 'TD', 'TH', 'CAPTION']);
    // Structural hints, not a dependency on a site's API. Unknown pages keep the normal article layout.
    const tiebaOp = '.pb-content-wrap, .image-text > #artplayer-app';
    const posts = '.l_post, .plhin, [data-post-id], article.comment, .comment-item, .post-item, .postItem, .thread-post, .pb-comment-item, '
      + tiebaOp + ', .thread-card-wrapper, .top-thread-card-item, .j_thread_list[data-tid]';
    const replyGroups = '.j_lzl_container, .lzl_container, .reply-list, .replies, [data-replies], .sub-reply-list, .sub-comment-list, .lzl-wrapper';
    const replies = '.lzl_single_post, .reply-item, [data-comment-id], .pb-lzl-item';
    // Classic Tieba's data-author="0" belongs to the entire post body, not an author label.
    const authors = '.p_author_name, .lzl_p_p, .authi .xw1, [data-author]:not(.d_post_content_main), .comment-author, .reply-author, .head-name, .frs-author-name';
    const controls = 'a, button, [role="button"], .j_lzl_m_w, .j_lzl_link_unfold, .lzl_link_unfold, .show-more-lzl';
    const floorNumber = value => {
      const text = String(value ?? '').trim();
      return /^\d{1,9}$/.test(text) && Number(text) > 0 ? String(Number(text)) : '';
    };
    const readFloor = (node, metadata) => {
      let floor = floorNumber(node.getAttribute('data-floor')) || floorNumber(metadata?.content?.post_no);
      if (!floor && node.matches('.pb-comment-item')) {
        // Read this post's own metadata, even when its label has no layout box. Body quotations
        // and nested replies can also mention "第 N 楼" and must never supply the post number.
        const label = node.querySelector(':scope > .comment-content > .pc-pb-comments-desc > .comment-desc-left, '
          + ':scope > .comment-content > .comment-desc-left, :scope > .pc-pb-comments-desc > .comment-desc-left, '
          + ':scope > .comment-desc-left');
        floor = floorNumber(label?.textContent.match(/^\s*第\s*(\d{1,9})\s*楼/)?.[1]);
      } else if (!floor && node.matches('.l_post')) {
        for (const label of node.querySelectorAll('.post-tail-wrap > .tail-info')) {
          floor = floorNumber(label.textContent.match(/^\s*(\d{1,9})\s*楼\s*$/)?.[1]);
          if (floor) break;
        }
      } else if (!floor) floor = floorNumber(state.inferredPosts?.get(node)?.floor);
      if (!floor && state.renderedContext === state.context) {
        floor = floorNumber(state.cards.get(nodeKey(node))?.dataset.readerFloor);
      }
      return floor;
    };
    const inferredPost = node => {
      for (let current = node, depth = 0; current && depth < 32; current = current.parentElement, depth++) {
        if (state.inferredPosts?.has(current)) return current;
      }
      return null;
    };
    const countedReply = node => {
      if (!node.matches('a, button, span, div, [role="button"]') || node.childElementCount > 1
          || node.firstElementChild?.childElementCount > 0) return null;
      const label = node.textContent.trim();
      if (label.length > 30) return null;
      const match = label.match(/^(?:(\d+)\s*(?:条)?回复|回复\s*[（(]\s*(\d+)\s*[）)])$/);
      return match && Number(match[1] || match[2]) > 0 ? Number(match[1] || match[2]) : null;
    };
    const replyNumber = value => /^\d{1,9}$/.test(String(value ?? '').trim()) ? Number(value) : null;
    const replyLabelCount = value => {
      const label = String(value || '').trim();
      if (label.length > 80) return null;
      const remaining = label.match(/^(?:还有|剩余)\s*(\d{1,9})\s*条(?:回复|评论)/);
      if (remaining) return {remaining: Number(remaining[1])};
      const total = label.match(/^(?:(?:展开|查看|显示|全部|共)\s*)?(\d{1,9})\s*(?:条)?(?:楼中楼)?(?:回复|评论)\s*$/)
        || label.match(/^(?:回复|评论)\s*[（(]\s*(\d{1,9})\s*[）)]\s*$/);
      return total ? {total: Number(total[1])} : null;
    };
    const replyTotals = new WeakMap();
    const prepareReplyTotal = (node, details, action, metadata) => {
      let declared = replyNumber(metadata?.total_num);
      for (const attribute of ['data-total-count', 'data-total', 'data-reply-count']) {
        if (declared === null) declared = replyNumber(node.getAttribute(attribute));
      }
      const actionGroup = action?.closest(replyGroups);
      const hint = action && !action.closest(replies) && (!actionGroup || actionGroup === node)
        ? replyLabelCount(action.textContent) : null;
      const previous = state.renderedContext === state.context
        ? replyNumber(state.cards.get(nodeKey(node))?.dataset.replyTotal) : null;
      replyTotals.set(details, {declared, hint, previous});
    };
    const finishReplyCounts = fragment => {
      for (const details of fragment.querySelectorAll('.replies-card')) {
        const loaded = Number(details.dataset.replyCount || 0);
        const info = replyTotals.get(details);
        let total = info?.declared ?? null;
        if (total === null) {
          let hint = info?.hint;
          for (const button of details.querySelectorAll('button[data-reader-action]')) {
            if (button.closest('.replies-card') !== details || button.closest('.reply-bubble')) continue;
            const count = replyLabelCount(button.textContent);
            if (count) { hint = count; break; }
          }
          total = hint ? hint.total ?? loaded + hint.remaining : info?.previous ?? null;
        }
        if (total !== null) details.dataset.replyTotal = Math.max(total, loaded);
        details.querySelector('summary').textContent = total === null
          ? '楼中楼回复 · 已加载 ' + loaded + ' 条'
          : Math.max(total, loaded) + ' 条回复 · 已加载 ' + loaded + ' 条';
      }
    };
    const isReplyClose = node => state.replyPane?.contains(node) && node.matches(controls)
      && /^(?:关闭|关闭回复|close|×|✕)$/i.test((node.getAttribute('aria-label') || node.getAttribute('title') || node.textContent).trim());
    const isReadAction = node => {
      if (node.closest('form') || node.form) return false;
      if (isReplyClose(node) || (state.replyPane?.contains(node) && isPageAction(node))) return true;
      if (countedReply(node) && (state.forumSite || node.closest(posts) || inferredPost(node))) return true;
      if (state.forumSite && node.matches('a, button, span, div, [role="button"]') && node.childElementCount <= 2
          && /^(?:只看楼主|取消只看楼主|全部回复\s*(?:[（(]\d+[）)])?|正序|倒序|按时间排序|按热度排序)$/.test(node.textContent.trim())) return true;
      if (!node.matches(controls)) return false;
      const raw = (node.textContent || '').trim();
      if (node.matches('.j_lzl_m') && raw === '点击查看') return true;
      if (node.matches('.j_lzl_link_unfold, .lzl_link_unfold') && /^回复\s*[（(]\s*\d+\s*[）)]$/.test(raw)) return true;
      if (node.matches('.lzl_pager a') && /^(?:\d{1,5}|上一页|下一页|尾页|首页)$/.test(raw)) return true;
      const label = raw.replace(/[（(]\s*\d+\s*[）)]\s*$/, '').trim();
      return label.length < 60 && /^(?:(?:展开|查看|显示|加载|更多).*(?:回复|评论)|还有\s*\d+\s*条回复.*(?:查看|展开)|(?:加载|显示|查看更多|展开更多)(?:更多)?(?:内容|评论|回复)?[.…\s]*)$/.test(label)
        && !/(?:发表|发送|提交|删除|点赞|举报)/.test(label);
    };
    const registerAction = node => {
      const id = nodeKey(node);
      state.buildActions.set(id, node);
      return id;
    };
    const isPageAction = node => {
      const control = node.matches('a, button, [role="button"], [class*="load-more"], [class*="loadMore"]');
      if ((!control && (!node.matches('span, div') || node.childElementCount > 1 || node.firstElementChild?.childElementCount > 0))
          || node.closest('form') || node.form || node.disabled || node.getAttribute('aria-disabled') === 'true') return null;
      const label = (node.getAttribute('aria-label') || node.getAttribute('title') || node.textContent).trim();
      if (label.length > 60) return null;
      let kind;
      if (/^(?:下一页|下页|下一頁|Next(?: page)?|[›»])\s*[>›»→]?$/i.test(label)
          || node.getAttribute('rel')?.split(/\s+/).includes('next')) kind = 'next';
      else if (/^(?:(?:点击|继续)?\s*(?:加载|显示|查看)更多(?:回复|评论|内容)?|更多(?:回复|评论|内容))[.…\s]*$/.test(label)) kind = 'more';
      return kind && (control || getComputedStyle(node).cursor === 'pointer') ? kind : null;
    };
    const findReplyAction = node => {
      const scope = inferredPost(node) || node.closest(posts) || node.parentElement;
      if (!scope) return null;
      // Stop at the first read-only action; never forward a bare "回复" / submit control.
      const walker = document.createTreeWalker(scope, NodeFilter.SHOW_ELEMENT);
      for (let i = 0, current = scope; current && i < 512; i++, current = walker.nextNode()) {
        if (isReadAction(current) && /回复|评论/.test(current.textContent)) return current;
      }
      return null;
    };
    state.invoke = id => {
      const node = state.actions.get(id);
      if (!node?.isConnected || !isReadAction(node)) return;
      if (isReplyClose(node)) state.activeReplyOwner = null;
      else if (node.matches('.j_lzl_m') || /回复|评论/.test(node.textContent)) {
        state.activeReplyOwner = inferredPost(node) || node.closest(posts);
        state.beginLoad('reply');
      }
      if (/只看楼主|正序|倒序|排序/.test(node.textContent)) {
        state.resetScrollAfterLoad = true;
        state.clearEntries = true;
      }
      node.click();
      state.requestRefresh();
    };
    state.finishLoad = text => {
      clearTimeout(state.loadTimer);
      state.loadTimer = null;
      state.loading = false;
      loadMore.disabled = false;
      loadMore.removeAttribute('aria-busy');
      loadMore.textContent = state.pageAction?.kind === 'next' ? '下一页' : '尝试加载更多';
      loadStatus.textContent = text;
    };
    state.beginLoad = (kind = 'page') => {
      if (state.loading) return false;
      state.loading = true;
      state.loadStarted = Date.now();
      loadMore.disabled = true;
      loadMore.setAttribute('aria-busy', 'true');
      loadMore.textContent = '正在加载…';
      loadStatus.textContent = kind === 'reply' ? '正在展开原网页回复…' : '正在请求原网页加载后续内容…';
      state.loadTimer = setTimeout(() => state.finishLoad(kind === 'reply'
        ? '暂未展开新回复；可能需要切回原网页登录或继续操作。'
        : '未加载到新内容；可能已到底，或需要切回原网页继续操作。'), 2500);
      return true;
    };
    const card = (node, kind, label, parent, open) => {
      const details = make('details', kind, '', parent);
      const id = nodeKey(node);
      details.dataset.readerKey = id;
      details.open = state.opened.has(id) ? state.opened.get(id) : open;
      const summary = make('summary', '', label, details);
      state.buildCards.set(id, details);
      return {details, summary};
    };
    state.advance = (node, descend, output) => {
      const next = node === state.root ? null : node.nextSibling;
      if (descend && node.firstChild) {
        state.stack.push({next, parent: state.parent});
        state.cursor = node.firstChild;
        state.parent = output;
      } else state.cursor = next;
      while (!state.cursor && state.stack.length) {
        const entry = state.stack.pop();
        state.cursor = entry.next;
        state.parent = entry.parent;
      }
    };
    state.analyze = () => {
      const node = state.cursor;
      state.nodes++;
      let descend = node.nodeType === Node.ELEMENT_NODE && node !== host && node !== pageStyle
        && !(state.tiebaRoot && node.matches('.image-text > #artplayer-app'))
        && !['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'FORM', 'IFRAME', 'SVG'].includes(node.tagName);
      if (descend) {
        if (node.hidden || node.getAttribute('aria-hidden') === 'true') descend = false;
        const kind = isPageAction(node);
        if (kind && !node.closest(posts) && state.pageCandidates.length < 256 && node.getClientRects().length
            && getComputedStyle(node).visibility !== 'hidden') state.pageCandidates.push({node, kind});
        if (state.activeReplyOwner && !state.buildReplyPane
            && node.matches('[role="dialog"], [aria-modal="true"], .reply-dialog, .reply-modal, .van-popup, .el-dialog, .ant-modal')
            && node.getClientRects().length && getComputedStyle(node).visibility !== 'hidden') state.buildReplyPane = node;
        const scope = node.closest(replyGroups) || (state.buildReplyPane?.contains(node) ? state.buildReplyPane : null);
        if (scope && !scope.matches('.lzl-wrapper') && !(state.tiebaClassic && node.closest('.lzl_single_post'))
            && node !== scope && state.replyMarkers.length < 1000
            && node.matches(authors + ', a[href*="/home/"], a[href*="un="]') && node.getClientRects().length) {
          state.replyMarkers.push({node, scope});
          for (let ancestor = node, depth = 0; ancestor && depth < 32; ancestor = ancestor.parentElement, depth++) {
            state.replyCounts.set(ancestor, (state.replyCounts.get(ancestor) || 0) + 1);
            if (ancestor === scope) break;
          }
        }
      } else if (node.nodeType === Node.TEXT_NODE && node.nodeValue.length < 100 && state.floorMarkers.length < 1000) {
        let marker = node.parentElement;
        let match = node.nodeValue.match(/^\s*第\s*(\d{1,6})\s*楼/);
        if (!match && node.nodeValue.trim() === '第') {
          for (let depth = 0; marker && depth < 3; depth++, marker = marker.parentElement) {
            const text = marker.textContent;
            if (text.length >= 100) break;
            match = text.match(/^\s*第\s*(\d{1,6})\s*楼/);
            if (match) break;
          }
        }
        if (match && marker?.getClientRects().length) {
          state.floorMarkers.push({node: marker, floor: match[1]});
          for (let ancestor = marker, depth = 0; ancestor && depth < 32; ancestor = ancestor.parentElement, depth++) {
            state.floorCounts.set(ancestor, (state.floorCounts.get(ancestor) || 0) + 1);
          }
        }
      }
      state.advance(node, descend, null);
    };
    state.startCopy = () => {
      state.inferredPosts = new WeakMap();
      state.inferredReplies = new WeakSet();
      let root = state.selectedRoot;
      for (const marker of state.floorMarkers) {
        let candidate = marker.node.closest(posts);
        if (!candidate && state.floorMarkers.length > 1) {
          candidate = marker.node;
          while (candidate.parentElement && candidate.parentElement !== document.body
              && state.floorCounts.get(candidate.parentElement) === 1) candidate = candidate.parentElement;
        }
        if (!candidate) {
          for (let ancestor = marker.node, depth = 0; ancestor && ancestor !== document.body && depth < 12;
              ancestor = ancestor.parentElement, depth++) {
            if (ancestor.querySelector('a[href*="/home/"], a[href*="un="]')) { candidate = ancestor; break; }
          }
        }
        if (!candidate || candidate === document.body || candidate === document.documentElement) continue;
        if (state.inferredPosts.has(candidate)) continue;
        const author = candidate.querySelector(authors)
          || candidate.querySelector('a[href*="/home/"], a[href*="un="], [class*="username"], [class*="user-name"]')
          || candidate.querySelector('a');
        if (!author && !state.forumSite && !candidate.matches(posts)) continue;
        state.inferredPosts.set(candidate, {floor: marker.floor, author});
        if (!state.firstPost) state.firstPost = candidate;
        while (root.parentElement && !root.contains(candidate)) root = root.parentElement;
      }
      for (const marker of state.replyMarkers) {
        let candidate = marker.node;
        while (candidate.parentElement && candidate.parentElement !== marker.scope
            && state.replyCounts.get(candidate.parentElement) === 1) candidate = candidate.parentElement;
        if (candidate !== marker.node) state.inferredReplies.add(candidate);
      }
      state.buildPageAction = state.pageCandidates.find(action =>
        !action.node.closest(posts) && !inferredPost(action.node) && !state.buildReplyPane?.contains(action.node)
        && (!state.tiebaRoot || state.tiebaClassic || state.tiebaRoot.contains(action.node))) || null;
      state.replyPane = state.buildReplyPane;
      state.floorMarkers = state.pageCandidates = [];
      state.replyMarkers = [];
      state.replyCounts = null;
      state.floorCounts = state.selectedRoot = null;
      state.phase = 'copy';
      state.root = state.liveRoot = root;
      state.cursor = root;
      state.parent = state.fragment;
      state.stack = [];
      state.nodes = 0;
    };
    state.step = () => {
      if (state.phase === 'analyze') { state.analyze(); return; }
      const node = state.cursor;
      let output = state.parent;
      let descend = false;
      state.nodes++;
      if (node.nodeType === Node.TEXT_NODE) {
        const text = node.nodeValue.slice(0, 120000 - state.characters);
        if (text.trim()) {
          output.append(document.createTextNode(text));
          state.characters += text.length;
        } else if (text) output.append(document.createTextNode(' '));
      } else if (node.nodeType === Node.ELEMENT_NODE && node !== host && node !== pageStyle
          && !(state.tiebaRoot && node.matches('.head-line, .forum-info-warp, .pc-pb-title, .pc-pb-first-floor-interactive, .pc-pb-reply-box, .comment-desc-right, .ylh-ad-container, .thread-action-area, .tooltip, .thread-content-link:empty'))
          && !(state.tiebaRoot && node.matches('.thread-image, .j_small_wrap, .threadlist_media') && !node.querySelector('img'))
          && !(state.tiebaClassic && node.matches('.d_author, .share_btn_wrapper, .post-tail-wrap, .p_props_tail, .lzl_editor_container, .j_lzl_p, .lzl_s_r, .lzl_p_p, .threadlist_author, .j_threadlist_li_left, .icon_thread_hidden, .p_content > br, .p_content > cc > br'))
          && (node !== state.replyPane || state.paneCopied)
          && (!skip.has(node.tagName) || ['BUTTON', 'IMG', 'PICTURE'].includes(node.tagName)
            || (state.paneCopied && state.replyPane?.contains(node) && ['HEADER', 'FOOTER', 'NAV'].includes(node.tagName)))
          && node.getAttribute('role') !== 'navigation') {
        const computed = getComputedStyle(node);
        const visible = !node.hidden && node.getAttribute('aria-hidden') !== 'true' && computed.display !== 'none'
          && computed.visibility !== 'hidden' && computed.visibility !== 'collapse';
        const group = node.matches(replyGroups);
        let replyMetadata;
        if (group) {
          const field = node.getAttribute('data-field');
          if (field && field.length < 8192) {
            try { replyMetadata = JSON.parse(field); } catch (_) { /* Optional reply count. */ }
          }
          if (state.tiebaClassic && replyMetadata?.total_num != null && Number(replyMetadata.total_num) === 0
              && !node.querySelector(replies)) {
            state.advance(node, false, output);
            return;
          }
        }
        // The global image switch hides source IMG/PICTURE nodes; their placeholders remain readable.
        if (node.tagName === 'IMG') {
          if (!node.hidden && node.getAttribute('aria-hidden') !== 'true') {
            const copy = imageCard(node, output);
            if (copy) markTarget(node, copy);
          }
        } else if (visible || group || (node.tagName === 'PICTURE'
            && !node.hidden && node.getAttribute('aria-hidden') !== 'true')) {
          descend = true;
          if (group) {
            const owner = inferredPost(node) || node.closest(posts);
            const {details, summary} = card(node, 'replies-card', '楼中楼回复 · 展开查看', output,
              Boolean(owner && owner === state.activeReplyOwner));
            const action = findReplyAction(node);
            prepareReplyTotal(node, details, action, replyMetadata);
            // Already loaded replies expand locally; loading more stays a separate explicit action.
            if (action && !node.matches('.lzl-wrapper')
                && !(state.tiebaClassic && node.querySelector('.lzl_single_post'))) summary.dataset.readerAction = registerAction(action);
            output = make('div', 'replies-body', '', details);
            if (!visible) {
              make('small', '', '展开后尝试加载原网页回复。', output);
              descend = false;
            }
          } else if (visible && isReadAction(node)) {
            const button = make('button', '', node.textContent.trim().slice(0, 60), output);
            if (countedReply(node)) button.textContent = '展开 ' + countedReply(node) + ' 条楼中楼回复';
            if (node.matches('.j_lzl_m')) button.textContent = '展开更多楼中楼回复';
            if (isReplyClose(node)) button.textContent = '关闭回复';
            button.type = 'button';
            button.dataset.readerAction = registerAction(node);
            descend = false;
          } else if (node.tagName === 'BUTTON') {
            descend = false;
          } else if (state.inferredPosts?.has(node) || node.matches(posts)) {
            state.postCount++;
            let metadata;
            const field = node.getAttribute('data-field');
            if (field && field.length < 8192) {
              try { metadata = JSON.parse(field); } catch (_) { /* Optional forum metadata. */ }
            }
            const floor = readFloor(node, metadata);
            const label = node.matches(tiebaOp) ? '楼主 · 正文'
              : node.matches('.top-thread-card-item, .j_thread_list.thread_top') ? '置顶帖子'
              : node.matches('.thread-card-wrapper, .j_thread_list[data-tid]') ? '阅读帖子' : floor ? '第 ' + floor + ' 楼' : '楼层回复';
            const {details, summary} = card(node, 'post-card', label, output, true);
            if (floor) details.dataset.readerFloor = floor;
            const author = node.matches(tiebaOp) ? node.parentElement.querySelector('.head-line .head-name')
              : state.inferredPosts?.get(node)?.author || node.querySelector(authors)
                || (node.matches('.thread-card-wrapper') ? node.querySelector('.forum-info-warp .forum-name') : null);
            if (author) make('span', 'post-author', author.textContent.trim().slice(0, 60), summary);
            output = make('div', 'post-body', '', details);
            if (state.tiebaClassic && node.matches('.j_thread_list') && metadata?.reply_num != null) {
              make('div', 'post-meta', '回复 ' + metadata.reply_num, output);
            }
            if (state.tiebaClassic && node.matches('.l_post')) {
              const date = [...node.querySelectorAll('.post-tail-wrap > .tail-info')]
                .find(label => /^\s*\d{4}-\d{1,2}-\d{1,2}/.test(label.textContent));
              if (date) make('div', 'post-meta', date.textContent.trim(), output);
            }
            state.buildPostBodies.set(node, output);
            if (node.matches('.image-text > #artplayer-app')) {
              make('small', 'post-meta', '原网页包含视频，可关闭阅读模式查看。', output);
              descend = false;
            }
          } else if (state.inferredReplies?.has(node) || node.matches(replies) || (!state.tiebaClassic && node.parentElement?.matches(replyGroups)
              && node.matches('div, li, article') && node.querySelector('a, [data-author]'))) {
            const container = output.nodeType === Node.ELEMENT_NODE ? output.closest('.replies-card') : null;
            if (container) {
              const count = Number(container.dataset.replyCount || 0) + 1;
              container.dataset.replyCount = count;
            }
            output = make('div', 'reply-bubble', '', output);
            if (state.tiebaRoot && node.matches('.pb-lzl-item')) {
              const author = node.querySelector('.head-name');
              if (author) make('div', 'reply-author', author.textContent.trim().slice(0, 60), output);
            }
          } else if (state.tiebaRoot && node.matches('.comment-desc-left')) {
            make('div', 'post-meta', node.textContent.trim().replace(/\s+/g, ' '), output);
            descend = false;
          } else if (state.tiebaRoot && node.matches('.thread-title')) {
            const original = node.closest('a.top-thread-card-item')
              || node.closest('.thread-card-wrapper')?.querySelector('a.thread-content-link[href]');
            const link = make('a', 'topic-title', node.textContent.trim(), output);
            if (original && /^https?:$/.test(original.protocol)) {
              link.href = original.href;
              link.target = '_blank';
              link.rel = 'noopener noreferrer';
            }
            descend = false;
          } else if ((node.matches(authors) && node.closest(posts) && !node.closest(replies))
              || (node.tagName === 'A' && state.inferredPosts?.get(inferredPost(node))?.author === node)) {
            descend = false;
          } else if (allowed.has(node.tagName)) {
            const tag = state.tiebaRoot && node.matches('.pb-content-item') ? 'span'
              : state.tiebaClassic && node.matches('.j_lzl_m_w, .lzl_li_pager') ? 'div' : node.tagName.toLowerCase();
            const copy = document.createElement(tag);
            if (node.tagName === 'A') {
              if (state.tiebaClassic && node.matches('.threadlist_title a.j_th_tit')) copy.className = 'topic-title';
              try {
                const url = new URL(node.getAttribute('href'), document.baseURI);
                if (node.hasAttribute('href') && ['http:', 'https:', 'mailto:'].includes(url.protocol)) {
                  copy.href = url.href;
                  if (node.target === '_blank') { copy.target = '_blank'; copy.rel = 'noopener noreferrer'; }
                }
              } catch (_) { /* A malformed URL remains plain text. */ }
            }
            output.append(copy);
            output = copy;
          }
          markTarget(node, output);
          const containsPosts = state.firstPost && node !== state.firstPost && node.contains(state.firstPost);
          const largeSurface = node.clientHeight >= Math.min(300, innerHeight / 2) && node.clientWidth >= innerWidth * .4;
          if (visible && (node === state.root || containsPosts || largeSurface || node.matches('[role="main"], [role="feed"]'))
              && computed.overflowY.match(/auto|scroll/) && node.scrollHeight > node.clientHeight + 1
              && (!state.buildScrollTarget || node.contains(state.buildScrollTarget)
                || node.clientWidth * node.clientHeight > state.buildScrollTarget.clientWidth * state.buildScrollTarget.clientHeight)) state.buildScrollTarget = node;
        }
      }
      state.advance(node, descend, output);
    };
    state.schedule = () => {
      if (state.disposed || state.scheduled || document.hidden || !state.cursor) return;
      state.scheduled = true;
      state.frame = requestAnimationFrame(state.flush);
      state.timer = setTimeout(state.flush, 100);
    };
    state.flush = () => {
      state.cancel();
      if (state.disposed || document.hidden) return;
      const deadline = performance.now() + 4;
      for (let steps = 0; steps < 256 && state.cursor && state.nodes < 20000 && state.characters < 120000; steps++) {
        state.step();
        if (performance.now() >= deadline) break;
      }
      if (state.phase === 'analyze' && (!state.cursor || state.nodes >= 20000)) state.startCopy();
      if (state.phase === 'copy' && !state.cursor && !state.paneCopied && state.replyPane
          && state.nodes < 20000 && state.characters < 120000) {
        state.paneCopied = true;
        const parent = state.buildPostBodies.get(state.activeReplyOwner) || state.fragment;
        const {details} = card(state.replyPane, 'replies-card', '楼中楼回复', parent, true);
        state.root = state.cursor = state.replyPane;
        state.parent = make('div', 'replies-body', '', details);
        state.stack = [];
      }
      if (state.cursor && state.nodes < 20000 && state.characters < 120000) state.schedule();
      else {
        const limited = Boolean(state.cursor);
        finishReplyCounts(state.fragment);
        const references = element => [...element.querySelectorAll('[data-reader-image], [data-reader-target]')]
          .map(node => node.dataset.readerImage || node.dataset.readerTarget).join('\n');
        const signature = state.fragment.textContent + references(state.fragment);
        const changed = state.signature !== signature;
        state.signature = signature;
        state.hasSnapshot = true;
        // Keep the floor currently under the toolbar in place while new floors/replies arrive.
        const rect = viewport.getBoundingClientRect();
        const anchor = shadow.elementFromPoint(rect.left + rect.width / 2, rect.top + toolbar.offsetHeight + 16)
          ?.closest('[data-reader-key]');
        const anchorId = anchor?.dataset.readerKey;
        const anchorTop = anchor?.getBoundingClientRect().top;
        const scrollTop = viewport.scrollTop;
        // Reuse unchanged floor cards, including their expanded reply subtree.
        for (const [id, copy] of state.buildCards) {
          const previous = state.cards.get(id);
          if (previous) copy.open = previous.open;
          if (state.fragment.contains(copy)) {
            if (previous?.isEqualNode(copy)) {
              if (!state.tiebaRoot) copy.replaceWith(previous);
              state.buildCards.set(id, previous);
            }
          } else if (previous && state.fragment.contains(previous)) state.buildCards.set(id, previous);
        }
        if (state.tiebaRoot) {
          // Keep sanitized cards when the source virtual list removes old DOM nodes. No source
          // elements/listeners are retained in this history; the action map only covers live nodes.
          if (state.clearEntries && (!state.resetScrollAfterLoad || changed)) {
            state.entries.clear();
            state.entryCharacters = 0;
            state.historyLimited = false;
            state.clearEntries = false;
          }
          const controls = make('div', 'reader-controls');
          for (const button of state.fragment.querySelectorAll('button[data-reader-action]')) {
            if (!button.closest('.post-card') && controls.childElementCount < 20) controls.append(button);
          }
          for (const post of state.fragment.querySelectorAll('.post-card')) {
            const id = post.dataset.readerKey;
            const candidate = state.buildCards.get(id) || post;
            const previous = state.entries.get(id);
            const cardSize = card => card ? card.textContent.length + references(card).length : 0;
            const size = (state.entryCharacters || 0) - cardSize(previous) + cardSize(candidate);
            if ((!previous && state.entries.size >= 500) || size > 1000000) {
              state.historyLimited = true;
              continue;
            }
            state.entries.set(id, candidate);
            state.entryCharacters = size;
          }
          // Reconcile top-level cards in place. Reattaching the entire retained history on every
          // source update would add layout work and risk clamping the scroll position to zero.
          const desired = [controls, ...state.entries.values()];
          const retained = new Set(desired);
          for (const child of [...content.childNodes]) if (!retained.has(child)) child.remove();
          let next = content.firstChild;
          for (const child of desired) {
            if (child === next) next = next.nextSibling;
            else content.insertBefore(child, next);
          }
          state.buildCards = new Map([...content.querySelectorAll('[data-reader-key]')]
            .map(element => [element.dataset.readerKey, element]));
          for (const button of content.querySelectorAll('[data-reader-action]')) {
            const available = state.buildActions.get(button.dataset.readerAction)?.isConnected;
            if (button.tagName === 'BUTTON') button.disabled = !available;
            if (!available) button.title = '此楼层已被原网页暂时卸载；已加载的回复仍可展开查看。';
          }
        }
        else content.replaceChildren(state.fragment);
        // Rebuild from committed/cached DOM: reconciliation can reuse older cards and discard copies.
        state.anchorTargets.clear();
        for (const marker of content.querySelectorAll('[data-reader-target]')) {
          for (const name of JSON.parse(marker.dataset.readerTarget)) {
            if (!state.anchorTargets.has(name)) state.anchorTargets.set(name, marker);
          }
        }
        if (state.liveRoot?.id && !state.anchorTargets.has(state.liveRoot.id)) state.anchorTargets.set(state.liveRoot.id, content);
        state.cards = state.buildCards;
        state.renderedContext = state.context;
        state.actions = state.buildActions;
        state.buildCards = state.buildActions = null;
        state.pageAction = state.buildPageAction;
        state.buildPageAction = null;
        state.buildPostBodies = null;
        if (changed && state.loadStarted && Date.now() - state.loadStarted < 15000) {
          state.finishLoad('已更新内容。');
          state.loadStarted = 0;
        }
        else if (!state.loading) {
          loadMore.disabled = false;
          loadMore.textContent = state.pageAction?.kind === 'next' ? '下一页' : '尝试加载更多';
        }
        state.scrollTarget = state.buildScrollTarget;
        state.buildScrollTarget = null;
        const replacement = state.cards.get(anchorId);
        viewport.scrollTop = state.resetScrollAfterLoad && changed ? 0
          : replacement ? scrollTop + replacement.getBoundingClientRect().top - anchorTop : scrollTop;
        if (changed) state.resetScrollAfterLoad = false;
        state.lastScroll = viewport.scrollTop;
        state.cursor = state.fragment = state.root = state.parent = null;
        state.stack = [];
        state.lastRefresh = Date.now();
        status.textContent = state.historyLimited ? '已达到本次阅读保留上限（500 条 / 100 万字），请切回原网页继续浏览。'
          : limited ? '内容较长，已显示部分正文；可切回原网页阅读全文。'
          : state.characters ? state.forumSite && !state.postCount ? '未识别到楼层结构，当前按普通正文展示。' : ''
            : '未找到可读正文，请切回原网页浏览。';
        if (state.historyLimited) {
          state.finishLoad('已保留当前内容，请切回原网页继续浏览。');
          loadMore.disabled = true;
          loadMore.textContent = '已达到保留上限';
        }
        if (state.dirty) state.requestRefresh();
      }
    };
    state.requestRefresh = () => {
      state.dirty = true;
      if (document.hidden) { state.needsRefresh = true; return; }
      if (state.disposed || state.cursor || state.updateTimer) return;
      // Coalesce bursts, and do not repeatedly restart an extraction that is still running.
      state.updateTimer = setTimeout(() => {
        state.updateTimer = null;
        state.refresh();
      }, Math.max(250, 750 - (Date.now() - state.lastRefresh)));
    };
    state.refresh = () => {
      state.cancel();
      clearTimeout(state.updateTimer);
      state.updateTimer = null;
      state.dirty = false;
      state.url = location.href;
      state.ready = document.readyState;
      title.textContent = document.title || location.hostname || '网页内容';
      domain.textContent = location.hostname || '当前页面';
      source.replaceChildren(document.createTextNode('来源：'));
      if (['http:', 'https:'].includes(location.protocol)) {
        const link = make('a', '', location.href, source);
        link.href = location.href;
      } else source.append(document.createTextNode(location.href));
      state.needsRefresh = document.hidden;
      if (document.hidden) {
        state.cursor = state.fragment = state.root = state.parent = null;
        state.buildCards = state.buildActions = state.buildScrollTarget = null;
        state.floorCounts = state.replyCounts = state.selectedRoot = state.buildPageAction = null;
        state.buildPostBodies = state.buildReplyPane = null;
        state.floorMarkers = state.replyMarkers = state.pageCandidates = [];
        state.stack = [];
        return;
      }
      const firstPost = document.querySelector(posts);
      state.forumSite = /(^|\.)tieba\.baidu\.com$/i.test(location.hostname);
      state.tiebaRoot = (state.forumSite || location.protocol === 'file:')
        // Video posts have an Artplayer mount instead of pb-content-wrap. Replies can also
        // arrive before the OP media mounts; always select the complete post in that case.
        ? document.querySelector('.image-text:has(.pb-content-wrap, #artplayer-app, .pc-pb-comments), .frs-feed-list, .home-page-wrapper .thread-container, '
          + '.p_postlist:has(.l_post), #thread_list:has(.j_thread_list)') : null;
      state.tiebaClassic = Boolean(state.tiebaRoot?.matches('.p_postlist, #thread_list'));
      const context = location.origin + location.pathname + '?' + new URL(location.href).searchParams.get('kw');
      if (state.context !== context) {
        state.clearEntries = true;
        state.context = context;
      }
      const root = state.tiebaRoot || document.querySelector('main, [role="main"]') || firstPost?.parentElement
        || document.querySelector('article') || document.body;
      state.liveRoot = root;
      state.firstPost = firstPost;
      state.buildScrollTarget = null;
      for (let ancestor = root.parentElement, depth = 0; ancestor && depth < 64; ancestor = ancestor.parentElement, depth++) {
        if (ancestor === document.body || ancestor === document.documentElement) break;
        if (/auto|scroll/.test(getComputedStyle(ancestor).overflowY) && ancestor.scrollHeight > ancestor.clientHeight + 1) {
          state.buildScrollTarget = ancestor;
          break;
        }
      }
      state.buildActions = new Map();
      state.buildCards = new Map();
      state.buildPostBodies = new Map();
      state.buildReplyPane = null;
      state.paneCopied = false;
      for (const element of state.scrollPositions.keys()) if (!element.isConnected) state.scrollPositions.delete(element);
      // Discard preferences for removed nodes while keeping all visible cards' expanded state.
      state.opened = new Map();
      for (const details of content.querySelectorAll('[data-reader-key]')) {
        state.opened.set(details.dataset.readerKey, details.open);
      }
      state.fragment = document.createDocumentFragment();
      state.selectedRoot = root;
      state.phase = 'analyze';
      state.floorMarkers = [];
      state.floorCounts = new WeakMap();
      state.replyMarkers = [];
      state.replyCounts = new WeakMap();
      state.pageCandidates = [];
      state.buildPageAction = null;
      state.cursor = document.body;
      state.parent = null;
      // The root's siblings are outside the chosen article and must never be copied.
      state.stack = [];
      state.root = document.body;
      state.nodes = state.characters = state.postCount = 0;
      if (!content.firstChild) status.textContent = '正在整理正文…';
      state.schedule();
    };
    state.visibility = () => {
      if (document.hidden) {
        state.closeImagePreview();
        state.cancel();
        clearTimeout(state.updateTimer);
        state.updateTimer = null;
        clearTimeout(state.sourceScrollTimer);
        state.sourceScrollTimer = null;
        if (state.loading) state.finishLoad('已暂停加载，返回后可重试。');
        if (state.cursor) {
          state.cursor = state.fragment = state.root = state.parent = null;
          state.buildCards = state.buildActions = state.buildScrollTarget = null;
          state.floorCounts = state.selectedRoot = state.buildPageAction = null;
          state.replyCounts = null;
          state.replyMarkers = [];
          state.buildPostBodies = state.buildReplyPane = null;
          state.floorMarkers = state.pageCandidates = [];
          state.stack = [];
          state.needsRefresh = true;
        }
        if (state.dirty) state.needsRefresh = true;
      } else if (state.needsRefresh) state.refresh();
      else state.schedule();
    };
    state.observer = new MutationObserver(records => {
      if (state.disposed) return;
      state.isolatePage();
      let inspected = 0;
      for (const record of records) {
        if (++inspected > 128) { state.requestRefresh(); break; }
        const target = record.target.nodeType === Node.ELEMENT_NODE ? record.target : record.target.parentElement;
        if (!target || target === host || target === pageStyle || target.closest('script, style')) continue;
        if (record.type === 'attributes' && record.attributeName === 'inert') continue;
        // The reader shows a static video hint; playback time and control animations are not text updates.
        if (state.tiebaRoot && target.closest('.image-text > #artplayer-app')
            && !target.matches('.image-text > #artplayer-app')) continue;
        if (record.type === 'childList' && record.addedNodes.length + record.removedNodes.length <= 4
            && [...record.addedNodes, ...record.removedNodes].every(node =>
              node === host || node === pageStyle || node.id === 'happyu-style')) continue;
        if (record.type === 'attributes' && record.attributeName === 'style') {
          const visibility = value => (value || '').match(/(?:^|;)\s*(?:display|visibility|content-visibility)\s*:[^;]*/g)?.join(';');
          if (visibility(record.oldValue) === visibility(target.getAttribute('style'))) continue;
        }
        const root = state.liveRoot;
        if (!root?.isConnected || root.contains(target) || target.contains(root)
            || state.replyPane?.contains(target) || target.closest('title')) {
          state.requestRefresh(); break;
        }
      }
    });
    state.observer.observe(document.documentElement, {childList: true, characterData: true, subtree: true,
      attributes: true, attributeOldValue: true,
      attributeFilter: ['class', 'hidden', 'aria-hidden', 'style', 'href', 'open', 'id', 'name', 'inert', 'data-floor', 'data-field', 'data-id', 'data-post-id',
        'data-total-count', 'data-total', 'data-reply-count',
        'src', 'srcset', 'data-src', 'data-original', 'data-original-src', 'data-lazy-src', 'data-actualsrc', 'data-srcset', 'alt']});
    state.scrollSource = (explicit = false) => {
      if (state.disposed || document.hidden || !state.hasSnapshot) return;
      if (state.tiebaRoot && state.historyLimited) return;
      const elapsed = Date.now() - (state.lastSourceScroll || 0);
      if (!explicit && elapsed < 400) {
        if (!state.sourceScrollTimer) state.sourceScrollTimer = setTimeout(() => {
          state.sourceScrollTimer = null;
          state.scrollSource();
        }, 400 - elapsed);
        return;
      }
      clearTimeout(state.sourceScrollTimer);
      state.sourceScrollTimer = null;
      state.lastSourceScroll = Date.now();
      const target = state.scrollTarget;
      const sourceElement = target || document.scrollingElement;
      if (!sourceElement) return;
      const nearBottom = explicit || viewport.scrollHeight - viewport.clientHeight - viewport.scrollTop < 160;
      // A cached reader and a virtual list have different scroll ranges. Mapping percentages back
      // and forth skips floors and makes the source oscillate as history grows. Advance only at
      // the reading frontier, one source viewport (or the next placeholder) at a time.
      if (state.tiebaRoot && !state.tiebaClassic && !nearBottom) return;
      if (nearBottom) {
        const action = state.pageAction;
        if (action?.kind === 'next' && !explicit) loadStatus.textContent = '本页已读完，点击“下一页”继续。';
        else if (state.beginLoad() && action?.node.isConnected && isPageAction(action.node) === action.kind) {
          if (action.kind === 'next') state.activeReplyOwner = null;
          action.node.click();
          state.requestRefresh();
          return;
        }
      }
      const range = Math.max(1, viewport.scrollHeight - viewport.clientHeight);
      const progress = nearBottom ? 1 : viewport.scrollTop / range;
      let top = progress * (sourceElement.scrollHeight - (target ? target.clientHeight : window.innerHeight));
      if (state.tiebaRoot && !state.tiebaClassic) {
        const height = target ? target.clientHeight : innerHeight;
        top = sourceElement.scrollTop + height * .8;
        const list = state.tiebaRoot.matches('.thread-container, .feed-list-container') ? state.tiebaRoot
          : state.tiebaRoot.querySelector('.thread-container, .feed-list-container');
        if (list) {
          // Placeholders have no post body. Seek the first one after the last rendered entry,
          // rather than jumping to scrollHeight (which can skip an entire virtualized batch).
          let last = null;
          for (const item of list.children) {
            if (item.matches('.virtual-list-item') && item.querySelector('.pb-comment-item, .thread-card-wrapper')) last = item;
          }
          let next = last?.nextElementSibling;
          while (next && (!next.matches('.virtual-list-item') || /^ad_/.test(next.getAttribute('data-key') || ''))) next = next.nextElementSibling;
          if (next && !next.querySelector('.pb-comment-item, .thread-card-wrapper')) {
            const boundary = next.getBoundingClientRect().top - (target ? target.getBoundingClientRect().top : 0)
              + sourceElement.scrollTop - height * .2;
            top = Math.max(top, boundary);
          }
        }
      }
      const previousTop = sourceElement.scrollTop;
      if (target) {
        if (!state.scrollPositions.has(target)) state.scrollPositions.set(target, target.scrollTop);
        target.scrollTop = top;
      } else window.scrollTo({top, behavior: 'instant'});
      // Native scroll/IntersectionObserver fire when the position changes. A repeated bottom gesture
      // also lets sites with a scroll listener retry after a transient loading failure.
      if (sourceElement.scrollTop === previousTop) (target || document).dispatchEvent(new Event('scroll', {bubbles: !target}));
      if (nearBottom) state.requestRefresh();
    };
    viewport.addEventListener('scroll', () => {
      const previous = state.lastScroll || 0;
      state.lastScroll = viewport.scrollTop;
      if (viewport.scrollTop > previous) state.scrollSource();
    }, {passive: true});
    viewport.addEventListener('wheel', event => {
      if (event.deltaY > 0 && !event.ctrlKey
          && viewport.scrollHeight - viewport.clientHeight - viewport.scrollTop < 160) state.scrollSource();
    }, {passive: true});
    loadMore.addEventListener('click', () => {
      state.scrollSource(true);
    });
    content.addEventListener('click', event => {
      const image = event.target.closest('[data-reader-image]');
      if (image) {
        event.preventDefault();
        state.openImagePreview(image);
        return;
      }
      const control = event.target.closest('[data-reader-action]');
      if (control && (control.tagName !== 'SUMMARY' || !control.parentElement.open)) {
        event.preventDefault();
        if (control.tagName === 'SUMMARY') control.parentElement.open = true;
        state.invoke(control.dataset.readerAction);
        return;
      }
      const link = event.target.closest('a[href]');
      if (link && link.target !== '_blank' && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey
          && jumpToAnchor(link)) event.preventDefault();
    });
    content.addEventListener('toggle', event => {
      const details = event.target;
      if (details.dataset.readerKey && content.contains(details)) state.opened.set(details.dataset.readerKey, details.open);
    }, true);
    document.addEventListener('visibilitychange', state.visibility);
    refresh.addEventListener('click', state.refresh);
    document.documentElement.append(pageStyle, host);
    document.documentElement.setAttribute('data-happyu-agent-reading', '');
    state.isolatePage();
    document.addEventListener('focusin', state.guardFocus, true);
    refresh.focus({preventScroll: true});
    window[key] = state;
    state.refresh();
  }
  state.previewBridge = previewImage;
  for (const [name, value] of Object.entries(theme)) state.host.style.setProperty('--reader-' + name, value);
  // Theme changes only update variables; a load completion/SPA navigation can refresh the document snapshot.
  if (state.url !== location.href || state.ready !== document.readyState) state.refresh();
})
