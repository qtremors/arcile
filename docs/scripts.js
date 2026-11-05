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

// FAQ Accordion Toggle
function toggleFaq(element) {
    const content = element.querySelector('.faq-content');
    const icon = element.querySelector('.faq-icon');
    
    // Close all others
    document.querySelectorAll('.faq-content').forEach(el => {
        if(el !== content) el.classList.remove('open');
    });
    document.querySelectorAll('.faq-icon').forEach(el => {
        if(el !== icon) el.classList.remove('rotate');
    });

    // Toggle current
    content.classList.toggle('open');
    icon.classList.toggle('rotate');
}

// Intersection Observer for Scroll Reveal Animations
document.addEventListener("DOMContentLoaded", () => {
    // Render Icons
    lucide.createIcons();

    const observerOptions = {
        root: null,
        rootMargin: '0px',
        threshold: 0.1
    };

    const observer = new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('opacity-100', 'translate-y-0');
                entry.target.classList.remove('opacity-0', 'translate-y-8');
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);

    document.querySelectorAll('.reveal').forEach(el => {
        observer.observe(el);
    });
});

const numberFormatter = new Intl.NumberFormat();
const reduceMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');

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

// Fetch Live GitHub Stats & Release Downloads
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
                const starsEl = document.getElementById('gh-stars');
                const forksEl = document.getElementById('gh-forks');
                animateCounter(starsEl, data.stargazers_count);
                animateCounter(forksEl, data.forks_count);
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

            const latestDownloads = sumReleaseDownloads([release]);
            animateCounter(document.getElementById('gh-latest-downloads'), latestDownloads);
        }
    } catch (error) {
        console.error('Error fetching the latest GitHub release:', error);
    }

    if (totalDownloadsResult.status === 'fulfilled') {
        const totalDownloads = totalDownloadsResult.value;
        animateCounter(document.getElementById('gh-total-downloads'), totalDownloads);
    } else {
        console.error('Error fetching total GitHub downloads:', totalDownloadsResult.reason);
    }
}

// Execute fetch
fetchGitHubStats();

// Mobile Menu Toggle
const mobileMenuBtn = document.getElementById('mobile-menu-btn');
const mobileMenu = document.getElementById('mobile-menu');
let isMobileMenuOpen = false;

if (mobileMenuBtn && mobileMenu) {
    const mobileMenuIcon = document.getElementById('mobile-menu-icon');
    
    function toggleMobileMenu() {
        isMobileMenuOpen = !isMobileMenuOpen;
        if (isMobileMenuOpen) {
            mobileMenu.classList.remove('translate-x-full');
            mobileMenu.classList.add('translate-x-0');
            if (mobileMenuIcon) {
                mobileMenuIcon.setAttribute('data-lucide', 'x');
            }
            document.body.style.overflow = 'hidden'; // Prevent scrolling
        } else {
            mobileMenu.classList.add('translate-x-full');
            mobileMenu.classList.remove('translate-x-0');
            if (mobileMenuIcon) {
                mobileMenuIcon.setAttribute('data-lucide', 'menu');
            }
            document.body.style.overflow = ''; // Restore scrolling
        }
        lucide.createIcons();
    }

    mobileMenuBtn.addEventListener('click', toggleMobileMenu);

    // Close menu when clicking a link
    document.querySelectorAll('.mobile-link').forEach(link => {
        link.addEventListener('click', () => {
            if (isMobileMenuOpen) toggleMobileMenu();
        });
    });
}
