export {}

interface PaddleCheckoutSettings {
  allowLogout?: boolean
  displayMode?: 'inline' | 'overlay'
  frameTarget?: string
  frameInitialHeight?: number
  frameStyle?: string
  locale?: string
  successUrl?: string
  theme?: 'light' | 'dark'
  variant?: 'one-page' | 'multi-page'
}

interface PaddleCheckoutItem {
  priceId: string
  quantity?: number
}

interface PaddleCheckoutOpenOptions {
  items: PaddleCheckoutItem[]
  customer?: {
    id?: string
    email?: string
  }
  customData?: Record<string, unknown>
  settings?: PaddleCheckoutSettings
}

interface PaddleCheckoutEvent {
  name: string
  data?: Record<string, unknown>
}

interface PaddleEventCallback {
  (event: PaddleCheckoutEvent): void
}

interface PaddleInitializeOptions {
  token: string
  environment?: 'sandbox' | 'production'
  eventCallback?: PaddleEventCallback
  checkout?: PaddleCheckoutSettings
}

interface PaddleInstance {
  Initialize: (options: PaddleInitializeOptions) => void
  Checkout: {
    open: (options: PaddleCheckoutOpenOptions) => void
    close: () => void
  }
  Environment: {
    set: (env: 'sandbox' | 'production') => void
  }
}

declare global {
  interface Window {
    Paddle?: PaddleInstance
  }
}
