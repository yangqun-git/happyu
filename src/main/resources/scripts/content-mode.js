((css, imagesBlocked) => {
  const key = '__happyuContentMode';
  let state = window[key];
  if (state && state.css === css && state.style.isConnected) return;
  if (!state) {
    // CSS is installed immediately. Only conflicting inline !important styles need JS.
    state = {
      style: document.createElement('style'), css: '', imagesBlocked: false,
      pending: new Map(), walker: null, current: null, rescan: false, scheduled: false
    };
    state.style.id = 'happyu-style';
    const normalizedStyles = new WeakMap();
    const themedProperties = [
      'background-color', 'background-image', 'color', 'border-top-color',
      'border-right-color', 'border-bottom-color', 'border-left-color',
      'text-shadow', 'box-shadow', 'caret-color'
    ];
    const mediaProperties = [...themedProperties, 'display', 'visibility'];
    const mediaSelector = "img, picture, source, video, audio, canvas, svg, iframe[src*='video']";
    state.normalize = element => {
      if (!element.style) return;
      const value = element.getAttribute('style');
      if (!value || !value.includes('!')) return;
      const media = state.imagesBlocked && element.matches(mediaSelector);
      const previous = normalizedStyles.get(element);
      if (previous && previous.value === value && previous.media === media) return;
      for (const property of media ? mediaProperties : themedProperties) {
        if (element.style.getPropertyPriority(property) === 'important') {
          // Keep the site's value; let our stylesheet control the presentation.
          // Remove first: changing only the priority can leave Chromium's computed style stale.
          const value = element.style.getPropertyValue(property);
          element.style.removeProperty(property);
          element.style.setProperty(property, value, '');
        }
      }
      normalizedStyles.set(element, {value: element.getAttribute('style'), media});
    };
    state.install = () => {
      const parent = document.head || document.documentElement;
      if (parent && !state.style.isConnected) parent.appendChild(state.style);
    };
    state.cancel = () => {
      cancelAnimationFrame(state.frameId);
      clearTimeout(state.timerId);
      state.scheduled = false;
    };
    state.defer = () => {
      state.cancel();
      // Hidden pages retain no queue of detached nodes; scan the live DOM on return.
      state.pending.clear();
      state.walker = state.current = null;
      state.rescan = true;
    };
    state.hasWork = () => state.walker || state.pending.size || state.rescan;
    state.schedule = () => {
      if (state.scheduled || document.hidden || !state.hasWork()) return;
      state.scheduled = true;
      state.frameId = requestAnimationFrame(state.flush);
      // Some embedded/occluded browsers stop painting without a visibility event.
      state.timerId = setTimeout(state.flush, 100);
    };
    state.queue = (element, subtree) => {
      if (state.pending.size >= 512 && !state.pending.has(element)) {
        // Bound retained references even if DOM changes outrun processing.
        state.pending.clear();
        state.rescan = true;
      }
      state.pending.set(element, subtree || state.pending.get(element) || false);
    };
    state.step = () => {
      if (!state.walker) {
        let root;
        if (state.rescan) {
          state.rescan = false;
          root = document;
        } else if (state.pending.size) {
          const [element, subtree] = state.pending.entries().next().value;
          state.pending.delete(element);
          if (!element.isConnected) return;
          if (!subtree) {
            state.normalize(element);
            return;
          }
          root = element;
        } else return;
        // Visit every element incrementally: a filtered nextNode/querySelectorAll
        // could otherwise traverse an arbitrarily large subtree in one call.
        state.walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);
        state.current = root === document ? state.walker.nextNode() : root;
      }
      const element = state.current;
      if (element && !element.isConnected) {
        // A site removed our cursor; recover the remaining live DOM on a later scan.
        state.walker = state.current = null;
        state.rescan = true;
        return;
      }
      if (element) {
        // A queued change/addition reached by this walk needs no separate pass.
        state.pending.delete(element);
        state.normalize(element);
        state.current = state.walker.nextNode();
      }
      if (!state.current) state.walker = null;
    };
    state.flush = () => {
      state.cancel();
      if (document.hidden) {
        state.defer();
        return;
      }
      const deadline = performance.now() + 4;
      for (let steps = 0; steps < 256 && state.hasWork(); steps++) {
        state.step();
        if (performance.now() >= deadline) break;
      }
      state.schedule();
    };
    state.observer = new MutationObserver(records => {
      state.install();
      if (document.hidden) {
        state.defer();
        return;
      }
      let observed = 0;
      recordsLoop: for (const record of records) {
        if (++observed > 512) {
          state.pending.clear();
          state.rescan = true;
          break;
        }
        if (record.type === 'attributes') {
          // Ignore our own writes; src changes can turn an iframe into media.
          if (record.attributeName !== 'style' ||
              normalizedStyles.get(record.target)?.value !== record.target.getAttribute('style')) {
            state.queue(record.target, false);
          }
        } else {
          for (const node of record.addedNodes) {
            if (++observed > 512) {
              state.pending.clear();
              state.rescan = true;
              break recordsLoop;
            }
            if (node.nodeType === Node.ELEMENT_NODE) state.queue(node, true);
          }
        }
      }
      state.schedule();
    });
    state.observer.observe(document, {
      childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'src']
    });
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) state.defer();
      else state.schedule();
    });
    window[key] = state;
  }
  const scanExisting = !state.css || (!state.imagesBlocked && imagesBlocked);
  state.imagesBlocked = imagesBlocked;
  state.css = css;
  if (state.style.textContent !== css) state.style.textContent = css;
  state.install();
  if (scanExisting) state.rescan = true;
  state.schedule();
})
