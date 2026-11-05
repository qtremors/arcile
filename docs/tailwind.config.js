// Builds tailwind.css for the static site. After changing classes in index.html or scripts.js:
//   npx tailwindcss@3.4.17 -c docs/tailwind.config.js -i docs/tailwind.input.css -o docs/tailwind.css --minify
module.exports = {
    content: [__dirname + '/index.html', __dirname + '/scripts.js'],
    theme: {
        extend: {
            fontFamily: {
                sans: ['Roboto', 'sans-serif'],
                display: ['Outfit', 'sans-serif'],
            },
            colors: {
                // Material 3 dark theme palette
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
                // M3 Expressive spring curve
                'spring': 'cubic-bezier(0.34, 1.56, 0.64, 1)',
            }
        }
    }
}
