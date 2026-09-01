export function useToast() {
  function notify(message, type = 'success', options = {}) {
    window.dispatchEvent(new CustomEvent('polaris:toast', { detail: { message, type, ...options } }))
  }
  return { notify, success: message => notify(message, 'success'), warning: message => notify(message, 'warning'), error: message => notify(message, 'error'), info: message => notify(message, 'info') }
}
