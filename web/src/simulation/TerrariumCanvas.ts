import type { Creature, WorldSnapshot } from './types'

const colors = { moss: '#70d7b2', coral: '#ff927d', lilac: '#c4a8ff' }
export class TerrariumCanvas {
  private frame = 0
  private observer: ResizeObserver
  private width = 0
  private height = 0
  private world: WorldSnapshot | null = null
  private time = 0
  private selectedId: string | null = null
  private hoveredId: string | null = null
  private readonly onPointerMove = (event: PointerEvent) => {
    const creature = this.creatureAt(event)
    this.canvas.style.cursor = creature ? 'pointer' : 'default'
    if (creature?.id !== this.hoveredId) { this.hoveredId = creature?.id ?? null; this.draw() }
  }
  private readonly onClick = (event: MouseEvent) => {
    const creature = this.creatureAt(event)
    if (creature) this.onSelect(creature.id)
  }
  constructor(private readonly canvas: HTMLCanvasElement, private readonly onSelect: (id: string) => void) {
    this.observer = new ResizeObserver(() => { this.resize(); this.draw() })
    this.observer.observe(canvas)
    canvas.addEventListener('pointermove', this.onPointerMove)
    canvas.addEventListener('click', this.onClick)
    this.resize()
    this.animate()
  }
  setWorld(world: WorldSnapshot) { this.world = world }
  setSelected(id: string) { this.selectedId = id; this.draw() }
  destroy() {
    cancelAnimationFrame(this.frame)
    this.observer.disconnect()
    this.canvas.removeEventListener('pointermove', this.onPointerMove)
    this.canvas.removeEventListener('click', this.onClick)
  }
  private animate = () => {
    this.time += .016
    this.draw()
    this.frame = requestAnimationFrame(this.animate)
  }
  private creatureAt(event: MouseEvent | PointerEvent): Creature | undefined {
    if (!this.world) return undefined
    const rect = this.canvas.getBoundingClientRect(), x = (event.clientX - rect.left) / rect.width, y = (event.clientY - rect.top) / rect.height
    return this.world.creatures.find((creature) => Math.hypot(creature.x - x, creature.y - y) < .055)
  }
  private draw() {
    if (!this.world) return
    const world = this.world, time = this.time
    const ctx = this.canvas.getContext('2d'); if (!ctx) return
    const { width: w, height: h } = this
    ctx.clearRect(0, 0, w, h)
    const glow = ctx.createRadialGradient(w * .52, h * .48, 8, w * .52, h * .48, w * .72)
    glow.addColorStop(0, '#1e3540'); glow.addColorStop(.58, '#172a35'); glow.addColorStop(1, '#111e29')
    ctx.fillStyle = glow; ctx.fillRect(0, 0, w, h)
    for (let i = 0; i < 18; i++) {
      const x = ((i * 139 + 31) % 997) / 997 * w + Math.sin(time * .16 + i) * 9
      const y = ((i * 271 + 83) % 991) / 991 * h + Math.cos(time * .13 + i) * 8
      ctx.beginPath(); ctx.arc(x, y, 1 + i % 2, 0, Math.PI * 2); ctx.fillStyle = `rgba(198,226,211,${.12 + .12 * Math.sin(time + i)})`; ctx.fill()
    }
    world.food.forEach((food) => {
      const x = food.x * w, y = food.y * h, pulse = 1 + Math.sin(time * .7 + food.phase) * .2
      ctx.save(); ctx.translate(x, y); ctx.rotate(time * .15 + food.phase)
      ctx.shadowColor = '#ffc76f'; ctx.shadowBlur = 10
      ctx.fillStyle = '#f5ca82'; ctx.beginPath(); ctx.moveTo(0, -food.size * 1.8 * pulse); ctx.quadraticCurveTo(food.size * 1.7, -food.size * .3, 0, food.size * 1.5 * pulse); ctx.quadraticCurveTo(-food.size * 1.7, -food.size * .3, 0, -food.size * 1.8 * pulse); ctx.fill()
      ctx.fillStyle = 'rgba(255,246,202,.9)'; ctx.beginPath(); ctx.arc(-food.size * .25, -food.size * .45, Math.max(1, food.size * .25), 0, Math.PI * 2); ctx.fill(); ctx.restore()
    })
    world.creatures.forEach((creature) => this.drawCreature(ctx, creature, w, h, time))
  }
  private drawCreature(ctx: CanvasRenderingContext2D, c: Creature, w: number, h: number, t: number) {
    const x = c.x * w + Math.sin(t * .65 + c.phase) * 4, y = c.y * h + Math.cos(t * .52 + c.phase) * 3
    const r = Math.min(w, h) * .031, color = colors[c.kind], breath = Math.sin(t * 2 + c.phase)
    const highlighted = c.id === this.selectedId || c.id === this.hoveredId
    ctx.save(); ctx.translate(x, y); ctx.rotate(Math.sin(t * .5 + c.phase) * .13); ctx.scale(1 + breath * .035, 1 - breath * .035)
    if (highlighted) { ctx.beginPath(); ctx.arc(0, 0, r * 1.63, 0, Math.PI * 2); ctx.strokeStyle = c.id === this.selectedId ? 'rgba(240,231,205,.84)' : 'rgba(211,226,211,.5)'; ctx.lineWidth = c.id === this.selectedId ? 1.5 : 1; ctx.setLineDash([3, 4]); ctx.stroke(); ctx.setLineDash([]) }
    ctx.shadowColor = color; ctx.shadowBlur = highlighted ? 27 : 18
    const body = ctx.createRadialGradient(-r * .32, -r * .38, 2, 0, 0, r * 1.2)
    body.addColorStop(0, '#fff4df'); body.addColorStop(.24, color); body.addColorStop(1, color)
    ctx.fillStyle = body; ctx.beginPath()
    for (let i = 0; i <= 32; i++) {
      const a = i / 32 * Math.PI * 2, wobble = 1 + .075 * Math.sin(a * 3 + t * 1.7 + c.phase) + .035 * Math.cos(a * 5 - t)
      const px = Math.cos(a) * r * wobble, py = Math.sin(a) * r * wobble * .84
      if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py)
    }
    ctx.closePath(); ctx.fill(); ctx.shadowBlur = 0
    ctx.fillStyle = '#17252b'; ctx.beginPath(); ctx.ellipse(-r * .28, -r * .02, r * .075, r * .105, 0, 0, Math.PI * 2); ctx.ellipse(r * .26, -r * .02, r * .075, r * .105, 0, 0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = 'rgba(255,255,255,.8)'; ctx.beginPath(); ctx.arc(-r * .31, -r * .06, r * .025, 0, Math.PI * 2); ctx.fill()
    if (c.kind === 'moss') { ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(0, -r * .8); ctx.quadraticCurveTo(-r * .12, -r * 1.35, -r * .48, -r * 1.22); ctx.stroke(); ctx.beginPath(); ctx.ellipse(-r * .45, -r * 1.22, r * .23, r * .11, -.3, 0, Math.PI * 2); ctx.fillStyle = color; ctx.fill() }
    if (c.kind === 'coral') { ctx.fillStyle = 'rgba(255,227,205,.72)'; ctx.beginPath(); ctx.arc(0, -r * .9, r * .16, 0, Math.PI * 2); ctx.fill() }
    if (c.kind === 'lilac') { ctx.strokeStyle = 'rgba(230,211,255,.84)'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(0, 0, r * 1.23, .2, 2.6); ctx.stroke() }
    ctx.restore()
  }
  private resize() {
    const rect = this.canvas.getBoundingClientRect(), ratio = Math.min(window.devicePixelRatio || 1, 2)
    this.width = rect.width; this.height = rect.height; this.canvas.width = rect.width * ratio; this.canvas.height = rect.height * ratio
    this.canvas.getContext('2d')?.setTransform(ratio, 0, 0, ratio, 0, 0)
  }
}
