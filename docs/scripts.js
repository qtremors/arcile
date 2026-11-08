// Arcile website behavior. Loaded with `defer`, so the page is parsed before this runs.

const reduceMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');

// Navbar tonal elevation once the page leaves the top. A sentinel observer replaces a scroll
// listener, so nothing runs while scrolling.
const navbar = document.getElementById('navbar');
if (navbar) {
    const navSentinel = document.createElement('div');
    navSentinel.setAttribute('aria-hidden', 'true');
    navSentinel.style.cssText = 'position:absolute;top:0;left:0;width:1px;height:20px;pointer-events:none';
    document.body.prepend(navSentinel);
    new IntersectionObserver(([entry]) => {
        navbar.classList.toggle('is-scrolled', !entry.isIntersecting);
    }).observe(navSentinel);
}

// Scroll reveal: each block fades up once, the first time it enters the screen
const revealObserver = new IntersectionObserver((entries, observer) => {
    entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
    });
}, { threshold: 0.1 });
document.querySelectorAll('.reveal').forEach(el => revealObserver.observe(el));

// Hero screenshot showcase. Tapping the phone shows the next screen. The first visit stays still;
// once the visitor scrolls away and comes back, the screens change on their own while the hero is
// on screen. Hovering pauses that, as does keyboard focus, a background tab, or reduced motion.
function setupShowcase() {
    const phone = document.getElementById('showcase-phone');
    const image = document.getElementById('showcase-image');
    const screens = [...document.querySelectorAll('#showcase-screens li')].map(item => ({
        src: item.dataset.src,
        alt: item.textContent.trim()
    }));
    if (!phone || !image || screens.length < 2) return;

    const interval = 3500;
    const decoded = new Map([[screens[0].src, Promise.resolve()]]);
    const preload = index => {
        const { src } = screens[index];
        if (!decoded.has(src)) {
            const img = new Image();
            img.src = src;
            decoded.set(src, img.decode().catch(() => {}));
        }
        return decoded.get(src);
    };

    let current = 0;
    let request = 0;

    async function showNext() {
        const next = (current + 1) % screens.length;
        current = next;
        const token = ++request;
        phone.classList.add('is-switching');
        await Promise.all([preload(next), new Promise(resolve => setTimeout(resolve, reduceMotionQuery.matches ? 0 : 150))]);
        if (token !== request) return; // a newer tap took over
        image.src = screens[next].src;
        image.alt = screens[next].alt;
        phone.classList.remove('is-switching');
        preload((next + 1) % screens.length);
    }

    let timer = 0;
    let inView = false;
    let returned = false; // true once the hero has been left and entered again
    let hovered = false;
    let focused = false;

    const schedule = () => {
        clearTimeout(timer);
        timer = 0;
        if (returned && inView && !hovered && !focused && !document.hidden && !reduceMotionQuery.matches) {
            timer = setTimeout(() => showNext().then(schedule), interval);
        }
    };

    phone.addEventListener('click', () => {
        showNext();
        schedule(); // a tap restarts the countdown
    });

    new IntersectionObserver(([entry]) => {
        if (inView && !entry.isIntersecting) returned = true;
        inView = entry.isIntersecting;
        if (inView && returned) preload((current + 1) % screens.length);
        schedule();
    }, { threshold: 0.5 }).observe(phone);

    phone.addEventListener('pointerenter', event => {
        if (event.pointerType !== 'mouse') return;
        hovered = true;
        schedule();
    });
    phone.addEventListener('pointerleave', () => {
        hovered = false;
        schedule();
    });
    // Keyboard focus pauses; the focus a tap leaves behind does not
    phone.addEventListener('focus', () => {
        focused = phone.matches(':focus-visible');
        schedule();
    });
    phone.addEventListener('blur', () => {
        focused = false;
        schedule();
    });
    document.addEventListener('visibilitychange', schedule);
    reduceMotionQuery.addEventListener('change', schedule);

    // Fetch the second screen once the page is idle, so the first tap is instant
    const warmUp = () => preload(1);
    if ('requestIdleCallback' in window) requestIdleCallback(warmUp);
    else setTimeout(warmUp, 1500);
}
setupShowcase();

