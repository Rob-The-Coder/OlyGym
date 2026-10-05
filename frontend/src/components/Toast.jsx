import { useUI } from '../store/useUI.js'

// role=status + aria-live=polite, with the message in its own element: this was a plain div whose
// text changed, so nothing ever read "Rest over — next set!" out. The span and the flex row are
// the snackbar's layout — a message on the left and room for one action on the right, which
// nothing uses yet.
export default function Toast() {
  const msg = useUI(s => s.toastMsg)
  return <div id="toast" role="status" aria-live="polite" className={msg ? 'show' : ''}>
    <span>{msg}</span>
  </div>
}
