import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import { MOBILE } from './lib/mobile.js'
import './index.css'
// Material 3, layered on purpose in this order: roles first, components last.
// index.css is upstream's and stays untouched; equal-specificity `:root` rules in
// m3.tokens.css win over it, and m3.components.css wins over both. See the headers.
import './m3.tokens.css'
import './m3.components.css'

// App.jsx restores per-route scroll itself; the browser's own attempt races it.
if ('scrollRestoration' in history) history.scrollRestoration = 'manual'

createRoot(document.getElementById('root')).render(
  <StrictMode><App /></StrictMode>
)

// Not in the mobile build: the native shell already serves everything from disk.
if (!MOBILE && 'serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.register('sw.js').catch(() => {})
}
