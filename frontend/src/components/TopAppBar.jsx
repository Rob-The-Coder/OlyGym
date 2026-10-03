import { useEffect, useRef, useState } from 'react'

/**
 * The Material 3 large top app bar (WS13).
 *
 * Two siblings rather than one box: the 64dp action row is the sticky element and the big
 * title scrolls *under* it. That is the point — a bar that shrinks as you scroll changes
 * the height of everything above the content, so the page jumps under your thumb. Here
 * nothing changes height, so the only movement is the title sliding behind the row and the
 * small title fading into it.
 *
 * Pinning is a scroll listener, not `animation-timeline: scroll()`. Both degrade, but
 * differently: a scroll timeline the WebView does not know leaves a bar that never gets a
 * title, where a missing scroll listener still leaves the bar with its actions and its way
 * back. The JS path is the one that stays useful in the worse case.
 *
 * `title` may be a string (rendered as an h1) or a node — WeekEdit puts its editable week
 * name here. A node has no sensible small-title copy, so `smallTitle` supplies it.
 */
export default function TopAppBar({ title, subtitle = null, smallTitle, leading = null, actions = null }) {
  const row = useRef(null)
  const big = useRef(null)
  const [pinned, setPinned] = useState(false)
  const small = smallTitle === undefined ? (typeof title === 'string' ? title : null) : smallTitle

  useEffect(() => {
    let raf = 0
    const measure = () => {
      raf = 0
      const b = big.current, r = row.current
      if (!b || !r) return
      // the 4px of slack is what keeps a sub-pixel layout from flickering the title in and out
      setPinned(b.getBoundingClientRect().bottom < r.getBoundingClientRect().bottom - 4)
    }
    // measured once on mount, so a restored scroll position (App.jsx) starts already pinned
    const onScroll = () => { if (!raf) raf = requestAnimationFrame(measure) }
    measure()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      if (raf) cancelAnimationFrame(raf)
    }
  }, [])

  return <>
    <div className={'ab-row' + (leading ? '' : ' nolead') + (pinned ? ' pinned' : '')} ref={row}>
      {leading}
      {/* only once the big title is behind the row, so a reader who never scrolls sees one title */}
      {pinned && small ? <span className="ab-t">{small}</span> : null}
      <span className="ab-sp" />
      {actions ? <div className="ab-acts">{actions}</div> : null}
    </div>
    <div className="ab-big" ref={big}>
      {typeof title === 'string' ? <h1 className="ab-title">{title}</h1> : title}
      {subtitle ? <div className="ab-sub">{subtitle}</div> : null}
    </div>
  </>
}
