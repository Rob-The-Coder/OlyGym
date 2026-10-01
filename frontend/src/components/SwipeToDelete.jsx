import { useEffect, useRef, useState } from 'react'
import { t } from '../lib/i18n.js'

// Swipe-left-to-reveal-delete, same touch/mouse-drag shape Modals.jsx already uses for
// swipe-to-dismiss (drag ref, transform during move, snap on release) — horizontal instead
// of vertical, and it reveals a button rather than acting outright, since a swipe that goes
// through by accident is a worse mistake than a sheet that closes by accident.
//
// touchmove has to be a real (non-passive) listener, not React's onTouchMove prop — the same
// reason Modals.jsx attaches its own via addEventListener({ passive: false }) rather than the
// JSX prop: React's synthetic touch handlers are passive, so preventDefault from inside one is
// silently ignored, and on iOS Safari a touch that starts on a row gets claimed for page
// scroll before a passive handler ever gets a say. Axis-locking matters just as much: this
// only calls preventDefault once a touch has clearly gone more horizontal than vertical, so a
// normal vertical scroll is never hijacked — both start with the touch essentially stationary
// or moving on the y axis, and this backs off the instant it can tell. No row here carries a
// drag-to-reorder gesture any more, so the wrapper sets no `data-nodrag` for one to opt out of.
//
// The row keeps its own opaque background rather than trusting whatever sits behind it in the
// DOM (a `.card`'s fill, usually) — without it the red button peeks out at the row's edge even
// at rest, since a transparent row lets an absolutely-positioned sibling show straight through.
const REVEAL = 76
export default function SwipeToDelete({ children, onDelete, deleteLabel, className, onClick }) {
  const outerRef = useRef(null)
  const rowRef = useRef(null)
  // The red button is only put on screen once a swipe is actually going horizontal. Left on at
  // rest it bleeds a red arc out of the row's rounded corner: the row is opaque and covers it, but
  // at fractional device pixels Chrome's compositor shows a sliver of the layer underneath, which
  // is exactly the "strange red outline" this used to be reported as (QA WS5, second round).
  const [armed, setArmed] = useState(false)
  const drag = useRef({ startX: null, startY: null, delta: 0, open: false, axis: null })

  const setX = (x, animate) => {
    const el = rowRef.current
    if (!el) return
    el.style.transition = animate ? 'transform .18s ease-out' : 'none'
    el.style.transform = `translateX(${x}px)`
  }
  const start = (x, y) => { drag.current = { startX: x, startY: y, delta: 0, open: drag.current.open, axis: null } }
  const move = (x, y, ev) => {
    const d = drag.current
    if (d.startX === null) return
    const dx = x - d.startX, dy = y - d.startY
    if (d.axis === null) {
      if (Math.abs(dx) < 8 && Math.abs(dy) < 8) return
      d.axis = Math.abs(dx) > Math.abs(dy) ? 'x' : 'y'
    }
    if (d.axis === 'y') return
    if (!armed) setArmed(true)
    ev?.preventDefault?.()
    d.delta = dx
    const base = d.open ? -REVEAL : 0
    setX(Math.max(-REVEAL - 12, Math.min(0, base + dx)), false)
  }
  const end = () => {
    const d = drag.current
    if (d.startX === null) return
    if (d.axis !== 'x') { d.startX = null; return }
    const traveled = (d.open ? -REVEAL : 0) + d.delta
    d.open = traveled < -REVEAL / 2
    setX(d.open ? -REVEAL : 0, true)
    if (!d.open) setArmed(false)
    d.startX = null
  }

  useEffect(() => {
    const el = outerRef.current
    if (!el) return
    const onTouchMove = e => move(e.touches[0].clientX, e.touches[0].clientY, e)
    el.addEventListener('touchmove', onTouchMove, { passive: false })
    return () => el.removeEventListener('touchmove', onTouchMove)
  }, [])

  return (
    <div ref={outerRef} style={{ position: 'relative', overflow: 'hidden', borderRadius: 'var(--r-card)' }}
      onTouchStart={e => { if (e.target.closest('button,input')) return; start(e.touches[0].clientX, e.touches[0].clientY) }}
      onTouchEnd={end}
      onMouseDown={e => { if (e.button !== 0 || e.target.closest('button,input')) return; start(e.clientX, e.clientY) }}
      onMouseMove={e => { if (drag.current.startX !== null && e.buttons === 1) move(e.clientX, e.clientY) }}
      onMouseUp={end}
      onMouseLeave={() => { if (drag.current.startX !== null) end() }}>
      <button className="swipe-del" aria-label={deleteLabel || t('Delete')}
        style={{
          position: 'absolute', inset: '0 0 0 auto', width: REVEAL, background: 'var(--red)', color: '#fff',
          fontSize: 12, fontWeight: 600, opacity: armed ? 1 : 0, pointerEvents: armed ? 'auto' : 'none'
        }}
        onClick={() => { setX(0, true); drag.current.open = false; setArmed(false); onDelete() }}>{t('Delete')}</button>
      <div ref={rowRef} className={className}
        onClick={e => { if (drag.current.open) { e.stopPropagation(); setX(0, true); drag.current.open = false; setArmed(false); return } onClick && onClick(e) }}
        style={{ background: 'var(--surface)', position: 'relative' }}>
        {children}
      </div>
    </div>
  )
}
