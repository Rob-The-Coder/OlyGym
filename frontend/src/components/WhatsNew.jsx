import { useStore } from '../store/useStore.js'
import { useUI } from '../store/useUI.js'
import { t } from '../lib/i18n.js'
import { changesSince, latestEntry } from '../lib/changelog.js'

// The bundled release highlights as one M3 basic dialog: a heading per version and one line per
// change. `.center` already caps the height and scrolls, so a long history stays inside it.
export function WhatsNew({ entries, close }) {
  return <div className="dlg">
    <h3>{t("What's new")}</h3>
    {entries.map(entry => <div className="wn-entry" key={entry.version}>
      <h4 className="wn-ver">v{entry.version}</h4>
      <ul className="wn-list">
        {entry.bullets.map((bullet, i) => <li key={i}>{t(bullet)}</li>)}
      </ul>
    </div>)}
    <div className="dlg-acts">
      <button className="dlg-btn" onClick={close}>{t('Got it')}</button>
    </div>
  </div>
}

// Opens the dialog for the releases the saved profile has not acknowledged yet. With nothing
// new (a manual open, or a version bumped before its entry was written) it still shows the
// newest release, so the list is never empty.
export function whatsNewSheet({ onDismiss } = {}) {
  const ack = useStore.getState().S.newsSeen
  const entries = changesSince(ack)
  const shown = entries.length ? entries : [latestEntry()]
  const sheet = useUI.getState().openSheet(close => <WhatsNew entries={shown} close={close} />, { kind: 'center' })
  if (onDismiss) {
    // Every dismissal route — Got it, backdrop, Escape, Android back — ends in closeSheet, so
    // the sheet leaving the store is the one point they share. Unsubscribe first: the callback
    // must run exactly once for the auto-opened dialog.
    const unsubscribe = useUI.subscribe(state => {
      if (state.sheets.some(s => s.id === sheet.id)) return
      unsubscribe()
      onDismiss()
    })
  }
  return sheet
}
