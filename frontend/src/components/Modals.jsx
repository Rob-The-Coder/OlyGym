import { useEffect, useRef } from 'react'
import { useUI } from '../store/useUI.js'
import { keyboardOpen } from '../lib/viewport-guard.js'

// One bottom sheet (or centered dialog) with swipe-to-dismiss.
let sheetSeq = 0
function Sheet({ sheet }) {
  const { closeSheet } = useUI()
  const ref = useRef(null)      // the drag target — .sheet only
  const box = useRef(null)      // the dialog itself, whichever kind it is
  const labelId = useRef('sheet-t-' + (++sheetSeq))

  // A dialog has to say that it is one, that the page behind it is not the thing to read, and
  // what it is called. Every sheet in the app already opens with an h3 that names it, so the name
  // is taken from that heading rather than from thirty new props — and the id is generated, so a
  // second sheet's heading cannot collide with the first's.
  //
  // Focus moves into the sheet, and goes back where it came from when the sheet closes. That is
  // not a nicety: the page behind is inert while a sheet is open, so a Tab that started on the
  // button that opened it would otherwise land in nothing at all.
  useEffect(() => {
    const el = box.current
    if (!el) return
    el.setAttribute('role', 'dialog')
    el.setAttribute('aria-modal', 'true')
    const heading = el.querySelector('h2, h3, .sect-t')
    if (heading) {
      heading.id = labelId.current
      el.setAttribute('aria-labelledby', labelId.current)
    }
    const from = document.activeElement
    el.setAttribute('tabindex', '-1')
    el.focus?.({ preventScroll: true })
    return () => {
      if (from && from !== document.body && from.focus && document.contains(from)) from.focus({ preventScroll: true })
    }
  }, [])
  // startY null = no gesture. axis stays null until the finger has travelled far enough to
  // tell a vertical pull from a sideways scroll; 'x' hands the gesture to whatever scrolls
  // horizontally under it (chip strips, heatmap) and the sheet stays put.
  const idle = () => ({ startX: null, startY: null, axis: null, delta: 0, vy: 0, lastY: 0, lastT: 0 })
  const drag = useRef(idle())

  // a gesture that begins on a slider (or opted-out control) belongs to that control, not to
  // the sheet's swipe-to-dismiss — so it keeps working while you drag. Horizontal strips are
  // opted out too: a sideways scroll must never wobble or pull down the sheet.
  const NODRAG = 'input[type=range], [data-nodrag], .chips, .hm-wrap'
  const begin = (target, x, y) => {
    const el = ref.current
    if (target.closest && target.closest(NODRAG)) { drag.current = idle(); return }
    if (el.scrollTop > 0) { drag.current = idle(); return }
    drag.current = { ...idle(), startX: x, startY: y, lastY: y, lastT: performance.now() }
  }
  const move = (e, x, y) => {
    const el = ref.current, d = drag.current
    if (d.startY === null) return
    // synthetic events (tests) may carry no x at all: treat that as straight down
    const dx = Number.isFinite(x) ? x - d.startX : 0, dy = y - d.startY
    if (d.axis === null) {
      // axis lock after ~8px: clearly-more-vertical wins the sheet, anything else is a scroll
      if (Math.abs(dx) < 8 && Math.abs(dy) < 8) return
      d.axis = Math.abs(dy) > Math.abs(dx) * 1.2 ? 'y' : 'x'
      if (d.axis === 'x') return
    }
    if (d.axis === 'x') return
    const now = performance.now()
    // velocity from the last move, so a fast short flick can still be told from a slow drag
    if (now > d.lastT) { d.vy = (y - d.lastY) / (now - d.lastT); d.lastY = y; d.lastT = now }
    if (dy > 0 && el.scrollTop <= 0) {
      e.preventDefault()
      d.delta = dy
      el.style.transition = 'none'
      el.style.transform = `translateY(${dy}px)`
    } else if (d.delta > 0) {
      // the pull reversed above the start: snap back before the sheet is allowed to scroll,
      // otherwise it scrolls while still hanging below its resting place
      e.preventDefault()
      d.delta = 0
      el.style.transform = 'translateY(0px)'
    }
  }
  const onTouchStart = e => begin(e.target, e.touches[0].clientX, e.touches[0].clientY)
  const onTouchMove = e => move(e, e.touches[0].clientX, e.touches[0].clientY)
  const onTouchEnd = () => {
    const el = ref.current, d = drag.current
    if (d.startY === null) return
    el.style.transition = 'transform .2s'
    // a long pull, or a short but fast flick, dismisses
    const flick = d.delta > 30 && d.vy > 0.6
    if ((d.delta > 90 || flick) && !sheet.locked) { el.style.transform = 'translateY(110%)'; setTimeout(() => closeSheet(sheet.id), 180) }
    else el.style.transform = ''
    drag.current = idle()
  }
  // Mouse drag (desktop testing / trackpads): same swipe-to-dismiss behaviour.
  const onMouseDown = e => { if (e.button === 0) begin(e.target, e.clientX, e.clientY) }
  const onMouseMove = e => move(e, e.clientX, e.clientY)
  const onMouseUp = () => onTouchEnd()

  // non-passive touchmove so preventDefault works (bottom sheets only; centered dialogs have no ref)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    el.addEventListener('touchmove', onTouchMove, { passive: false })
    window.addEventListener('mouseup', onMouseUp)
    return () => {
      el.removeEventListener('touchmove', onTouchMove)
      window.removeEventListener('mouseup', onMouseUp)
    }
  }, [])

  const close = () => closeSheet(sheet.id)
  if (sheet.kind === 'center') {
    return (
      <div>
        <div className="mback" onClick={() => { if (!sheet.locked) close() }} />
        <div className="center" ref={box}>{sheet.render(close)}</div>
      </div>
    )
  }
  return (
    <div>
      <div className="mback" onClick={() => { if (!sheet.locked) close() }} />
      <div className="sheet" ref={el => { ref.current = el; box.current = el }} onTouchStart={onTouchStart} onTouchEnd={onTouchEnd}
        onMouseDown={onMouseDown} onMouseMove={onMouseMove} onMouseUp={onMouseUp} onMouseLeave={onMouseUp}>
        <div className="grab" />
        {sheet.render(close)}
      </div>
    </div>
  )
}

