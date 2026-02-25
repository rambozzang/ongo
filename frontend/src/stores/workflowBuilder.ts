import { defineStore } from 'pinia'
import type {
  WfWorkflow,
  WfNode,
  WfConnection,
  WfNodeType,
  WfNodeKind,
  WfNodeDefinition,
  WfTemplate,
  WfExecutionLog,
  WfPort,
} from '@/types/workflowBuilder'
import { workflowBuilderApi } from '@/api/workflowBuilder'
import { useNotificationStore } from '@/stores/notification'

// ─── Node Definitions ─────────────────────────────────
export const NODE_DEFINITIONS: WfNodeDefinition[] = [
  // Triggers
  { kind: 'VIDEO_UPLOADED', type: 'trigger', label: '영상 업로드', icon: 'CloudArrowUpIcon', description: '영상이 업로드되면 실행', defaultConfig: {} },
  { kind: 'VIDEO_PUBLISHED', type: 'trigger', label: '영상 게시', icon: 'PlayCircleIcon', description: '영상이 게시되면 실행', defaultConfig: {} },
  { kind: 'COMMENT_RECEIVED', type: 'trigger', label: '댓글 수신', icon: 'ChatBubbleLeftIcon', description: '댓글이 달리면 실행', defaultConfig: { keywords: [] } },
  { kind: 'SCHEDULE_DUE', type: 'trigger', label: '예약 시간', icon: 'ClockIcon', description: '예약된 시간에 실행', defaultConfig: { cronExpression: '' } },
  { kind: 'ANALYTICS_MILESTONE', type: 'trigger', label: '성과 마일스톤', icon: 'ChartBarIcon', description: '조회수/좋아요 목표 달성 시 실행', defaultConfig: { metric: 'views', threshold: 1000 } },
  { kind: 'SUBSCRIBER_MILESTONE', type: 'trigger', label: '구독자 마일스톤', icon: 'UserGroupIcon', description: '구독자 수 목표 달성 시 실행', defaultConfig: { threshold: 1000 } },
  { kind: 'CREDIT_LOW', type: 'trigger', label: '크레딧 부족', icon: 'ExclamationTriangleIcon', description: 'AI 크레딧이 부족할 때 실행', defaultConfig: { threshold: 10 } },
  // Conditions
  { kind: 'PLATFORM', type: 'condition', label: '플랫폼 필터', icon: 'FunnelIcon', description: '특정 플랫폼에서만 실행', defaultConfig: { platforms: [] } },
  { kind: 'TAG_MATCH', type: 'condition', label: '태그 매칭', icon: 'TagIcon', description: '특정 태그가 포함된 경우 실행', defaultConfig: { tags: [], matchMode: 'any' } },
  { kind: 'TIME_RANGE', type: 'condition', label: '시간대 필터', icon: 'CalendarDaysIcon', description: '특정 시간대에만 실행', defaultConfig: { startHour: 9, endHour: 18 } },
  { kind: 'VIEW_COUNT', type: 'condition', label: '조회수 조건', icon: 'EyeIcon', description: '조회수 기준 필터', defaultConfig: { operator: 'GTE', value: 100 } },
  { kind: 'DURATION', type: 'condition', label: '영상 길이', icon: 'FilmIcon', description: '영상 길이 기준 필터', defaultConfig: { operator: 'LTE', seconds: 60 } },
  { kind: 'LANGUAGE', type: 'condition', label: '언어 필터', icon: 'LanguageIcon', description: '특정 언어의 콘텐츠만 실행', defaultConfig: { languages: ['ko'] } },
  // Actions
  { kind: 'SEND_NOTIFICATION', type: 'action', label: '알림 보내기', icon: 'BellAlertIcon', description: '푸시/이메일 알림 발송', defaultConfig: { channel: 'push', message: '' } },
  { kind: 'AUTO_PUBLISH', type: 'action', label: '자동 게시', icon: 'RocketLaunchIcon', description: '지정된 플랫폼에 자동 게시', defaultConfig: { platforms: [] } },
  { kind: 'CROSS_POST', type: 'action', label: '크로스 포스팅', icon: 'ArrowsRightLeftIcon', description: '다른 플랫폼에 동시 게시', defaultConfig: { targetPlatforms: [] } },
  { kind: 'GENERATE_METADATA', type: 'action', label: 'AI 메타 생성', icon: 'SparklesIcon', description: 'AI로 제목/설명/태그 자동 생성', defaultConfig: { fields: ['title', 'description', 'tags'] } },
  { kind: 'ADD_TAG', type: 'action', label: '태그 추가', icon: 'TagIcon', description: '자동으로 태그 추가', defaultConfig: { tags: [] } },
  { kind: 'MOVE_STATUS', type: 'action', label: '상태 변경', icon: 'ArrowPathIcon', description: '영상 상태 자동 변경', defaultConfig: { targetStatus: 'PUBLISHED' } },
  { kind: 'AI_THUMBNAIL', type: 'action', label: 'AI 썸네일', icon: 'PhotoIcon', description: 'AI로 썸네일 자동 생성', defaultConfig: { style: 'modern' } },
]

