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
            }
        }
    }
}

// Render Icons
lucide.createIcons();

// Navbar surface tonal elevation effect on scroll
const navbar = document.getElementById('navbar');
window.addEventListener('scroll', () => {
    if (window.scrollY > 20) {
        navbar.classList.remove('bg-m3-bg');
        navbar.classList.add('bg-m3-surfaceContainer', 'shadow-md');
    } else {
        navbar.classList.add('bg-m3-bg');
        navbar.classList.remove('bg-m3-surfaceContainer', 'shadow-md');
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

// Fetch Live GitHub Stats
async function fetchGitHubStats() {
    try {
        const response = await fetch('https://api.github.com/repos/qtremors/arcile');
        if (!response.ok) return;
        const data = await response.json();
        
        if (data.stargazers_count !== undefined) {
            document.getElementById('gh-stars').innerText = data.stargazers_count;
            document.getElementById('gh-forks').innerText = data.forks_count;
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
    function toggleMobileMenu() {
        isMobileMenuOpen = !isMobileMenuOpen;
        if (isMobileMenuOpen) {
            mobileMenu.classList.remove('translate-x-full');
            mobileMenu.classList.add('translate-x-0');
            mobileMenuBtn.innerHTML = '<i data-lucide="x" class="w-6 h-6"></i>';
            document.body.style.overflow = 'hidden'; // Prevent scrolling
        } else {
            mobileMenu.classList.add('translate-x-full');
            mobileMenu.classList.remove('translate-x-0');
            mobileMenuBtn.innerHTML = '<i data-lucide="menu" class="w-6 h-6"></i>';
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
