import { render } from 'preact'
import { App } from './App'
import { boot, errorText } from './core'
import './styles.css'

const root = document.getElementById('app')!

// The page's theme choice, if the reader made one; otherwise the system decides.
try {
  const theme = localStorage.getItem('mymemos.theme')
  if (theme === 'light' || theme === 'dark') document.documentElement.dataset.theme = theme
} catch {
  // Storage can be refused; the system theme is a fine default.
}

boot().then(
  () => render(<App />, root),
  (e) => {
    root.innerHTML = ''
    const p = document.createElement('p')
    p.className = 'empty'
    p.textContent = `MyMemos could not open its local database: ${errorText(e)}. A private window, or a browser set to refuse site data, can cause this.`
    root.appendChild(p)
  },
)

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  addEventListener('load', () => void navigator.serviceWorker.register('/sw.js'))
}
