export async function openCamera(video) {
  if (!navigator.mediaDevices?.getUserMedia) throw new Error('当前设备不支持摄像头访问')
  const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false })
  video.srcObject = stream
  await video.play()
  return stream
}

export function stopCamera(stream) { stream?.getTracks?.().forEach(track => track.stop()) }

let detectorPromise

async function getDetector() {
  if (!('BarcodeDetector' in window)) return null
  if (!detectorPromise) {
    detectorPromise = (async () => {
      const preferred = ['qr_code', 'code_128', 'code_39', 'ean_13', 'ean_8', 'upc_a', 'upc_e', 'itf', 'data_matrix', 'pdf417', 'aztec']
      const formats = window.BarcodeDetector.getSupportedFormats
        ? (await window.BarcodeDetector.getSupportedFormats()).filter(format => preferred.includes(format))
        : preferred
      return new window.BarcodeDetector({ formats: formats.length ? formats : ['qr_code'] })
    })().catch(() => null)
  }
  return detectorPromise
}

export async function detectBarcode(video) {
  const detector = await getDetector()
  if (!detector) return null
  const codes = await detector.detect(video)
  return codes[0]?.rawValue || null
}