export default function Modals() {
  const sheets = useUI(s => s.sheets)
  const closeSheet = useUI(s => s.closeSheet)
  const prevLen = useRef(0)
  const suppressPop = useRef(false)
  const pushedEntries = useRef(0)
  const sheetEntries = useRef([])

  // Every opened sheet gets a history entry so Android back dismisses it instead of
  // leaving the page (issue #63). Keep the pushed-entry count and each active sheet's
  // live-entry status explicit: sheet count cannot tell whether popstate already spent
  // an entry (especially for a locked sheet) or whether several sheets opened at once.
  useEffect(() => {
    const prev = prevLen.current
    prevLen.current = sheets.length
    if (sheets.length > prev) {
      for (let i = prev; i < sheets.length; i++) {
        sheetEntries.current.push({ openedAt: location.href, live: true })
        history.pushState({ OlyGymSheet: true }, '')
        pushedEntries.current++
      }
    } else if (sheets.length < prev) {
      const closedEntries = sheetEntries.current.splice(sheets.length, prev - sheets.length)
      const rewind = closedEntries.filter(entry =>
        entry.live && !(typeof entry.openedAt === 'string' && location.href !== entry.openedAt)).length
      if (rewind > 0) {
        pushedEntries.current = Math.max(0, pushedEntries.current - rewind)
        suppressPop.current = true
        history.go(-rewind)
      }
      // Entries skipped because the app moved on remain in pushedEntries as deliberate
      // leaks until a later popstate consumes them with no corresponding active sheet.
    }
  }, [sheets.length])

  useEffect(() => {
    const onPop = () => {
      if (suppressPop.current) { suppressPop.current = false; return }
      if (pushedEntries.current <= 0) return
      pushedEntries.current--
      // The browser has already spent one pushed entry. Mark the latest live active
      // sheet entry spent even when the sheet is locked; with no active entry this is a
      // moved-on leak, which is still accounted for by the counter decrement above.
      for (let i = sheetEntries.current.length - 1; i >= 0; i--) {
        if (sheetEntries.current[i].live) {
          sheetEntries.current[i].live = false
          break
        }
      }
      const top = sheets[sheets.length - 1]
      if (top && !top.locked) closeSheet(top.id)
    }
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [sheets, closeSheet])

  // The page behind an open sheet is inert. Focus cannot reach it and a screen reader does not
  // read it — which is the whole point of a modal, and was the one thing nothing said before.
  // Feature-detected: this is Chrome and the Android WebView, but a missing inert must not leave
  // the sheet open with the page focusable, so the Tab ring below carries the keyboard on its own.
  useEffect(() => {
    if (!sheets.length) return
    if (typeof HTMLElement === 'undefined' || !('inert' in HTMLElement.prototype)) return
    const root = document.getElementById('modal-root')
    // #modal-root is a sibling of #app inside the React container, not a child of body: inerting
    // body's children would inert the dialog itself.
    const behind = root ? [...root.parentElement.children].filter(el => el !== root) : []
    behind.forEach(el => el.setAttribute('inert', ''))
    return () => behind.forEach(el => el.removeAttribute('inert'))
  }, [sheets.length > 0])

  // lock the page behind any open sheet (iOS-safe), and keep the keyboard inside the top one
  useEffect(() => {
    if (!sheets.length) return
    const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    const onKey = e => {
      const st = useUI.getState()
      const top = st.sheets[st.sheets.length - 1]
      if (e.key === 'Escape') {
        if (top && !top.locked) st.closeSheet(top.id)
        return
      }
      // Tab closes the ring inside the topmost dialog. It would otherwise leave the sheet for the
      // browser's own chrome and come back to the first control, which reads as the sheet having
      // gone away — and with several sheets stacked, focus must not wander into the ones below.
      if (e.key !== 'Tab') return
      const boxes = document.querySelectorAll('#modal-root .sheet, #modal-root .center')
      const el = boxes[boxes.length - 1]
      if (!el) return
      const items = [...el.querySelectorAll(FOCUSABLE)]
      const active = document.activeElement
      const inside = el.contains(active)
      if (!items.length) { e.preventDefault(); el.focus?.({ preventScroll: true }); return }
      const first = items[0], last = items[items.length - 1]
      if (e.shiftKey && (!inside || active === first || active === el)) { e.preventDefault(); last.focus?.({ preventScroll: true }) }
      else if (!e.shiftKey && (!inside || active === last)) { e.preventDefault(); first.focus?.({ preventScroll: true }) }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [sheets.length])
  useEffect(() => {
    if (!sheets.length) return
    // A text field on the page behind (a set's weight, the Library search) keeps focus when a
    // button opens a sheet — WebKit does not blur on button taps — and its keyboard then
    // stays up under the sheet, or leaves the viewport displaced when it finally goes. The
    // sheet owns the screen now; a field inside the sheet (the picker search) is left alone.
    const a = document.activeElement
    if (a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA' || a.isContentEditable) && !a.closest?.('#modal-root')) a.blur?.()
    const y = window.scrollY || 0
    const b = document.body.style
    b.position = 'fixed'; b.top = -y + 'px'; b.left = '0'; b.right = '0'; b.width = '100%'
    return () => {
      b.position = b.top = b.left = b.right = b.width = ''
      window.scrollTo(0, y)
      // If the sheet closed with the keyboard still up (tap "+" in the picker, then finish),
      // iOS scrolls the page again while the keyboard dismisses — after the line above ran.
      // Ask once more when that animation is over. The window-level guard in
      // lib/viewport-guard.js covers the keyboard closing while a sheet stays open.
      // Only with the keyboard up, though: without one there is nothing to undo, and a late
      // restore would overwrite a scroll the page behind made on purpose because the sheet
      // closed — the workout list going to the current exercise after ⋯ → Layout → List
      // (issue #224). At this point the sheet's field is already out of the DOM but the
      // keyboard has not started to go, so the visual viewport still tells the truth.
      if (!keyboardOpen()) return
      window.setTimeout(() => { if (document.body.style.position !== 'fixed') window.scrollTo(0, y) }, 350)
    }
  }, [sheets.length > 0])

  if (!sheets.length) return null
  return (
    <div id="modal-root" className="open">
      {sheets.map(s => <Sheet key={s.id} sheet={s} />)}
    </div>
  )
}
