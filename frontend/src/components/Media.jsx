import { useState } from 'react'
import { imageChain } from '../lib/media.js'
import { useStore } from '../store/useStore.js'
import { t, exerciseNameFor } from '../lib/i18n.js'
import Icon from './Icon.jsx'

// The exercise's demo picture: the poster frame of its YouTube video, hotlinked from
// img.youtube.com (see lib/media.js) — the catalogue ships no image of its own. There is nothing
// to pause any more, so the old tap-to-pause is gone; a tap only retries after a failure.
// `compact` shrinks it (superset cards). `minimizable` (workout view) adds a persistent
// minimize/expand control so the picture stops eating the screen; the chosen size is saved to
// settings and carries across exercises and future workouts (issue #12). Settings can also turn
// workout media off entirely (gifSize 'off') — then nothing renders here and the exercise card
// closes up, exactly like a custom exercise without media. Any other/legacy value acts as 'full'.
// Custom exercises have no video — the block stays empty by design (issue #11).
export default function Media({ ex, id, compact, minimizable }) {
  // YouTube has no maxresdefault for older uploads, so a 404 steps to the next frame in the
  // chain; once the chain is exhausted a neutral tile stands in — a dropped connection, an
  // expired session on a gated instance or a CDN hiccup used to leave the browser's
  // broken-image glyph on a white block.
  const chain = imageChain(ex)
  const [step, setStep] = useState(0)
  const gifSize = useStore(s => s.S.gifSize)
  const update = useStore(s => s.update)
  if (!chain.length) return null
  if (minimizable && gifSize === 'off') return null
  const mini = minimizable && gifSize === 'mini'
  const toggleSize = e => { e.stopPropagation(); update(s => { s.gifSize = mini ? 'full' : 'mini' }) }
  const src = chain[step]
  const retry = () => setStep(0)
  return (
    <div className={'exmedia' + (compact ? ' compact' : '') + (mini ? ' mini' : '') + (src ? '' : ' broken')} id={id} onClick={src ? undefined : retry}>
      {src
        ? <img decoding="async" draggable={false} src={src} alt={exerciseNameFor(ex)} onError={() => setStep(s => s + 1)} />
        : <div className="exmedia-x"><Icon name="dumbbell" /></div>}
      {minimizable && (
        <button className="giftoggle" onClick={toggleSize}>
          <Icon name={mini ? 'expand' : 'minimize'} />{mini ? t('Expand') : t('Minimize')}
        </button>
      )}
      {/* The frame is a still, but it names what the picture is: WS3 hangs the player off it. */}
      {!mini && src && (
        <span className="gifhint"><Icon name="play" />{t('video')}</span>
      )}
    </div>
  )
}

// List-row picture: the same poster frame, cropped square by CSS, cheap size, lazy.
export function Thumb({ ex }) {
  const chain = imageChain(ex, ['mqdefault', 'hqdefault'])
  const [step, setStep] = useState(0)
  const src = chain[step]
  if (!src) return <div className="thumb thumb-x"><Icon name="dumbbell" /></div>
  return <img className="thumb" loading="lazy" decoding="async" draggable={false} src={src} alt="" onError={() => setStep(s => s + 1)} />
}
