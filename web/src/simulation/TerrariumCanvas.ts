import type { Creature, WorldSnapshot } from './types'

const colors = { moss: '#70d7b2', coral: '#ff927d', lilac: '#c4a8ff' }
export class TerrariumCanvas {
  private frame = 0
  private observer: ResizeObserver
  private width = 0
  private height = 0
  private world: WorldSnapshot | null = null
  private time = 0
  private lastFrameTime = 0
  private visualPositions = new Map<string, { x: number; y: number }>()
  private selectedId: string | null = null
  private hoveredId: string | null = null
  private trails = new Map<string, Array<{ x: number; y: number }>>()
  private foodBursts: Array<{ x: number; y: number; started: number; color: string }> = []
  private readonly motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  private readonly onMotionPreference = () => {
    cancelAnimationFrame(this.frame)
    if (this.motionPreference.matches) {
      if (this.world) for (const creature of this.world.creatures) this.visualPositions.set(creature.id, { x: creature.x, y: creature.y })
      this.draw()
    } else {
      this.lastFrameTime = 0
      this.frame = requestAnimationFrame(this.animate)
    }
  }
  private readonly onPointerMove = (event: PointerEvent) => {
    const creature = this.creatureAt(event)
    this.canvas.style.cursor = creature ? 'pointer' : 'default'
    if (creature?.id !== this.hoveredId) { this.hoveredId = creature?.id ?? null; this.draw() }
  }
  private readonly onPointerLeave = () => {
    if (this.hoveredId) { this.hoveredId = null; this.draw() }
  }
  private readonly onClick = (event: MouseEvent) => {
    const creature = this.creatureAt(event)
    if (creature) this.onSelect(creature.id)
  }
  constructor(private readonly canvas: HTMLCanvasElement, private readonly onSelect: (id: string) => void) {
    this.observer = new ResizeObserver(() => { this.resize(); this.draw() })
    this.observer.observe(canvas)
    canvas.addEventListener('pointermove', this.onPointerMove)
    canvas.addEventListener('pointerleave', this.onPointerLeave)
    canvas.addEventListener('click', this.onClick)
    this.motionPreference.addEventListener('change', this.onMotionPreference)
    this.resize()
    if (!this.motionPreference.matches) this.frame = requestAnimationFrame(this.animate)
  }
  setWorld(world: WorldSnapshot) {
    const previous = this.world
    if (previous) {
      for (const creature of world.creatures) {
        const old = previous.creatures.find((item) => item.id === creature.id)
        if (!old || (old.x === creature.x && old.y === creature.y)) continue
        const points = this.trails.get(creature.id) ?? []
        points.push({ x: old.x, y: old.y })
        this.trails.set(creature.id, points.slice(-9))
      }
      const remaining = new Set(world.food.map((food) => food.id))
      for (const food of previous.food) {
        if (remaining.has(food.id)) continue
        // WorldSnapshot does not identify the eater, so the removal effect stays neutral.
        this.foodBursts.push({ x: food.x, y: food.y, started: this.time, color: '#f5ca82' })
      }
    }
    this.world = world
    if (this.motionPreference.matches) {
      for (const creature of world.creatures) this.visualPositions.set(creature.id, { x: creature.x, y: creature.y })
    }
    this.draw()
  }
  setSelected(id: string) { this.selectedId = id; this.draw() }
  destroy() {
    cancelAnimationFrame(this.frame)
    this.observer.disconnect()
    this.canvas.removeEventListener('pointermove', this.onPointerMove)
    this.canvas.removeEventListener('pointerleave', this.onPointerLeave)
    this.canvas.removeEventListener('click', this.onClick)
    this.motionPreference.removeEventListener('change', this.onMotionPreference)
  }
  private animate = (frameTime: number) => {
    const elapsed = this.lastFrameTime === 0 ? 16 : Math.min(64, frameTime - this.lastFrameTime)
    this.lastFrameTime = frameTime
    this.time += elapsed / 1000
    this.updateVisualPositions(elapsed)
    this.draw()
    this.frame = requestAnimationFrame(this.animate)
  }

