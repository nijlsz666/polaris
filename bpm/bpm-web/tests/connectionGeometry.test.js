import test from 'node:test'
import assert from 'node:assert/strict'
import { bestPorts, connectionPath, connectionPreviewPath, portPoint } from '../src/connectionGeometry.js'

const start = { type: 'start', x: 120, y: 205 }
const task = { type: 'userTask', x: 360, y: 175 }
const end = { type: 'end', x: 1150, y: 105 }
const gateway = { type: 'exclusiveGateway', x: 665, y: 190 }

test('chooses ports from the dominant axis between nodes', () => {
  assert.deepEqual(bestPorts(start, task), { sourcePort: 'right', targetPort: 'left' })
  assert.deepEqual(bestPorts(task, { type: 'userTask', x: 360, y: 360 }), { sourcePort: 'bottom', targetPort: 'top' })
})

test('routes horizontal connections outward from both ports', () => {
  const path = connectionPath(start, task, 'right', 'left')
  assert.match(path, /^M 190 240 H 214/)
  assert.match(path, /H 360$/)
})

test('routes mixed ports without leaving the source in the wrong direction', () => {
  const path = connectionPath(start, end, 'right', 'top')
  assert.match(path, /^M 190 240 H 214/)
  assert.match(path, /V 105$/)
})

test('uses the actual inset ports rendered by gateway nodes', () => {
  assert.deepEqual(portPoint(gateway, 'left'), { x: 673, y: 237 })
  assert.deepEqual(portPoint(gateway, 'right'), { x: 751, y: 237 })
})

test('connection preview starts at the selected port and never defaults to stage origin', () => {
  const path = connectionPreviewPath(start, 'right', { x: 500, y: 300 })
  assert.match(path, /^M 190 240 H 214/)
  assert.doesNotMatch(path, /H 0|V 0/)
  assert.deepEqual(portPoint(start, 'right'), { x: 190, y: 240 })
  assert.equal(connectionPreviewPath(start, 'right', portPoint(start, 'right')), 'M 190 240 H 214')
})
