import type { WorldSnapshot } from './types'

export type EngineStatus = 'connecting' | 'ready' | 'error'
export interface EngineView {
  status: EngineStatus
  world: WorldSnapshot | null
  error: string | null
}

class EngineStore {
  private view: EngineView = { status: 'connecting', world: null, error: null }
  private listeners = new Set<() => void>()
  private connection: Promise<void> | null = null
  private requestQueue: Promise<unknown> = Promise.resolve()

  getSnapshot = () => this.view

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  connect = () => {
    if (this.view.status === 'ready') return Promise.resolve()
    if (this.connection) return this.connection
    this.setView({ ...this.view, status: 'connecting', error: null })
    this.connection = fetch('/api/world')
      .then((response) => this.readWorld(response))
      .then((world) => this.setView({ status: 'ready', world, error: null }))
      .catch((error: unknown) => this.setFailure(error))
      .finally(() => { this.connection = null })
    return this.connection
  }

  advance = (ticks: number) => this.enqueue(async () => {
    const response = await fetch('/api/step', { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: String(ticks) })
    this.setWorld(await this.readWorld(response))
  })

  restart = () => this.enqueue(async () => {
    const response = await fetch('/api/restart', { method: 'POST' })
    this.setWorld(await this.readWorld(response))
  })

  applyProgram = (creatureId: string, source: string) => this.enqueue(async () => {
    const response = await fetch(`/api/creatures/${encodeURIComponent(creatureId)}/program`, {
      method: 'PUT', headers: { 'Content-Type': 'text/plain; charset=utf-8' }, body: source,
    })
    if (!response.ok) {
      const message = await response.text()
      if (response.status >= 500) this.setView({ ...this.view, status: 'error', error: message })
      throw new EngineRequestError(message || `Program rejected (${response.status})`, response.status)
    }
    this.setWorld(await this.readWorld(response))
  })

  private enqueue<T>(operation: () => Promise<T>) {
    const next = this.requestQueue.then(operation, operation)
    this.requestQueue = next.then(() => undefined, () => undefined)
    return next.catch((error: unknown) => {
      if (!(error instanceof EngineRequestError) || error.status >= 500) this.setFailure(error)
      throw error
    })
  }

  private async readWorld(response: Response): Promise<WorldSnapshot> {
    if (!response.ok) {
      const message = await response.text()
      throw new EngineRequestError(message || `Simulation API returned ${response.status}`, response.status)
    }
    return await response.json() as WorldSnapshot
  }

  private setWorld(world: WorldSnapshot) { this.setView({ status: 'ready', world, error: null }) }
  private setFailure(error: unknown) {
    const message = error instanceof Error ? error.message : 'The Java simulation could not be reached.'
    this.setView({ ...this.view, status: 'error', error: message })
  }
  private setView(view: EngineView) { this.view = view; this.listeners.forEach((listener) => listener()) }
}

class EngineRequestError extends Error {
  constructor(message: string, readonly status: number) { super(message); this.name = 'EngineRequestError' }
}

export const engineStore = new EngineStore()