// ─── Templates ────────────────────────────────────────
export const WORKFLOW_TEMPLATES: WfTemplate[] = [
  {
    id: 'auto-cross-post',
    name: '자동 크로스 포스팅',
    description: '영상 업로드 시 모든 플랫폼에 자동 게시',
    nodes: [
      { type: 'trigger', kind: 'VIDEO_UPLOADED', label: '영상 업로드', position: { x: 100, y: 200 }, config: {}, ports: [{ id: 'p-out', type: 'output', nodeId: '' }] },
      { type: 'action', kind: 'CROSS_POST', label: '크로스 포스팅', position: { x: 500, y: 200 }, config: { targetPlatforms: ['youtube', 'tiktok', 'instagram'] }, ports: [{ id: 'p-in', type: 'input', nodeId: '' }] },
    ],
    connections: [{ sourceNodeId: '0', sourcePortId: 'p-out', targetNodeId: '1', targetPortId: 'p-in' }],
  },
  {
    id: 'smart-metadata',
    name: 'AI 스마트 메타데이터',
    description: '영상 업로드 후 AI로 메타데이터 자동 생성',
    nodes: [
      { type: 'trigger', kind: 'VIDEO_UPLOADED', label: '영상 업로드', position: { x: 100, y: 200 }, config: {}, ports: [{ id: 'p-out', type: 'output', nodeId: '' }] },
      { type: 'action', kind: 'GENERATE_METADATA', label: 'AI 메타 생성', position: { x: 500, y: 200 }, config: { fields: ['title', 'description', 'tags'] }, ports: [{ id: 'p-in', type: 'input', nodeId: '' }] },
    ],
    connections: [{ sourceNodeId: '0', sourcePortId: 'p-out', targetNodeId: '1', targetPortId: 'p-in' }],
  },
  {
    id: 'milestone-notify',
    name: '마일스톤 알림',
    description: '조회수 또는 구독자 목표 달성 시 알림',
    nodes: [
      { type: 'trigger', kind: 'ANALYTICS_MILESTONE', label: '성과 마일스톤', position: { x: 100, y: 200 }, config: { metric: 'views', threshold: 10000 }, ports: [{ id: 'p-out', type: 'output', nodeId: '' }] },
      { type: 'condition', kind: 'PLATFORM', label: '플랫폼 필터', position: { x: 350, y: 200 }, config: { platforms: ['youtube'] }, ports: [{ id: 'p-in', type: 'input', nodeId: '' }, { id: 'p-out', type: 'output', nodeId: '' }] },
      { type: 'action', kind: 'SEND_NOTIFICATION', label: '알림 보내기', position: { x: 600, y: 200 }, config: { channel: 'push', message: '축하합니다! 목표를 달성했습니다!' }, ports: [{ id: 'p-in', type: 'input', nodeId: '' }] },
    ],
    connections: [
      { sourceNodeId: '0', sourcePortId: 'p-out', targetNodeId: '1', targetPortId: 'p-in' },
      { sourceNodeId: '1', sourcePortId: 'p-out', targetNodeId: '2', targetPortId: 'p-in' },
    ],
  },
  {
    id: 'shorts-auto-publish',
    name: '숏폼 자동 게시',
    description: '60초 이하 영상을 자동으로 숏폼 플랫폼에 게시',
    nodes: [
      { type: 'trigger', kind: 'VIDEO_UPLOADED', label: '영상 업로드', position: { x: 100, y: 200 }, config: {}, ports: [{ id: 'p-out', type: 'output', nodeId: '' }] },
      { type: 'condition', kind: 'DURATION', label: '60초 이하', position: { x: 350, y: 200 }, config: { operator: 'LTE', seconds: 60 }, ports: [{ id: 'p-in', type: 'input', nodeId: '' }, { id: 'p-out', type: 'output', nodeId: '' }] },
      { type: 'action', kind: 'AUTO_PUBLISH', label: '자동 게시', position: { x: 600, y: 200 }, config: { platforms: ['tiktok', 'instagram', 'youtube_shorts'] }, ports: [{ id: 'p-in', type: 'input', nodeId: '' }] },
    ],
    connections: [
      { sourceNodeId: '0', sourcePortId: 'p-out', targetNodeId: '1', targetPortId: 'p-in' },
      { sourceNodeId: '1', sourcePortId: 'p-out', targetNodeId: '2', targetPortId: 'p-in' },
    ],
  },
]