  private updateVisualPositions(elapsed: number) {
    if (!this.world) return
    const follow = 1 - Math.exp(-elapsed / 115)
    for (const creature of this.world.creatures) {
      const position = this.visualPositions.get(creature.id)
      if (!position) {
        this.visualPositions.set(creature.id, { x: creature.x, y: creature.y })
        continue
      }
      position.x += (creature.x - position.x) * follow
      position.y += (creature.y - position.y) * follow
    }
  }
  private creatureAt(event: MouseEvent | PointerEvent): Creature | undefined {
    if (!this.world) return undefined
    const rect = this.canvas.getBoundingClientRect(), x = (event.clientX - rect.left) / rect.width, y = (event.clientY - rect.top) / rect.height
    return this.world.creatures.find((creature) => {
      const position = this.visualPositions.get(creature.id) ?? creature
      return Math.hypot(position.x - x, position.y - y) < .055
    })
  }
  private draw() {
    if (!this.world) return
    const time = this.time
    const world: WorldSnapshot = { ...this.world, creatures: this.world.creatures.map((creature) => {
      const position = this.visualPositions.get(creature.id) ?? creature
      return { ...creature, x: position.x, y: position.y }
    }) }
    const ctx = this.canvas.getContext('2d'); if (!ctx) return
    const { width: w, height: h } = this
    ctx.clearRect(0, 0, w, h)
    const glow = ctx.createRadialGradient(w * .52, h * .48, 8, w * .52, h * .48, w * .72)
    glow.addColorStop(0, '#1e3540'); glow.addColorStop(.58, '#172a35'); glow.addColorStop(1, '#111e29')
    ctx.fillStyle = glow; ctx.fillRect(0, 0, w, h)
    const lightPools = [
      [w * .23, h * .25, w * .42, 'rgba(89,165,143,.10)'],
      [w * .78, h * .68, w * .34, 'rgba(181,133,165,.075)'],
      [w * .58, h * .1, w * .25, 'rgba(213,174,123,.06)'],
    ] as const
    for (const [x, y, radius, color] of lightPools) {
      const pool = ctx.createRadialGradient(x, y, 0, x, y, radius)
      pool.addColorStop(0, color); pool.addColorStop(1, 'rgba(0,0,0,0)')
      ctx.fillStyle = pool; ctx.fillRect(0, 0, w, h)
    }
    const midworldFog = ctx.createLinearGradient(0, h * .2, 0, h * .82)
    midworldFog.addColorStop(0, 'rgba(35,86,82,0)')
    midworldFog.addColorStop(.48, 'rgba(52,112,101,.105)')
    midworldFog.addColorStop(1, 'rgba(35,86,82,0)')
    ctx.fillStyle = midworldFog; ctx.fillRect(0, h * .2, w, h * .62)
    this.drawCreatureLightPools(ctx, world, w, h)
    this.drawBotanicalSilhouettes(ctx, w, h, time)
    for (let i = 0; i < 14; i++) {
      const x = ((i * 139 + 31) % 997) / 997 * w + Math.sin(time * .12 + i * 1.7) * 12
      const y = ((i * 271 + 83) % 991) / 991 * h + Math.cos(time * .1 + i * 1.3) * 10
      const shimmer = .09 + .09 * (Math.sin(time * .45 + i * 2) + 1) / 2
      ctx.beginPath(); ctx.arc(x, y, i % 5 === 0 ? 1.6 : 1, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(208,229,213,${shimmer})`; ctx.fill()
    }
    const vignette = ctx.createRadialGradient(w * .5, h * .48, h * .2, w * .5, h * .48, Math.max(w, h) * .72)
    vignette.addColorStop(0, 'rgba(5,12,18,0)'); vignette.addColorStop(1, 'rgba(5,11,17,.48)')
    ctx.fillStyle = vignette; ctx.fillRect(0, 0, w, h)
    this.drawStoneAndMossBed(ctx, w, h)
    this.drawFlowerPatches(ctx, w, h)
    world.food.forEach((food) => {
      const x = food.x * w, y = food.y * h, pulse = 1 + Math.sin(time * .7 + food.phase) * .2
      const size = 2.5 + food.size * 1.15
      ctx.save(); ctx.translate(x, y); ctx.rotate(time * .15 + food.phase)
      ctx.shadowColor = '#ffc76f'; ctx.shadowBlur = 12
      ctx.fillStyle = '#f5ca82'; ctx.beginPath(); ctx.moveTo(0, -size * 1.8 * pulse); ctx.quadraticCurveTo(size * 1.7, -size * .3, 0, size * 1.5 * pulse); ctx.quadraticCurveTo(-size * 1.7, -size * .3, 0, -size * 1.8 * pulse); ctx.fill()
      ctx.shadowBlur = 0; ctx.strokeStyle = 'rgba(255,244,205,.72)'; ctx.lineWidth = Math.max(1, size * .12)
      ctx.beginPath(); ctx.moveTo(0, -size * 1.15); ctx.quadraticCurveTo(size * .2, 0, 0, size * 1.05); ctx.stroke()
      ctx.fillStyle = 'rgba(255,246,202,.9)'; ctx.beginPath(); ctx.arc(-size * .25, -size * .45, Math.max(1, size * .22), 0, Math.PI * 2); ctx.fill(); ctx.restore()
    })
    this.drawFoodBursts(ctx, w, h, time)
    this.drawTargetLine(ctx, world, w, h)
    this.drawSelectedTrail(ctx, w, h)
    world.creatures.forEach((creature) => this.drawCreature(ctx, creature, w, h, time))
  }

  private drawCreatureLightPools(ctx: CanvasRenderingContext2D, world: WorldSnapshot, w: number, h: number) {
    for (const creature of world.creatures) {
      const x = creature.x * w, y = creature.y * h
      const selected = creature.id === this.selectedId
      const radius = Math.min(w, h) * (selected ? .17 : .11)
      const color = creature.kind === 'moss' ? '112,220,184' : creature.kind === 'coral' ? '255,150,125' : '191,164,255'
      const light = ctx.createRadialGradient(x, y, 0, x, y, radius)
      light.addColorStop(0, `rgba(${color},${selected ? .18 : .065})`)
      light.addColorStop(.42, `rgba(${color},${selected ? .07 : .025})`)
      light.addColorStop(1, `rgba(${color},0)`)
      ctx.fillStyle = light
      ctx.fillRect(x - radius, y - radius, radius * 2, radius * 2)
    }
  }

  private drawBotanicalSilhouettes(ctx: CanvasRenderingContext2D, w: number, h: number, time: number) {
    // Fixed compositions keep the habitat recognizable and keep decoration out of creature state.
    const plants: Array<{ x: number; height: number; lean: number; color: string; phase: number }> = [
      { x: .025, height: .34, lean: .045, color: 'rgba(70,132,117,.30)', phase: .2 },
      { x: .09, height: .23, lean: -.035, color: 'rgba(75,142,120,.23)', phase: 1.1 },
      { x: .19, height: .16, lean: .03, color: 'rgba(66,123,108,.16)', phase: 2.4 },
      { x: .32, height: .24, lean: .035, color: 'rgba(70,132,117,.13)', phase: 1.6 },
      { x: .43, height: .31, lean: -.025, color: 'rgba(73,139,119,.12)', phase: 2.1 },
      { x: .61, height: .27, lean: .03, color: 'rgba(72,133,117,.12)', phase: .9 },
      { x: .72, height: .2, lean: -.025, color: 'rgba(74,137,119,.13)', phase: 2.6 },
      { x: .91, height: .28, lean: -.045, color: 'rgba(91,131,123,.23)', phase: 1.9 },
      { x: .975, height: .38, lean: -.035, color: 'rgba(79,137,121,.31)', phase: .7 },
      { x: .82, height: .17, lean: .025, color: 'rgba(99,133,126,.15)', phase: 2.8 },
    ]
    plants.forEach((plant, index) => {
      const sway = Math.sin(time * .18 + plant.phase) * w * .003
      const rootX = plant.x * w, rootY = h * 1.015
      const topX = rootX + plant.lean * w + sway, topY = rootY - plant.height * h
      const stemColor = plant.color.replace(/\.\d+\)/, '.75)')
      ctx.save(); ctx.lineCap = 'round'; ctx.strokeStyle = stemColor; ctx.lineWidth = index % 3 === 0 ? 2.1 : 1.35
      ctx.beginPath(); ctx.moveTo(rootX, rootY); ctx.quadraticCurveTo(rootX - plant.lean * w * .25, rootY - plant.height * h * .52, topX, topY); ctx.stroke()
      for (let leaf = 0; leaf < 5; leaf++) {
        const at = .18 + leaf * .145, side = leaf % 2 === 0 ? 1 : -1
        const stemX = rootX + (topX - rootX) * at, stemY = rootY + (topY - rootY) * at
        const reach = h * (.032 + (index % 2) * .008) * (1 - leaf * .075)
        const tipX = stemX + side * reach + sway * .3, tipY = stemY - reach * .36
        ctx.fillStyle = plant.color
        ctx.beginPath(); ctx.moveTo(stemX, stemY); ctx.quadraticCurveTo(stemX + side * reach * .8, stemY - reach * 1.05, tipX, tipY); ctx.quadraticCurveTo(stemX + side * reach * .14, stemY - reach * .12, stemX, stemY); ctx.fill()
        ctx.strokeStyle = 'rgba(173,209,174,.12)'; ctx.lineWidth = .8
        ctx.beginPath(); ctx.moveTo(stemX, stemY); ctx.quadraticCurveTo(stemX + side * reach * .42, stemY - reach * .34, tipX, tipY); ctx.stroke()
      }
      ctx.restore()
    })
    // A few low fronds anchor the bottom edge without crowding the habitat.
    ctx.save(); ctx.globalAlpha = .27; ctx.strokeStyle = '#79b697'; ctx.lineWidth = 1.4
    for (let side = 0; side < 2; side++) {
      const rootX = side === 0 ? w * .27 : w * .73, rootY = h * .99
      const direction = side === 0 ? 1 : -1
      for (let frond = 0; frond < 3; frond++) {
        const endX = rootX + direction * (w * (.09 + frond * .025)), endY = rootY - h * (.075 + frond * .018)
        ctx.beginPath(); ctx.moveTo(rootX, rootY); ctx.quadraticCurveTo(rootX + direction * w * .035, rootY - h * .13, endX, endY); ctx.stroke()
        for (let leaf = 1; leaf <= 4; leaf++) {
          const at = leaf / 5, x = rootX + (endX - rootX) * at, y = rootY + (endY - rootY) * at
          const spread = h * .018
          ctx.beginPath(); ctx.moveTo(x, y); ctx.quadraticCurveTo(x + direction * spread, y - spread, x + direction * spread * 1.35, y - spread * .25); ctx.stroke()
          ctx.beginPath(); ctx.moveTo(x, y); ctx.quadraticCurveTo(x - direction * spread, y - spread, x - direction * spread * 1.35, y - spread * .25); ctx.stroke()
        }
      }
    }
    ctx.restore()
    const leaves: Array<{ x: number; y: number; length: number; angle: number; shade: string }> = [
      { x: .015, y: .77, length: .14, angle: -.47, shade: 'rgba(72,139,117,.31)' },
      { x: .09, y: .91, length: .11, angle: -.88, shade: 'rgba(86,154,126,.25)' },
      { x: .25, y: .985, length: .09, angle: -.62, shade: 'rgba(86,154,126,.2)' },
      { x: .76, y: .99, length: .095, angle: 3.7, shade: 'rgba(86,154,126,.2)' },
      { x: .91, y: .91, length: .12, angle: 3.85, shade: 'rgba(86,154,126,.26)' },
      { x: .99, y: .75, length: .14, angle: 3.52, shade: 'rgba(72,139,117,.31)' },
    ]
    leaves.forEach((leaf) => {
      const length = h * leaf.length, width = length * .31
      ctx.save(); ctx.translate(leaf.x * w, leaf.y * h); ctx.rotate(leaf.angle)
      ctx.fillStyle = leaf.shade; ctx.beginPath(); ctx.moveTo(0, 0)
      ctx.quadraticCurveTo(length * .3, -width * 1.2, length, -width * .34)
      ctx.quadraticCurveTo(length * .62, width * .78, 0, 0); ctx.fill()
      ctx.strokeStyle = 'rgba(190,218,178,.16)'; ctx.lineWidth = 1
      ctx.beginPath(); ctx.moveTo(0, 0); ctx.quadraticCurveTo(length * .48, -width * .08, length * .94, -width * .3); ctx.stroke()
      ctx.restore()
    })
  }

  private drawStoneAndMossBed(ctx: CanvasRenderingContext2D, w: number, h: number) {
    const floor = ctx.createLinearGradient(0, h * .89, 0, h)
    floor.addColorStop(0, 'rgba(19,38,42,0)'); floor.addColorStop(1, 'rgba(11,25,30,.74)')
    ctx.fillStyle = floor; ctx.fillRect(0, h * .86, w, h * .14)
    const stones = [
      [.045, .973, .061, .019], [.13, .987, .038, .013], [.34, .982, .052, .014],
      [.66, .988, .044, .012], [.88, .98, .06, .017], [.965, .966, .042, .02],
    ]
    stones.forEach(([x, y, rx, ry], index) => {
      ctx.fillStyle = index % 2 ? 'rgba(107,135,119,.14)' : 'rgba(114,139,124,.19)'
      ctx.beginPath(); ctx.ellipse(x * w, y * h, rx * w, ry * h, index % 2 ? -.08 : .06, Math.PI, Math.PI * 2); ctx.fill()
      ctx.strokeStyle = 'rgba(184,202,167,.13)'; ctx.lineWidth = 1
      ctx.beginPath(); ctx.ellipse(x * w, y * h, rx * w * .72, ry * h * .7, 0, Math.PI * 1.1, Math.PI * 1.75); ctx.stroke()
    })
  }

  private drawFlowerPatches(ctx: CanvasRenderingContext2D, w: number, h: number) {
    // These three small planted patches give the habitat a few quiet focal points.
    const patches = [
      { x: .115, y: .958, color: '#e6c987', glow: 'rgba(230,201,135,.16)' },
      { x: .49, y: .986, color: '#b7d4aa', glow: 'rgba(166,211,162,.12)' },
      { x: .89, y: .95, color: '#d5b9e8', glow: 'rgba(198,171,225,.14)' },
    ]
    patches.forEach((patch, patchIndex) => {
      const x = patch.x * w, y = patch.y * h, scale = Math.min(w, h)
      ctx.save()
      const moss = ctx.createRadialGradient(x, y, 0, x, y, scale * .065)
      moss.addColorStop(0, patch.glow); moss.addColorStop(1, 'rgba(0,0,0,0)')
      ctx.fillStyle = moss; ctx.fillRect(x - scale * .07, y - scale * .08, scale * .14, scale * .1)
      ctx.fillStyle = patchIndex % 2 ? 'rgba(106,153,116,.24)' : 'rgba(103,151,117,.28)'
      ctx.beginPath(); ctx.ellipse(x, y, scale * .045, scale * .009, 0, Math.PI, Math.PI * 2); ctx.fill()
      for (let bloom = 0; bloom < 3; bloom++) {
        const offsetX = (bloom - 1) * scale * .018
        const flowerX = x + offsetX, flowerY = y - scale * (.045 + (bloom % 2) * .019)
        ctx.strokeStyle = 'rgba(134,171,131,.55)'; ctx.lineWidth = 1
        ctx.beginPath(); ctx.moveTo(x + offsetX * .3, y); ctx.quadraticCurveTo(flowerX - offsetX * .2, flowerY + scale * .018, flowerX, flowerY); ctx.stroke()
        ctx.fillStyle = 'rgba(144,184,139,.5)'
        ctx.beginPath(); ctx.ellipse(x + offsetX * .5 - scale * .008, y - scale * .016, scale * .009, scale * .004, -.35, 0, Math.PI * 2); ctx.fill()
        ctx.shadowColor = patch.color; ctx.shadowBlur = 5
        for (let petal = 0; petal < 5; petal++) {
          const angle = petal * Math.PI * 2 / 5
          ctx.beginPath(); ctx.arc(flowerX + Math.cos(angle) * scale * .004, flowerY + Math.sin(angle) * scale * .004, scale * .0032, 0, Math.PI * 2)
          ctx.fillStyle = patch.color; ctx.fill()
        }
        ctx.shadowBlur = 0; ctx.beginPath(); ctx.arc(flowerX, flowerY, scale * .0023, 0, Math.PI * 2)
        ctx.fillStyle = '#fff0c9'; ctx.fill()
      }
      ctx.restore()
    })
  }

  private drawFoodBursts(ctx: CanvasRenderingContext2D, w: number, h: number, time: number) {
    this.foodBursts = this.foodBursts.filter((burst) => time - burst.started < .7)
    for (const burst of this.foodBursts) {
      const age = time - burst.started, fade = 1 - age / .7
      for (let piece = 0; piece < 7; piece++) {
        const angle = piece * 2.399, distance = age * (14 + piece % 3 * 4)
        ctx.beginPath(); ctx.arc(burst.x * w + Math.cos(angle) * distance, burst.y * h + Math.sin(angle) * distance, Math.max(.7, 2.1 * fade), 0, Math.PI * 2)
        ctx.fillStyle = burst.color; ctx.globalAlpha = fade * .8; ctx.fill(); ctx.globalAlpha = 1
      }
    }
  }

  private drawTargetLine(ctx: CanvasRenderingContext2D, world: WorldSnapshot, w: number, h: number) {
    const selected = world.creatures.find((creature) => creature.id === this.selectedId)
    const target = selected && this.targetFood(selected, world)
    if (!selected || !target) return
    const fromX = selected.x * w, fromY = selected.y * h, toX = target.x * w, toY = target.y * h
    ctx.save(); ctx.beginPath(); ctx.moveTo(fromX, fromY)
    ctx.quadraticCurveTo((fromX + toX) / 2, (fromY + toY) / 2 - 18, toX, toY)
    ctx.setLineDash([3, 7]); ctx.lineDashOffset = -this.time * 2.5
    ctx.strokeStyle = 'rgba(226,219,185,.43)'; ctx.lineWidth = 1.25; ctx.stroke(); ctx.restore()
  }

  private drawSelectedTrail(ctx: CanvasRenderingContext2D, w: number, h: number) {
    const points = this.trails.get(this.selectedId ?? '')
    if (!points || points.length < 2) return
    ctx.save(); ctx.beginPath(); ctx.moveTo(points[0].x * w, points[0].y * h)
    for (const point of points.slice(1)) ctx.lineTo(point.x * w, point.y * h)
    ctx.strokeStyle = 'rgba(204,228,211,.18)'; ctx.lineWidth = 2; ctx.lineCap = 'round'; ctx.stroke(); ctx.restore()
  }

  private targetFood(creature: Creature, world: WorldSnapshot) {
    const snackNumber = creature.goal.match(/snack\s+(\d+)/i)?.[1]
    return snackNumber ? world.food.find((food) => food.id === snackNumber || food.id === `f${snackNumber}`) : undefined
  }

  private drawCreature(ctx: CanvasRenderingContext2D, c: Creature, w: number, h: number, t: number) {
    const x = c.x * w + Math.sin(t * .36 + c.phase) * 2.1 + Math.sin(t * .17 + c.phase * 1.4) * .9
    const y = c.y * h + Math.sin(t * .58 + c.phase + .6) * 2.1
    const r = Math.min(w, h) * .037, color = colors[c.kind], breath = Math.sin(t * 1.45 + c.phase)
    const highlighted = c.id === this.selectedId || c.id === this.hoveredId
    ctx.save(); ctx.translate(x, y)
    ctx.fillStyle = 'rgba(4,12,17,.30)'; ctx.beginPath(); ctx.ellipse(2, r * 1.08, r * 1.12, r * .27, 0, 0, Math.PI * 2); ctx.fill()
    ctx.rotate(Math.sin(t * .36 + c.phase) * .1); ctx.scale(1 + breath * .045, 1 - breath * .045)
    if (highlighted) {
      const ringColor = c.id === this.selectedId ? 'rgba(240,231,205,.88)' : 'rgba(211,226,211,.62)'
      ctx.beginPath(); ctx.arc(0, 0, r * 1.72, 0, Math.PI * 2); ctx.strokeStyle = ringColor
      ctx.lineWidth = c.id === this.selectedId ? 1.45 : 1.2
      if (c.id === this.selectedId) { ctx.shadowColor = 'rgba(235,222,183,.55)'; ctx.shadowBlur = 6 }
      ctx.setLineDash(c.id === this.selectedId ? [] : [2, 5]); ctx.stroke(); ctx.setLineDash([]); ctx.shadowBlur = 0
      if (c.id === this.selectedId) {
        ctx.beginPath(); ctx.arc(0, 0, r * 1.92, -.75, .4); ctx.strokeStyle = 'rgba(240,231,205,.36)'; ctx.lineWidth = .9; ctx.stroke()
        ctx.fillStyle = 'rgba(244,220,174,.82)'
        for (const angle of [-.75, .4]) { ctx.beginPath(); ctx.arc(Math.cos(angle) * r * 1.92, Math.sin(angle) * r * 1.92, 1.5, 0, Math.PI * 2); ctx.fill() }
      }
    }
    this.drawCreatureSilhouette(ctx, c, r, t, color)
    ctx.shadowColor = color; ctx.shadowBlur = highlighted ? 25 : 17
    const body = ctx.createRadialGradient(-r * .32, -r * .38, 2, 0, 0, r * 1.2)
    body.addColorStop(0, '#fff4df'); body.addColorStop(.24, color); body.addColorStop(1, color)
    ctx.fillStyle = body; ctx.beginPath()
    for (let i = 0; i <= 32; i++) {
      const a = i / 32 * Math.PI * 2
      const lobe = c.kind === 'coral' ? .055 * Math.max(0, Math.sin(a * 3 + .65)) : c.kind === 'lilac' ? .025 * Math.cos(a * 2) : 0
      const wobble = 1 + lobe + .075 * Math.sin(a * 3 + t * 1.7 + c.phase) + .035 * Math.cos(a * 5 - t)
      const width = c.kind === 'moss' ? 1.1 : c.kind === 'lilac' ? .92 : 1
      const height = c.kind === 'lilac' ? .96 : c.kind === 'moss' ? .79 : .86
      const px = Math.cos(a) * r * wobble * width, py = Math.sin(a) * r * wobble * height
      if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py)
    }
    ctx.closePath(); ctx.fill(); ctx.shadowBlur = 0
    const target = this.world && this.targetFood(c, this.world)
    const lookX = target ? Math.sign(target.x - c.x) * r * .065 : 0
    const lookY = target ? Math.sign(target.y - c.y) * r * .055 : 0
    ctx.fillStyle = '#17252b'; ctx.beginPath(); ctx.ellipse(-r * .28 + lookX, -r * .02 + lookY, r * .075, r * .105, 0, 0, Math.PI * 2); ctx.ellipse(r * .26 + lookX, -r * .02 + lookY, r * .075, r * .105, 0, 0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = 'rgba(255,255,255,.8)'; ctx.beginPath(); ctx.arc(-r * .31, -r * .06, r * .025, 0, Math.PI * 2); ctx.fill()
    ctx.strokeStyle = 'rgba(69,45,45,.55)'; ctx.lineWidth = Math.max(1, r * .035); ctx.lineCap = 'round'
    ctx.beginPath(); ctx.moveTo(-r * .07, r * .22); ctx.quadraticCurveTo(0, r * .28, r * .07, r * .22); ctx.stroke()
    if (c.kind === 'moss') { ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(0, -r * .8); ctx.quadraticCurveTo(-r * .12, -r * 1.35, -r * .48, -r * 1.22); ctx.stroke(); ctx.beginPath(); ctx.ellipse(-r * .45, -r * 1.22, r * .23, r * .11, -.3, 0, Math.PI * 2); ctx.fillStyle = color; ctx.fill() }
    if (c.kind === 'coral') { ctx.fillStyle = 'rgba(255,227,205,.72)'; ctx.beginPath(); ctx.arc(0, -r * .9, r * .16, 0, Math.PI * 2); ctx.fill() }
    if (c.kind === 'lilac') { ctx.strokeStyle = 'rgba(230,211,255,.84)'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(0, 0, r * 1.23, .2, 2.6); ctx.stroke() }
    ctx.restore()
  }

  private drawCreatureSilhouette(ctx: CanvasRenderingContext2D, creature: Creature, radius: number, time: number, color: string) {
    ctx.save(); ctx.lineCap = 'round'; ctx.lineJoin = 'round'
    if (creature.kind === 'coral') {
      // Branched feelers make the coral species read differently at a glance.
      ctx.strokeStyle = color; ctx.lineWidth = radius * .11
      for (const side of [-1, 1]) {
        const tipY = -radius * .94 + Math.sin(time + creature.phase) * 1.2
        ctx.beginPath(); ctx.moveTo(side * radius * .35, -radius * .58)
        ctx.quadraticCurveTo(side * radius * .65, -radius * 1.02, side * radius * .82, tipY); ctx.stroke()
        ctx.fillStyle = '#ffd1b4'; ctx.beginPath(); ctx.arc(side * radius * .82, tipY, radius * .1, 0, Math.PI * 2); ctx.fill()
      }
    } else if (creature.kind === 'lilac') {
      ctx.strokeStyle = 'rgba(232,217,255,.9)'; ctx.lineWidth = radius * .055
      for (const side of [-1, 1]) {
        const sway = Math.sin(time * .7 + creature.phase + side) * radius * .08
        const tipX = side * radius * .44 + sway, tipY = -radius * 1.45
        ctx.beginPath(); ctx.moveTo(side * radius * .28, -radius * .72)
        ctx.quadraticCurveTo(side * radius * .52 + sway, -radius * 1.25, tipX, tipY); ctx.stroke()
        ctx.fillStyle = '#eadbff'; ctx.beginPath(); ctx.arc(tipX, tipY, radius * .09, 0, Math.PI * 2); ctx.fill()
      }
    }
    ctx.restore()
  }
  private resize() {
    const rect = this.canvas.getBoundingClientRect(), ratio = Math.min(window.devicePixelRatio || 1, 2)
    this.width = rect.width; this.height = rect.height; this.canvas.width = rect.width * ratio; this.canvas.height = rect.height * ratio
    this.canvas.getContext('2d')?.setTransform(ratio, 0, 0, ratio, 0, 0)
  }
}
