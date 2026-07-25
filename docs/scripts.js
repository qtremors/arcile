// Tailwind Configuration
tailwind.config = {
    theme: {
        extend: {
            fontFamily: {
                sans: ['Roboto', 'sans-serif'],
                display: ['Outfit', 'sans-serif'],
            },
            colors: {
                // Authentic Material 3 Dark Theme Palette
                m3: {
                    bg: '#141218',
                    surface: '#1D1B20',
                    surfaceContainerLow: '#1D1B20',
                    surfaceContainer: '#211F26',
                    surfaceContainerHigh: '#2B2930',
                    surfaceContainerHighest: '#36343B',
                    primary: '#D0BCFF',
                    onPrimary: '#381E72',
                    primaryContainer: '#4F378B',
                    onPrimaryContainer: '#EADDFF',
                    secondary: '#CCC2DC',
                    onSecondary: '#332D41',
                    secondaryContainer: '#4A4458',
                    onSecondaryContainer: '#E8DEF8',
                    outline: '#938F99',
                    outlineVariant: '#49454F',
                    onSurface: '#E6E0E9',
                    onSurfaceVariant: '#CAC4D0',
                }
            },
            animation: {
                'float': 'float 6s ease-in-out infinite',
            },
            keyframes: {
                float: {
                    '0%, 100%': { transform: 'translateY(0)' },
                    '50%': { transform: 'translateY(-12px)' },
                }
            },
            transitionTimingFunction: {
                // M3 Expressive Spring Transition Curve
                'spring': 'cubic-bezier(0.34, 1.56, 0.64, 1)',
            }
        }
    }
}

// Navbar surface tonal elevation effect on scroll
const navbar = document.getElementById('navbar');
window.addEventListener('scroll', () => {
    if (window.scrollY > 20) {
        navbar.classList.remove('bg-m3-bg');
        navbar.classList.add('bg-m3-surfaceContainer/80', 'backdrop-blur-lg', 'shadow-lg', 'border-b', 'border-m3-outlineVariant/10');
    } else {
        navbar.classList.add('bg-m3-bg');
        navbar.classList.remove('bg-m3-surfaceContainer/80', 'backdrop-blur-lg', 'shadow-lg', 'border-b', 'border-m3-outlineVariant/10');
    }
});

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

// Fetch Live GitHub Stats & Latest Release
async function fetchGitHubStats() {
    try {
        const repoRes = await fetch('https://api.github.com/repos/qtremors/arcile');
        if (repoRes.ok) {
            const data = await repoRes.json();
            if (data.stargazers_count !== undefined) {
                const starsEl = document.getElementById('gh-stars');
                const forksEl = document.getElementById('gh-forks');
                if (starsEl) starsEl.innerText = data.stargazers_count;
                if (forksEl) forksEl.innerText = data.forks_count;
            }
        }

        const releaseRes = await fetch('https://api.github.com/repos/qtremors/arcile/releases/latest');
        if (releaseRes.ok) {
            const release = await releaseRes.json();
            if (release.tag_name) {
                document.querySelectorAll('.download-btn-text').forEach(el => {
                    el.innerText = `Download ${release.tag_name}`;
                });
            }

            let latestDownloads = 0;
            if (release.assets && Array.isArray(release.assets)) {
                release.assets.forEach(asset => {
                    latestDownloads += asset.download_count || 0;
                });
            }
            const downloadsEl = document.getElementById('gh-downloads');
            if (downloadsEl && latestDownloads > 0) {
                downloadsEl.innerText = latestDownloads;
            }
        }
    } catch (error) {
        console.error('Error fetching GitHub stats:', error);
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
