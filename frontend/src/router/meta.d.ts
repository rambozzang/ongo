import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    allowAuthenticated?: boolean
    breadcrumb?: string
    videoTitle?: string
  }
}