// ─── Helper ───────────────────────────────────────────
let _nextId = 1
function uid(): string {
  return `node-${Date.now()}-${_nextId++}`
}

function createPorts(type: WfNodeType, nodeId: string): WfPort[] {
  const ports: WfPort[] = []
  if (type === 'condition' || type === 'action') {
    ports.push({ id: `${nodeId}-in`, type: 'input', nodeId })
  }
  if (type === 'trigger' || type === 'condition') {
    ports.push({ id: `${nodeId}-out`, type: 'output', nodeId })
  }
  return ports
}

// ─── Store ────────────────────────────────────────────
interface WorkflowBuilderState {
  workflows: WfWorkflow[]
  currentWorkflow: WfWorkflow | null
  nodes: WfNode[]
  connections: WfConnection[]
  selectedNodeId: string | null
  loading: boolean
  isDirty: boolean
  zoom: number
  panOffset: { x: number; y: number }
  executionLogs: WfExecutionLog[]
}

export const useWorkflowBuilderStore = defineStore('workflowBuilder', {
  state: (): WorkflowBuilderState => ({
    workflows: [],
    currentWorkflow: null,
    nodes: [],
    connections: [],
    selectedNodeId: null,
    loading: false,
    isDirty: false,
    zoom: 1,
    panOffset: { x: 0, y: 0 },
    executionLogs: [],
  }),

  getters: {
    selectedNode: (state): WfNode | null => {
      if (!state.selectedNodeId) return null
      return state.nodes.find(n => n.id === state.selectedNodeId) ?? null
    },

    triggerNodes: (state): WfNode[] => {
      return state.nodes.filter(n => n.type === 'trigger')
    },

    conditionNodes: (state): WfNode[] => {
      return state.nodes.filter(n => n.type === 'condition')
    },

    actionNodes: (state): WfNode[] => {
      return state.nodes.filter(n => n.type === 'action')
    },

    activeWorkflows: (state): WfWorkflow[] => {
      return state.workflows.filter(w => w.isEnabled)
    },

    nodeConnections: (state) => {
      return (nodeId: string): WfConnection[] => {
        return state.connections.filter(
          c => c.sourceNodeId === nodeId || c.targetNodeId === nodeId
        )
      }
    },
  },

  actions: {
    // ─── Workflow CRUD ─────────────────────────────────
    async fetchWorkflows() {
      this.loading = true
      try {
        this.workflows = await workflowBuilderApi.list()
      } catch {
        useNotificationStore().error('워크플로우 목록을 불러오지 못했습니다')
        this.workflows = []
      } finally {
        this.loading = false
      }
    },

    async saveWorkflow() {
      if (!this.currentWorkflow) return
      try {
        const payload = {
          name: this.currentWorkflow.name,
          description: this.currentWorkflow.description,
          nodes: this.nodes,
          connections: this.connections,
          isEnabled: this.currentWorkflow.isEnabled,
        }
        if (this.currentWorkflow.id > 0) {
          const updated = await workflowBuilderApi.update(this.currentWorkflow.id, payload)
          const idx = this.workflows.findIndex(w => w.id === updated.id)
          if (idx !== -1) this.workflows[idx] = updated
          this.currentWorkflow = updated
        } else {
          const created = await workflowBuilderApi.create(payload)
          this.workflows.push(created)
          this.currentWorkflow = created
        }
        this.isDirty = false
        useNotificationStore().success('워크플로우가 저장되었습니다')
      } catch {
        useNotificationStore().error('워크플로우 저장에 실패했습니다')
      }
    },

    async deleteWorkflow(id: number) {
      try {
        await workflowBuilderApi.delete(id)
        this.workflows = this.workflows.filter(w => w.id !== id)
        if (this.currentWorkflow?.id === id) {
          this.currentWorkflow = null
          this.nodes = []
          this.connections = []
        }
      } catch {
        useNotificationStore().error('워크플로우 삭제에 실패했습니다')
      }
    },

    async toggleWorkflow(id: number) {
      try {
        const updated = await workflowBuilderApi.toggle(id)
        const idx = this.workflows.findIndex(w => w.id === updated.id)
        if (idx !== -1) this.workflows[idx] = updated
        if (this.currentWorkflow?.id === id) {
          this.currentWorkflow = updated
        }
      } catch {
        useNotificationStore().error('워크플로우 상태 변경에 실패했습니다')
        // local fallback
        const wf = this.workflows.find(w => w.id === id)
        if (wf) wf.isEnabled = !wf.isEnabled
      }
    },

    async testRunWorkflow() {
      if (!this.currentWorkflow || this.currentWorkflow.id <= 0) return
      try {
        const log = await workflowBuilderApi.testRun(this.currentWorkflow.id)
        this.executionLogs.unshift(log)
        useNotificationStore().success('테스트 실행이 완료되었습니다')
      } catch {
        useNotificationStore().error('테스트 실행에 실패했습니다')
      }
    },

    async fetchExecutionLogs(workflowId: number) {
      try {
        this.executionLogs = await workflowBuilderApi.getExecutions(workflowId)
      } catch {
        this.executionLogs = []
      }
    },

    // ─── Canvas state ─────────────────────────────────
    loadWorkflow(workflow: WfWorkflow) {
      this.currentWorkflow = { ...workflow }
      this.nodes = [...workflow.nodes]
      this.connections = [...workflow.connections]
      this.selectedNodeId = null
      this.isDirty = false
      this.zoom = 1
      this.panOffset = { x: 0, y: 0 }
    },

    createNewWorkflow() {
      this.currentWorkflow = {
        id: 0,
        name: '새 워크플로우',
        description: '',
        nodes: [],
        connections: [],
        isEnabled: false,
        executionCount: 0,
        lastExecutedAt: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      this.nodes = []
      this.connections = []
      this.selectedNodeId = null
      this.isDirty = false
    },

    loadTemplate(template: WfTemplate) {
      this.createNewWorkflow()
      if (this.currentWorkflow) {
        this.currentWorkflow.name = template.name
        this.currentWorkflow.description = template.description
      }
      const idMap: Record<string, string> = {}
      this.nodes = template.nodes.map((n, i) => {
        const nodeId = uid()
        idMap[String(i)] = nodeId
        return {
          ...n,
          id: nodeId,
          ports: createPorts(n.type, nodeId),
        }
      })
      this.connections = template.connections.map(c => ({
        ...c,
        id: uid(),
        sourceNodeId: idMap[c.sourceNodeId] ?? c.sourceNodeId,
        targetNodeId: idMap[c.targetNodeId] ?? c.targetNodeId,
        sourcePortId: `${idMap[c.sourceNodeId]}-out`,
        targetPortId: `${idMap[c.targetNodeId]}-in`,
      }))
      this.isDirty = true
    },

    // ─── Node operations ──────────────────────────────
    addNode(kind: WfNodeKind, position: { x: number; y: number }) {
      const def = NODE_DEFINITIONS.find(d => d.kind === kind)
      if (!def) return
      const nodeId = uid()
      const node: WfNode = {
        id: nodeId,
        type: def.type,
        kind,
        label: def.label,
        position: { ...position },
        config: { ...def.defaultConfig },
        ports: createPorts(def.type, nodeId),
      }
      this.nodes.push(node)
      this.selectedNodeId = nodeId
      this.isDirty = true
    },

    removeNode(nodeId: string) {
      this.nodes = this.nodes.filter(n => n.id !== nodeId)
      this.connections = this.connections.filter(
        c => c.sourceNodeId !== nodeId && c.targetNodeId !== nodeId
      )
      if (this.selectedNodeId === nodeId) {
        this.selectedNodeId = null
      }
      this.isDirty = true
    },

    updateNodePosition(nodeId: string, position: { x: number; y: number }) {
      const node = this.nodes.find(n => n.id === nodeId)
      if (node) {
        node.position = { ...position }
        this.isDirty = true
      }
    },

    updateNodeConfig(nodeId: string, config: Record<string, unknown>) {
      const node = this.nodes.find(n => n.id === nodeId)
      if (node) {
        node.config = { ...config }
        this.isDirty = true
      }
    },

    updateNodeLabel(nodeId: string, label: string) {
      const node = this.nodes.find(n => n.id === nodeId)
      if (node) {
        node.label = label
        this.isDirty = true
      }
    },

    selectNode(nodeId: string | null) {
      this.selectedNodeId = nodeId
    },

    // ─── Connection operations ────────────────────────
    addConnection(sourceNodeId: string, targetNodeId: string) {
      const sourceNode = this.nodes.find(n => n.id === sourceNodeId)
      const targetNode = this.nodes.find(n => n.id === targetNodeId)
      if (!sourceNode || !targetNode) return

      const sourcePort = sourceNode.ports.find(p => p.type === 'output')
      const targetPort = targetNode.ports.find(p => p.type === 'input')
      if (!sourcePort || !targetPort) return

      // Prevent duplicate connections
      const exists = this.connections.some(
        c => c.sourceNodeId === sourceNodeId && c.targetNodeId === targetNodeId
      )
      if (exists) return

      this.connections.push({
        id: uid(),
        sourceNodeId,
        sourcePortId: sourcePort.id,
        targetNodeId,
        targetPortId: targetPort.id,
      })
      this.isDirty = true
    },

    removeConnection(connectionId: string) {
      this.connections = this.connections.filter(c => c.id !== connectionId)
      this.isDirty = true
    },

    // ─── Zoom/Pan ─────────────────────────────────────
    setZoom(zoom: number) {
      this.zoom = Math.max(0.25, Math.min(2, zoom))
    },

    setPanOffset(offset: { x: number; y: number }) {
      this.panOffset = { ...offset }
    },

    resetView() {
      this.zoom = 1
      this.panOffset = { x: 0, y: 0 }
    },
  },
})
