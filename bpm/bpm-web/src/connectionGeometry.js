const HORIZONTAL_PORTS = new Set(['left', 'right'])

const PORT_VECTORS = {
  top: { x: 0, y: -1 },
  right: { x: 1, y: 0 },
  bottom: { x: 0, y: 1 },
  left: { x: -1, y: 0 }
}

const GATEWAY_TYPES = new Set(['exclusiveGateway', 'parallelGateway', 'inclusiveGateway'])

export function nodeSize(node) {
  if (['exclusiveGateway', 'parallelGateway', 'inclusiveGateway'].includes(node.type)) return { width: 94, height: 94 }
  if (['start', 'end', 'timerEvent', 'messageEvent'].includes(node.type)) return { width: 70, height: 70 }
  return { width: 190, height: 78 }
}

export function nodeCenter(node) {
  const size = nodeSize(node)
  return { x: node.x + size.width / 2, y: node.y + size.height / 2 }
}

export function bestPorts(fromNode, toNode) {
  const a = nodeCenter(fromNode)
  const b = nodeCenter(toNode)
  const dx = b.x - a.x
  const dy = b.y - a.y
  if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? { sourcePort: 'right', targetPort: 'left' } : { sourcePort: 'left', targetPort: 'right' }
  return dy >= 0 ? { sourcePort: 'bottom', targetPort: 'top' } : { sourcePort: 'top', targetPort: 'bottom' }
}

export function portPoint(node, port) {
  const size = nodeSize(node)
  const inset = GATEWAY_TYPES.has(node.type) ? 8 : 0
  if (port === 'top') return { x: node.x + size.width / 2, y: node.y + inset }
  if (port === 'bottom') return { x: node.x + size.width / 2, y: node.y + size.height - inset }
  if (port === 'left') return { x: node.x + inset, y: node.y + size.height / 2 }
  return { x: node.x + size.width - inset, y: node.y + size.height / 2 }
}

function addVector(point, port, distance) {
  const vector = PORT_VECTORS[port] || PORT_VECTORS.right
  return { x: point.x + vector.x * distance, y: point.y + vector.y * distance }
}

function samePoint(a, b) {
  return a.x === b.x && a.y === b.y
}

function compactPoints(points) {
  const compact = []
  for (const point of points) {
    if (!compact.length || !samePoint(compact[compact.length - 1], point)) compact.push(point)
  }
  return compact
}

function appendPathCommand(commands, from, to) {
  if (samePoint(from, to)) return
  if (from.y === to.y) commands.push(`H ${to.x}`)
  else if (from.x === to.x) commands.push(`V ${to.y}`)
  else commands.push(`L ${to.x} ${to.y}`)
}

function pointsToPath(points) {
  const compact = compactPoints(points)
  if (!compact.length) return ''
  const commands = [`M ${compact[0].x} ${compact[0].y}`]
  for (let index = 1; index < compact.length; index += 1) appendPathCommand(commands, compact[index - 1], compact[index])
  return commands.join(' ')
}

function routeBetweenPorts(from, to, sourcePort, targetPort, stub = 24) {
  const sourceExit = addVector(from, sourcePort, stub)
  const targetEntry = addVector(to, targetPort, stub)
  const sourceHorizontal = HORIZONTAL_PORTS.has(sourcePort)
  const targetHorizontal = HORIZONTAL_PORTS.has(targetPort)
  const points = [from, sourceExit]

  if (sourceHorizontal && targetHorizontal) {
    if (sourceExit.y === targetEntry.y) points.push(targetEntry)
    else {
      const midX = Math.round((sourceExit.x + targetEntry.x) / 2)
      points.push({ x: midX, y: sourceExit.y }, { x: midX, y: targetEntry.y }, targetEntry)
    }
  } else if (!sourceHorizontal && !targetHorizontal) {
    if (sourceExit.x === targetEntry.x) points.push(targetEntry)
    else {
      const midY = Math.round((sourceExit.y + targetEntry.y) / 2)
      points.push({ x: sourceExit.x, y: midY }, { x: targetEntry.x, y: midY }, targetEntry)
    }
  } else if (sourceHorizontal) {
    points.push({ x: targetEntry.x, y: sourceExit.y }, targetEntry)
  } else {
    points.push({ x: sourceExit.x, y: targetEntry.y }, targetEntry)
  }

  points.push(to)
  return compactPoints(points)
}

function routeToCursor(from, cursor, sourcePort, stub = 24) {
  const sourceExit = addVector(from, sourcePort, stub)
  if (samePoint(from, cursor)) return [from, sourceExit]
  const sourceHorizontal = HORIZONTAL_PORTS.has(sourcePort)
  const points = [from, sourceExit]
  if (sourceHorizontal) {
    if (sourceExit.y === cursor.y) points.push(cursor)
    else {
      const midX = Math.round((sourceExit.x + cursor.x) / 2)
      points.push({ x: midX, y: sourceExit.y }, { x: midX, y: cursor.y }, cursor)
    }
  } else if (sourceExit.x === cursor.x) points.push(cursor)
  else {
    const midY = Math.round((sourceExit.y + cursor.y) / 2)
    points.push({ x: sourceExit.x, y: midY }, { x: cursor.x, y: midY }, cursor)
  }
  return compactPoints(points)
}

export function connectionPath(fromNode, toNode, sourcePort, targetPort) {
  return pointsToPath(routeBetweenPorts(portPoint(fromNode, sourcePort), portPoint(toNode, targetPort), sourcePort, targetPort))
}

export function connectionPreviewPath(fromNode, sourcePort, cursor) {
  return pointsToPath(routeToCursor(portPoint(fromNode, sourcePort), cursor, sourcePort))
}

export function connectionLabelPoint(fromNode, toNode, sourcePort, targetPort) {
  const points = routeBetweenPorts(portPoint(fromNode, sourcePort), portPoint(toNode, targetPort), sourcePort, targetPort)
  let longest = null
  for (let index = 1; index < points.length; index += 1) {
    const from = points[index - 1]
    const to = points[index]
    const length = Math.abs(to.x - from.x) + Math.abs(to.y - from.y)
    if (!longest || length > longest.length) longest = { from, to, length }
  }
  if (!longest) return { x: 0, y: 0 }
  if (longest.from.y === longest.to.y) return { x: (longest.from.x + longest.to.x) / 2, y: longest.from.y - 8 }
  return { x: longest.from.x + 8, y: (longest.from.y + longest.to.y) / 2 }
}
