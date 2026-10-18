import { useUI } from '../store/useUI.js'

// role=status + aria-live=polite, with the message in its own element: this was a plain div whose
// text changed, so nothing ever read "Rest over — next set!" out. The span and the flex row are
// the snackbar's layout — a message on the left and one action on the right. `toastAction` is the
// one thing using that slot today: the Undo offered after deleting a logged workout.
export default function Toast() {
  const msg = useUI(s => s.toastMsg)
  const action = useUI(s => s.toastAction)
  return <div id="toast" role="status" aria-live="polite" className={msg ? 'show' : ''}>
    <span>{msg}</span>
    {action && <button className="toast-act" onClick={() => { useUI.getState().dismissToast(); action.onClick() }}>{action.label}</button>}
  </div>
}