// Mobile menu
const mobileMenuBtn = document.getElementById('mobile-menu-btn');
const mobileMenu = document.getElementById('mobile-menu');
if (mobileMenuBtn && mobileMenu) {
    const menuIcon = document.getElementById('mobile-menu-icon');

    const setMenuOpen = open => {
        mobileMenu.classList.toggle('is-open', open);
        mobileMenuBtn.setAttribute('aria-expanded', String(open));
        mobileMenuBtn.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
        menuIcon?.setAttribute('href', open ? '#i-x' : '#i-menu');
        document.body.style.overflow = open ? 'hidden' : '';
    };

    mobileMenuBtn.addEventListener('click', () => setMenuOpen(!mobileMenu.classList.contains('is-open')));
    document.querySelectorAll('.mobile-link').forEach(link => {
        link.addEventListener('click', () => setMenuOpen(false));
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && mobileMenu.classList.contains('is-open')) setMenuOpen(false);
    });
}

// Live GitHub stats and release downloads
const numberFormatter = new Intl.NumberFormat();

function animateCounter(element, target) {
    if (!element || !Number.isFinite(target)) return;

    const endValue = Math.max(0, Math.trunc(target));
    if (reduceMotionQuery.matches || endValue === 0) {
        element.innerText = numberFormatter.format(endValue);
        return;
    }

    const duration = 800;
    const startTime = performance.now();

    function updateCounter(currentTime) {
        const progress = Math.min((currentTime - startTime) / duration, 1);
        const easedProgress = 1 - Math.pow(1 - progress, 3);
        element.innerText = numberFormatter.format(Math.round(endValue * easedProgress));

        if (progress < 1) {
            requestAnimationFrame(updateCounter);
        }
    }

    requestAnimationFrame(updateCounter);
}

function sumReleaseDownloads(releases) {
    return releases.reduce((releaseTotal, release) => {
        const assetTotal = Array.isArray(release.assets)
            ? release.assets.reduce((total, asset) => total + (asset.download_count || 0), 0)
            : 0;
        return releaseTotal + assetTotal;
    }, 0);
}

async function fetchTotalReleaseDownloads() {
    let nextUrl = 'https://api.github.com/repos/qtremors/arcile/releases?per_page=100';
    let totalDownloads = 0;

    while (nextUrl) {
        const response = await fetch(nextUrl);
        if (!response.ok) throw new Error(`GitHub releases request failed: ${response.status}`);

        const releases = await response.json();
        totalDownloads += sumReleaseDownloads(releases);

        const nextLink = response.headers.get('link')
            ?.split(',')
            .find(link => link.includes('rel="next"'));
        nextUrl = nextLink?.match(/<([^>]+)>/)?.[1] || '';
    }

    return totalDownloads;
}

async function fetchGitHubStats() {
    const [repoResult, releaseResult, totalDownloadsResult] = await Promise.allSettled([
        fetch('https://api.github.com/repos/qtremors/arcile'),
        fetch('https://api.github.com/repos/qtremors/arcile/releases/latest'),
        fetchTotalReleaseDownloads()
    ]);

    try {
        const repoRes = repoResult.status === 'fulfilled' ? repoResult.value : null;
        if (repoRes?.ok) {
            const data = await repoRes.json();
            if (data.stargazers_count !== undefined) {
                animateCounter(document.getElementById('gh-stars'), data.stargazers_count);
                animateCounter(document.getElementById('gh-forks'), data.forks_count);
            }
        }
    } catch (error) {
        console.error('Error fetching GitHub repository stats:', error);
    }

    try {
        const releaseRes = releaseResult.status === 'fulfilled' ? releaseResult.value : null;
        if (releaseRes?.ok) {
            const release = await releaseRes.json();
            if (release.tag_name) {
                document.querySelectorAll('.download-btn-text').forEach(el => {
                    el.innerText = `Download ${release.tag_name}`;
                });
            }
            animateCounter(document.getElementById('gh-latest-downloads'), sumReleaseDownloads([release]));
        }
    } catch (error) {
        console.error('Error fetching the latest GitHub release:', error);
    }

    if (totalDownloadsResult.status === 'fulfilled') {
        animateCounter(document.getElementById('gh-total-downloads'), totalDownloadsResult.value);
    } else {
        console.error('Error fetching total GitHub downloads:', totalDownloadsResult.reason);
    }
}

fetchGitHubStats();
