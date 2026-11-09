(() => {
  'use strict';
  let revealed = 0;
  const revealButton = document.querySelector('#reveal-hint');
  revealButton.addEventListener('click', () => {
    revealed = (revealed + 1) % 3;
    document.querySelector('#hint-skeleton').hidden = revealed < 1;
    document.querySelector('#hint-reference').hidden = revealed < 2;
    revealButton.firstChild.textContent = revealed === 0 ? '再看一种用途 ' : revealed === 1 ? '看看延伸话题 ' : '收起其他用途 ';
    document.querySelector('#hint-status').textContent = ['已收起追问和延伸话题', '已展开追问表达', '已展开延伸话题表达'][revealed];
  });
  const scenes = {
    cafe:['AI 是店员，你是顾客','Could I get a latte with oat milk, please?','请给我一杯燕麦奶拿铁。',['说出想要的饮品','说明自己的偏好']],
    shopping:['AI 是服装店店员，你是顾客','Do you have this in a smaller size?','这件有小一号的吗？',['询问尺码','表达购买意愿']],
    directions:['AI 是路人，你是旅行者','Excuse me, how do I get to the station?','打扰一下，请问车站怎么走？',['询问路线','确认方向']],
    hotel:['AI 是前台，你是已预订的客人','Hi, I have a reservation under Chen.','你好，我有预订，姓陈。',['说明预订信息','询问入住事项']],
    campus:['AI 是同学，你是新同学','Is this seat taken? I’m new here.','这里有人坐吗？我是新来的。',['自然开启对话','了解对方的兴趣']],
    interview:['AI 是面试官，你是求职者','I enjoy turning ideas into useful products.','我喜欢把想法变成有用的产品。',['介绍自己的经验','说明应聘动机']]
  };
  document.querySelectorAll('[data-scene]').forEach(button => button.addEventListener('click', () => {
    document.querySelectorAll('[data-scene]').forEach(item => item.setAttribute('aria-pressed', String(item === button)));
    const [roles, line, translation, goals] = scenes[button.dataset.scene];
    document.querySelector('#scene-title').textContent = button.textContent;
    document.querySelector('#scene-roles').textContent = roles; document.querySelector('#scene-line').textContent = line; document.querySelector('#scene-translation').textContent = translation;
    document.querySelectorAll('#scene-goals li').forEach((item, i) => { item.lastChild.textContent = goals[i]; });
  }));
  document.querySelector('a[href="#vocab-details"]').addEventListener('click', () => { document.querySelector('#vocab-details').open = true; });
  // Keep native scrolling and readable defaults; all enhancement is optional.
  const media = window.matchMedia('(prefers-reduced-motion: reduce)');
  const toggle = document.querySelector('#motion-toggle');
  let userPaused = false;
  const surfaces = [...document.querySelectorAll('.motion-surface')];
  const visibility = new Map(surfaces.map(el => [el, false]));
  const entrances = new Set();
  const reduced = () => media.matches || userPaused;
  function syncMotion() {
    document.documentElement.classList.toggle('all-motion-paused', reduced());
    toggle.setAttribute('aria-pressed', String(reduced()));
    toggle.textContent = media.matches ? '已减少动效' : userPaused ? '播放动效' : '暂停动效';
    toggle.disabled = media.matches;
    for (const el of surfaces) el.classList.toggle('motion-paused', document.hidden || !visibility.get(el));
    if (reduced() || document.hidden) {
      for (const animation of entrances) animation.finish();
    }
  }
  toggle.addEventListener('click', () => { userPaused = !userPaused; syncMotion(); });
  media.addEventListener('change', syncMotion);
  document.addEventListener('visibilitychange', syncMotion);
  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver(entries => {
      for (const entry of entries) visibility.set(entry.target, entry.isIntersecting);
      syncMotion();
    }, { rootMargin: '30px' });
    surfaces.forEach(el => observer.observe(el));
    const reveal = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        reveal.unobserve(entry.target);
        if (reduced() || typeof entry.target.animate !== 'function') continue;
        const type = entry.target.dataset.reveal;
        let frames;
        if (type === 'topics') frames = [{ clipPath: 'inset(8% 0 0 round 18px)', opacity: .65 }, { clipPath: 'inset(0 round 18px)', opacity: 1 }];
        else if (type === 'scenario') frames = [{ transform: 'translateX(20px)', opacity: .6 }, { transform: 'translateX(0)', opacity: 1 }];
        else if (type === 'collection' || type === 'recap' || type === 'vocabulary') frames = [{ translate: '0 24px', rotate: type === 'recap' ? '-2deg' : '2deg', opacity: .65 }, { translate: '0 0', rotate: '0deg', opacity: 1 }];
        else frames = [{ translate: '0 18px', opacity: .65 }, { translate: '0 0', opacity: 1 }];
        const animation = entry.target.animate(frames, { duration: type === 'hero' ? 750 : 620, easing: 'cubic-bezier(.16,1,.3,1)' });
        entrances.add(animation);
        animation.finished.then(() => entrances.delete(animation)).catch(() => entrances.delete(animation));
      }
    }, { threshold: .13 });
    document.querySelectorAll('[data-reveal]').forEach(el => reveal.observe(el));
  } else surfaces.forEach(el => visibility.set(el, true));
  syncMotion();
  const links = [...document.querySelectorAll('.chapter-links a')];
  const chapters = links.map(link => document.querySelector(link.getAttribute('href')));
  const progress = document.querySelector('.reading-progress span');
  let scheduled = false;
  let lastChapter = -1;
  function updateScroll() {
    scheduled = false;
    const max = document.documentElement.scrollHeight - innerHeight;
    progress.style.transform = 'scaleX(' + (max > 0 ? Math.max(0, Math.min(1, scrollY / max)) : 0) + ')';
    let current = 0;
    chapters.forEach((section, index) => { if (section.getBoundingClientRect().top <= 160) current = index; });
    links.forEach((link, index) => index === current ? link.setAttribute('aria-current', 'location') : link.removeAttribute('aria-current'));
    if (current !== lastChapter) {
      const nav = document.querySelector('.chapter-links');
      const link = links[current];
      const viewport = nav.getBoundingClientRect();
      const bounds = link.getBoundingClientRect();
      if (bounds.left < viewport.left || bounds.right > viewport.right) {
        nav.scrollTo({ left: nav.scrollLeft + bounds.left - viewport.left - 8, behavior: 'auto' });
      }
      lastChapter = current;
    }
  }
  const requestUpdate = () => { if (!scheduled) { scheduled = true; requestAnimationFrame(updateScroll); } };
  addEventListener('scroll', requestUpdate, { passive: true });
  addEventListener('resize', requestUpdate, { passive: true });
  updateScroll();
})();
