<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { ElMessageBox, ElNotification } from 'element-plus';
import {
  acceptWorkOrder,
  assignWorkOrderHandler,
  fetchCompanies,
  fetchAdminHandlers,
  fetchAdminOverview,
  fetchAdminUsers,
  fetchDepartments,
  fetchTeams,
  fetchAdminWorkOrderStatistics,
  fetchAdminWorkOrders,
  fetchFileJob,
  downloadImportErrorReport,
  downloadFileJobResult,
  downloadUserImportTemplate,
  exportAdminWorkOrders,
  importUsers,
  returnWorkOrderToProcessing,
  submitWorkOrderForConfirmation,
  updateAdminUserOrganization,
  updateAdminUserEnabled,
  updateAdminUserRole,
  updateDepartmentAdmin,
  type AdminHandler,
  type AdminUser,
  type OrganizationItem,
  type WorkOrderStatistics,
} from './api/admin';
import { checkBackend, checkDatabase, type HealthStatus } from './api/health';
import {
  cancelWorkOrder,
  confirmWorkOrder,
  createWorkOrderComment,
  createWorkOrder,
  deleteWorkOrderComment,
  fetchWorkOrderAttachments,
  fetchWorkOrderComments,
  fetchWorkOrderDetail,
  fetchWorkOrderLogs,
  fetchWorkOrders,
  updateWorkOrder,
  uploadWorkOrderAttachment,
  workOrderAttachmentDownloadUrl,
  type WorkOrder,
  type WorkOrderAttachment,
  type WorkOrderComment,
  type WorkOrderOperationLog,
} from './api/workOrders';
import {
  changePassword,
  getCurrentUser,
  loginUser,
  logoutUser,
  registerUser,
  updateProfile,
  type CurrentUser,
} from './api/auth';
import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationItem,
} from './api/notifications';
import { createRealtimeClient, type RealtimeEvent } from './api/realtime';

interface StatusItem {
  label: string;
  status: HealthStatus;
}

type View = 'register' | 'login' | 'workOrders' | 'workOrderDetail' | 'profile' | 'admin';

type Priority = '低' | '中' | '高';

const statuses = reactive<Record<'frontend' | 'backend' | 'database', StatusItem>>({
  frontend: { label: '前端运行正常', status: 'ok' },
  backend: { label: '后端连接检查中', status: 'checking' },
  database: { label: '数据库连接检查中', status: 'checking' },
});

const registerForm = reactive({
  username: '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
  companyId: undefined as number | undefined,
  departmentId: undefined as number | undefined,
  teamId: undefined as number | undefined,
});
const loginForm = reactive({ username: '', password: '' });
const profileForm = reactive({ nickname: '' });
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' });
const workOrderForm = reactive({ title: '', description: '', type: '', priority: '中' as Priority });
const editWorkOrderForm = reactive({ title: '', description: '', type: '', priority: '中' as Priority });
const commentForm = reactive({ content: '' });
const workOrderFilters = reactive({
  keyword: '',
  status: '',
  priority: '',
  sort: 'createdAtDesc' as 'createdAtDesc' | 'createdAtAsc',
});
const adminUserFilters = reactive({ keyword: '' });
const adminWorkOrderFilters = reactive({
  keyword: '',
  status: '',
  priority: '',
  creatorId: undefined as number | undefined,
  handlerId: undefined as number | undefined,
  createdFrom: '',
  createdTo: '',
  sort: 'createdAtDesc' as 'createdAtDesc' | 'createdAtAsc',
});
const adminStatisticsFilters = reactive({
  createdFrom: '',
  createdTo: '',
});
const assignmentForm = reactive({ handlerId: undefined as number | undefined });

const currentView = ref<View>('login');
const currentUser = ref<CurrentUser | null>(null);
const workOrders = ref<WorkOrder[]>([]);
const workOrderTotal = ref(0);
const workOrderPage = ref(1);
const workOrderPageSize = ref(10);
const workOrderTotalPages = ref(0);
const adminWorkOrders = ref<WorkOrder[]>([]);
const adminUsers = ref<AdminUser[]>([]);
const adminHandlers = ref<AdminHandler[]>([]);
const companies = ref<OrganizationItem[]>([]);
const departments = ref<OrganizationItem[]>([]);
const teams = ref<OrganizationItem[]>([]);
const workOrderStatistics = ref<WorkOrderStatistics | null>(null);
const notifications = ref<NotificationItem[]>([]);
const unreadNotificationCount = ref(0);
const adminUserTotal = ref(0);
const adminUserPage = ref(1);
const adminUserPageSize = ref(10);
const adminUserTotalPages = ref(0);
const adminWorkOrderTotal = ref(0);
const adminWorkOrderPage = ref(1);
const adminWorkOrderPageSize = ref(10);
const adminWorkOrderTotalPages = ref(0);
const selectedWorkOrder = ref<WorkOrder | null>(null);
const operationLogs = ref<WorkOrderOperationLog[]>([]);
const workOrderComments = ref<WorkOrderComment[]>([]);
const workOrderAttachments = ref<WorkOrderAttachment[]>([]);
const registerError = ref('');
const loginError = ref('');
const successMessage = ref('');
const workOrderError = ref('');
const workOrderMessage = ref('');
const detailError = ref('');
const adminError = ref('');
const adminMessage = ref('');
const profileError = ref('');
const profileMessage = ref('');
const passwordError = ref('');
const isSubmitting = ref(false);
const isWorkOrdersLoading = ref(false);
const isDetailLoading = ref(false);
const isAdminUsersLoading = ref(false);
const isAdminWorkOrdersLoading = ref(false);
const isAdminHandlersLoading = ref(false);
const isExcelWorking = ref(false);
const userImportInput = ref<HTMLInputElement | null>(null);
const isWorkOrderStatisticsLoading = ref(false);
const isNotificationsLoading = ref(false);
const isNotificationsOpen = ref(false);
const lastImportErrorJobId = ref<number | null>(null);
const isOperationLogsLoading = ref(false);
const isCommentsLoading = ref(false);
const isAttachmentsLoading = ref(false);
const isWorkOrderActionSubmitting = ref(false);
const isWorkOrderCreateSubmitting = ref(false);
const isCommentSubmitting = ref(false);
const isAttachmentSubmitting = ref(false);
const isEditingWorkOrder = ref(false);
const workOrderCreateIdempotencyKey = ref(newIdempotencyKey());
const workOrdersLoaded = ref(false);
const adminLoaded = ref(false);
const realtimeClient = ref<ReturnType<typeof createRealtimeClient> | null>(null);
const statusItems = computed(() => [statuses.frontend, statuses.backend, statuses.database]);
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN');
function hasPermission(permission: string) {
  if (currentUser.value?.permissions?.includes(permission)) return true;
  if (currentUser.value?.role === 'ADMIN') {
    return [
      'ticket:create',
      'ticket:view',
      'ticket:update',
      'ticket:cancel',
      'ticket:comment',
      'ticket:attachment',
      'ticket:assign',
      'ticket:accept',
      'ticket:submit',
      'ticket:return',
      'ticket:confirm',
      'ticket:log:view',
      'user:view',
      'user:update',
      'user:disable',
      'organization:manage',
      'statistics:view',
    ].includes(permission);
  }
  return [
    'ticket:create',
    'ticket:view',
    'ticket:update',
    'ticket:cancel',
    'ticket:comment',
    'ticket:attachment',
    'ticket:log:view',
    'ticket:confirm',
  ].includes(permission);
}
const canOpenAdmin = computed(() =>
  hasPermission('user:view') ||
  hasPermission('ticket:assign') ||
  hasPermission('ticket:accept') ||
  hasPermission('ticket:submit') ||
  hasPermission('ticket:return') ||
  hasPermission('statistics:view') ||
  hasPermission('organization:manage'),
);
const canManageSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  const user = currentUser.value;
  return Boolean(workOrder && user && hasPermission('ticket:update') && workOrder.status === '待处理' && (isAdmin.value || workOrder.creatorId === user.id));
});
const canAssignSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && hasPermission('ticket:assign') && workOrder.status === '待处理');
});
const selectedAssignmentHandler = computed(() => adminHandlers.value.find((handler) => handler.id === assignmentForm.handlerId));
const canAcceptSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && hasPermission('ticket:accept') && workOrder.status === '待处理' && (!workOrder.handlerId || workOrder.handlerId === currentUser.value?.id));
});
const canSubmitSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && hasPermission('ticket:submit') && workOrder.status === '处理中' && workOrder.handlerId === currentUser.value?.id);
});
const canReturnSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && hasPermission('ticket:return') && workOrder.status === '待确认' && workOrder.handlerId === currentUser.value?.id);
});
const canConfirmSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && currentUser.value && hasPermission('ticket:confirm') && workOrder.status === '待确认' && workOrder.creatorId === currentUser.value.id);
});
const canCancelSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && currentUser.value && hasPermission('ticket:cancel') && workOrder.status === '待处理' && workOrder.creatorId === currentUser.value.id);
});
const canCommentSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && currentUser.value && hasPermission('ticket:comment') && workOrder.status !== '已取消');
});
const pageTitle = computed(() => {
  if (currentView.value === 'admin') return '管理页面';
  if (currentView.value === 'profile') return '个人资料';
  if (currentView.value === 'workOrderDetail') return '工单详情';
  return '工单列表';
});
const hasWorkOrderStatistics = computed(() => Boolean(workOrderStatistics.value && workOrderStatistics.value.total > 0));
const statusStatisticMax = computed(() => Math.max(1, ...(workOrderStatistics.value?.statusCounts.map((item) => item.count) ?? [])));
const priorityStatisticMax = computed(() => Math.max(1, ...(workOrderStatistics.value?.priorityCounts.map((item) => item.count) ?? [])));
const dailyStatisticMax = computed(() => Math.max(1, ...(workOrderStatistics.value?.dailyNewCounts.map((item) => item.count) ?? [])));
const adminStatisticMax = computed(() => Math.max(1, ...(workOrderStatistics.value?.adminProcessingCounts.map((item) => item.count) ?? [])));
const slaPriorityStatisticMax = computed(() => Math.max(1, ...(workOrderStatistics.value?.slaOverduePriorityCounts?.map((item) => item.count) ?? [])));

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value || '无时间';
  return date.toLocaleString('zh-CN', { hour12: false });
}

function formatDurationMinutes(minutes: number) {
  if (!minutes) return '0 分钟';
  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  if (hours === 0) return `${restMinutes} 分钟`;
  if (restMinutes === 0) return `${hours} 小时`;
  return `${hours} 小时 ${restMinutes} 分钟`;
}

function statisticBarWidth(count: number, max: number) {
  return `${Math.round((count / max) * 100)}%`;
}

function formatFileSize(size: number) {
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
  if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${size} B`;
}

function operationActionLabel(action: string) {
  const labels: Record<string, string> = {
    create: '\u521b\u5efa\u5de5\u5355',
    update: '\u4fee\u6539\u5de5\u5355',
    assign_handler: '\u5206\u914d\u5904\u7406\u4eba',
    accept: '\u63a5\u5355',
    submit: '\u63d0\u4ea4\u786e\u8ba4',
    return: '\u9000\u56de\u5904\u7406\u4e2d',
    confirm: '\u786e\u8ba4\u5b8c\u6210',
    cancel: '\u53d6\u6d88\u5de5\u5355',
    comment_add: '\u6dfb\u52a0\u8bc4\u8bba',
    comment_update: '\u4fee\u6539\u8bc4\u8bba',
    comment_delete: '\u5220\u9664\u8bc4\u8bba',
    attachment_add: '\u6dfb\u52a0\u9644\u4ef6',
    attachment_delete: '\u5220\u9664\u9644\u4ef6',
  };
  return labels[action] || action;
}

function operationFieldLabel(fieldName?: string | null) {
  const labels: Record<string, string> = {
    title: '\u6807\u9898',
    description: '\u63cf\u8ff0',
    type: '\u7c7b\u578b',
    priority: '\u4f18\u5148\u7ea7',
    handler: '\u5904\u7406\u4eba',
    status: '\u72b6\u6001',
    comment: '\u8bc4\u8bba',
    attachment: '\u9644\u4ef6',
  };
  return fieldName ? labels[fieldName] || fieldName : '';
}

function operationActor(log: WorkOrderOperationLog) {
  return log.actorNickname ? `${log.actorNickname}\uff08${log.actorUsername}\uff09` : log.actorUsername;
}

function roleLabel(role: string) {
  const labels: Record<string, string> = {
    ADMIN: '\u7ba1\u7406\u5458',
    USER: '\u666e\u901a\u7528\u6237',
    CUSTOMER_SERVICE: '\u5ba2\u670d\u4eba\u5458',
    DEPARTMENT_ADMIN: '\u90e8\u95e8\u7ba1\u7406\u5458',
    AUDITOR: '\u5ba1\u8ba1\u4eba\u5458',
  };
  return labels[role] || role;
}

function slaStatusLabel(status?: string | null) {
  const labels: Record<string, string> = {
    NORMAL: '\u0053\u004c\u0041 \u6b63\u5e38',
    NEAR_OVERDUE: '\u0053\u004c\u0041 \u5373\u5c06\u8d85\u65f6',
    FIRST_RESPONSE_OVERDUE: '\u9996\u6b21\u54cd\u5e94\u8d85\u65f6',
    RESOLUTION_OVERDUE: '\u89e3\u51b3\u8d85\u65f6',
    COMPLETED: '\u0053\u004c\u0041 \u5df2\u5b8c\u6210',
  };
  return labels[status || 'NORMAL'] || status || '\u0053\u004c\u0041 \u6b63\u5e38';
}

function slaSummary(workOrder: WorkOrder) {
  const firstResponse = workOrder.firstResponseDueAt ? `\u9996\u54cd ${formatTime(workOrder.firstResponseDueAt)}` : '\u9996\u54cd\u672a\u8bbe\u7f6e';
  const resolution = workOrder.resolutionDueAt ? `\u89e3\u51b3 ${formatTime(workOrder.resolutionDueAt)}` : '\u89e3\u51b3\u672a\u8bbe\u7f6e';
  return `${slaStatusLabel(workOrder.slaStatus)} \u00b7 ${firstResponse} \u00b7 ${resolution}`;
}
async function loadUnreadNotifications() {
  if (!currentUser.value) {
    unreadNotificationCount.value = 0;
    return;
  }
  unreadNotificationCount.value = await fetchUnreadNotificationCount().catch(() => 0);
}

async function loadNotifications() {
  if (!currentUser.value) return;
  isNotificationsLoading.value = true;
  try {
    const response = await fetchNotifications();
    notifications.value = response.items;
    await loadUnreadNotifications();
  } finally {
    isNotificationsLoading.value = false;
  }
}

function startRealtime() {
  if (!currentUser.value || realtimeClient.value) return;
  realtimeClient.value = createRealtimeClient({
    onEvent: handleRealtimeEvent,
    onAuthExpired: handleRealtimeAuthExpired,
  });
  realtimeClient.value.start();
}

function stopRealtime() {
  realtimeClient.value?.stop();
  realtimeClient.value = null;
}

function handleRealtimeEvent(event: RealtimeEvent) {
  if (!currentUser.value) return;
  if (typeof event.unreadCount === 'number') {
    unreadNotificationCount.value = event.unreadCount;
  }
  if (event.type === 'NOTIFICATION_CREATED') {
    handleRealtimeNotification(event);
    return;
  }
  if (event.type === 'UNREAD_COUNT_CHANGED') {
    return;
  }
  if (event.type === 'AUTH_CONTEXT_CHANGED') {
    void refreshAuthContext();
    return;
  }
  if (['WORK_ORDER_CREATED', 'WORK_ORDER_ASSIGNED', 'WORK_ORDER_STATUS_CHANGED', 'COMMENT_CREATED'].includes(event.type)) {
    void refreshRealtimeWorkOrderState(event);
  }
}

function handleRealtimeNotification(event: RealtimeEvent) {
  if (isNotificationsOpen.value) {
    void loadNotifications();
  }
  const title = typeof event.payload?.title === 'string' ? event.payload.title : '新通知';
  const content = typeof event.payload?.content === 'string' ? event.payload.content : '';
  ElNotification({
    title,
    message: content,
    type: event.payload?.notificationType === 'WORK_ORDER_ASSIGNED' ? 'success' : 'info',
    duration: 5000,
    onClick: () => {
      if (event.entityId) {
        void openWorkOrderDetail(event.entityId);
      }
    },
  });
}

async function refreshRealtimeWorkOrderState(event: RealtimeEvent) {
  if (currentView.value === 'workOrders' && workOrdersLoaded.value && !isWorkOrdersLoading.value) {
    await openWorkOrders();
  }
  if (currentView.value === 'admin' && adminLoaded.value && !isAdminWorkOrdersLoading.value && hasPermission('ticket:assign')) {
    await loadAdminWorkOrders();
  }
  if (event.entityId && selectedWorkOrder.value?.id === event.entityId && !isDetailLoading.value) {
    try {
      selectedWorkOrder.value = await fetchWorkOrderDetail(event.entityId);
      assignmentForm.handlerId = selectedWorkOrder.value.handlerId ?? undefined;
      await Promise.all([loadComments(event.entityId), loadOperationLogs(event.entityId)]);
    } catch {
      selectedWorkOrder.value = null;
      detailError.value = '当前工单已不可见或已被更新，请返回列表刷新';
    }
  }
  if (hasPermission('statistics:view') && currentView.value === 'admin') {
    await loadWorkOrderStatistics().catch(() => undefined);
  }
}

async function refreshAuthContext() {
  try {
    currentUser.value = await getCurrentUser();
    if (!currentUser.value) {
      await handleRealtimeAuthExpired();
      return;
    }
    profileForm.nickname = currentUser.value.nickname;
    await Promise.all([
      loadUnreadNotifications(),
      currentView.value === 'workOrders' ? openWorkOrders() : Promise.resolve(),
      currentView.value === 'admin' && canOpenAdmin.value ? openAdmin() : Promise.resolve(),
      selectedWorkOrder.value ? openWorkOrderDetail(selectedWorkOrder.value.id) : Promise.resolve(),
    ]);
  } catch {
    await handleRealtimeAuthExpired();
  }
}

async function handleRealtimeAuthExpired() {
  stopRealtime();
  currentUser.value = null;
  workOrders.value = [];
  adminWorkOrders.value = [];
  selectedWorkOrder.value = null;
  notifications.value = [];
  unreadNotificationCount.value = 0;
  currentView.value = 'login';
  loginError.value = '登录状态已过期，请重新登录';
}

async function toggleNotifications() {
  isNotificationsOpen.value = !isNotificationsOpen.value;
  if (isNotificationsOpen.value) {
    await loadNotifications();
  }
}

async function openNotification(notification: NotificationItem) {
  if (!notification.read) {
    await markNotificationRead(notification.id).catch(() => undefined);
    await loadUnreadNotifications();
  }
  isNotificationsOpen.value = false;
  if (notification.workOrderId) {
    await openWorkOrderDetail(notification.workOrderId);
  }
}

async function markAllNotificationsReadAction() {
  await markAllNotificationsRead();
  notifications.value = notifications.value.map((item) => ({ ...item, read: true }));
  unreadNotificationCount.value = 0;
}

const currentRoleLabels = computed(() => {
  const roles = currentUser.value?.roles?.length ? currentUser.value.roles : currentUser.value?.role ? [currentUser.value.role] : [];
  return roles.join(' / ');
});

function organizationLabel(item: { departmentName?: string | null; teamName?: string | null; companyName?: string | null }) {
  const parts = [item.companyName, item.departmentName, item.teamName].filter(Boolean);
  return parts.length > 0 ? parts.join(' / ') : '未确认组织';
}

function orgStatusLabel(orgConfirmed?: boolean) {
  return orgConfirmed ? '已确认' : '待管理员确认';
}

async function loadOrganizations() {
  try {
    companies.value = await fetchCompanies();
    departments.value = await fetchDepartments(registerForm.companyId);
    teams.value = await fetchTeams(registerForm.departmentId);
  } catch {
    companies.value = [];
    departments.value = [];
    teams.value = [];
  }
}

async function changeRegisterCompany() {
  registerForm.departmentId = undefined;
  registerForm.teamId = undefined;
  departments.value = await fetchDepartments(registerForm.companyId).catch(() => []);
  teams.value = [];
}

async function changeRegisterDepartment() {
  registerForm.teamId = undefined;
  teams.value = await fetchTeams(registerForm.departmentId).catch(() => []);
}

function operationChangeText(log: WorkOrderOperationLog) {
  const field = operationFieldLabel(log.fieldName);
  if (log.oldValue != null && log.newValue != null) return `${field}\uff1a${log.oldValue} \u2192 ${log.newValue}`;
  if (log.newValue != null && field) return `${field}\uff1a${log.newValue}`;
  if (log.newValue != null) return log.newValue;
  return field;
}

function validateRegisterForm() {
  const username = registerForm.username.trim();
  const nickname = registerForm.nickname.trim();
  const email = registerForm.email.trim();
  if (username.length < 4 || username.length > 30) return '用户名长度必须为 4 到 30 个字符';
  if (!nickname) return '昵称不能为空';
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return '邮箱格式不正确';
  if (registerForm.password.length < 8) return '密码长度至少 8 位';
  if (registerForm.password !== registerForm.confirmPassword) return '两次输入的密码不一致';
  return '';
}

function toPriority(value: string): Priority {
  return ['低', '中', '高'].includes(value) ? (value as Priority) : '中';
}

async function submitRegister() {
  registerError.value = '';
  successMessage.value = '';
  const validationMessage = validateRegisterForm();
  if (validationMessage) {
    registerError.value = validationMessage;
    return;
  }
  isSubmitting.value = true;
  try {
    await registerUser({
      username: registerForm.username.trim(),
      nickname: registerForm.nickname.trim(),
      email: registerForm.email.trim() || undefined,
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword,
      companyId: registerForm.companyId,
      departmentId: registerForm.departmentId,
      teamId: registerForm.teamId,
    });
    successMessage.value = registerForm.email.trim() ? '注册成功，验证码邮件已进入发送队列，请登录后完成邮箱验证' : '注册成功，请登录';
    currentView.value = 'login';
    loginForm.username = registerForm.username.trim();
  } catch (error) {
    registerError.value = error instanceof Error ? error.message : '注册失败，请稍后再试';
  } finally {
    isSubmitting.value = false;
  }
}

async function submitLogin() {
  loginError.value = '';
  successMessage.value = '';
  if (!loginForm.username.trim() || !loginForm.password) {
    loginError.value = '请输入用户名和密码';
    return;
  }
  isSubmitting.value = true;
  try {
    currentUser.value = await loginUser({ username: loginForm.username.trim(), password: loginForm.password });
    profileForm.nickname = currentUser.value.nickname;
    startRealtime();
    await Promise.all([openWorkOrders(), loadUnreadNotifications()]);
  } catch (error) {
    currentUser.value = null;
    loginError.value = error instanceof Error ? error.message : '用户名或密码错误';
  } finally {
    isSubmitting.value = false;
  }
}

async function openWorkOrders(options: { resetPage?: boolean } = {}) {
  if (!currentUser.value) {
    currentView.value = 'login';
    return;
  }
  if (options.resetPage) {
    workOrderPage.value = 1;
  }
  currentView.value = 'workOrders';
  isWorkOrdersLoading.value = true;
  workOrderError.value = '';
  workOrderMessage.value = '';
  detailError.value = '';
  adminError.value = '';
  selectedWorkOrder.value = null;
  operationLogs.value = [];
  workOrderComments.value = [];
  workOrderAttachments.value = [];
  commentForm.content = '';
  isEditingWorkOrder.value = false;
  try {
    const response = await fetchWorkOrders({
      keyword: workOrderFilters.keyword,
      status: workOrderFilters.status,
      priority: workOrderFilters.priority,
      sort: workOrderFilters.sort,
      page: workOrderPage.value,
      pageSize: workOrderPageSize.value,
    });
    workOrders.value = response.items;
    workOrderTotal.value = response.total;
    workOrderPage.value = response.page;
    workOrderPageSize.value = response.pageSize;
    workOrderTotalPages.value = response.totalPages;
    workOrdersLoaded.value = true;
  } catch (error) {
    workOrdersLoaded.value = false;
    workOrders.value = [];
    workOrderTotal.value = 0;
    workOrderTotalPages.value = 0;
    workOrderError.value = error instanceof Error ? error.message : '获取工单列表失败';
  } finally {
    isWorkOrdersLoading.value = false;
  }
}

function applyWorkOrderFilters() {
  void openWorkOrders({ resetPage: true });
}

function changeWorkOrderPage(page: number) {
  workOrderPage.value = page;
  void openWorkOrders();
}

function changeWorkOrderPageSize(pageSize: number) {
  workOrderPageSize.value = pageSize;
  void openWorkOrders({ resetPage: true });
}

function optionalPositiveNumber(value: number | undefined) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : undefined;
}

async function loadWorkOrderStatistics() {
  if (!currentUser.value || !hasPermission('statistics:view')) {
    currentView.value = 'login';
    return;
  }
  isWorkOrderStatisticsLoading.value = true;
  adminError.value = '';
  try {
    workOrderStatistics.value = await fetchAdminWorkOrderStatistics({
      createdFrom: adminStatisticsFilters.createdFrom,
      createdTo: adminStatisticsFilters.createdTo,
    });
  } catch (error) {
    workOrderStatistics.value = null;
    adminError.value = error instanceof Error ? error.message : '获取工单统计失败';
  } finally {
    isWorkOrderStatisticsLoading.value = false;
  }
}

function applyStatisticsFilters() {
  void loadWorkOrderStatistics();
}

async function loadAdminHandlers() {
  if (!currentUser.value || !(hasPermission('ticket:assign') || hasPermission('ticket:accept') || hasPermission('ticket:submit') || hasPermission('ticket:return'))) {
    adminHandlers.value = [];
    return;
  }
  isAdminHandlersLoading.value = true;
  try {
    adminHandlers.value = await fetchAdminHandlers();
  } catch (error) {
    adminHandlers.value = [];
    adminError.value = error instanceof Error ? error.message : '获取处理人列表失败';
  } finally {
    isAdminHandlersLoading.value = false;
  }
}

async function loadAdminUsers(options: { resetPage?: boolean } = {}) {
  if (!currentUser.value || !hasPermission('user:view')) {
    currentView.value = 'login';
    return;
  }
  if (options.resetPage) {
    adminUserPage.value = 1;
  }
  isAdminUsersLoading.value = true;
  adminError.value = '';
  try {
    const response = await fetchAdminUsers({
      keyword: adminUserFilters.keyword,
      page: adminUserPage.value,
      pageSize: adminUserPageSize.value,
    });
    adminUsers.value = response.items;
    adminUserTotal.value = response.total;
    adminUserPage.value = response.page;
    adminUserPageSize.value = response.pageSize;
    adminUserTotalPages.value = response.totalPages;
  } catch (error) {
    adminUsers.value = [];
    adminUserTotal.value = 0;
    adminUserTotalPages.value = 0;
    adminError.value = error instanceof Error ? error.message : '获取用户列表失败';
  } finally {
    isAdminUsersLoading.value = false;
  }
}

function applyAdminUserFilters() {
  void loadAdminUsers({ resetPage: true });
}

async function downloadUserTemplate() {
  isExcelWorking.value = true;
  adminError.value = '';
  try {
    await downloadUserImportTemplate();
    adminMessage.value = '用户导入模板已下载';
  } catch (error) {
    adminError.value = error instanceof Error ? error.message : '下载用户导入模板失败';
  } finally {
    isExcelWorking.value = false;
  }
}

async function uploadUserImport(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  isExcelWorking.value = true;
  adminError.value = '';
  try {
    const job = await importUsers(file);
    lastImportErrorJobId.value = job.hasErrorReport ? job.id : null;
    adminMessage.value = `导入完成：成功 ${job.successCount} 条，失败 ${job.failedCount} 条`;
    await Promise.all([loadAdminUsers({ resetPage: true }), loadAdminHandlers()]);
  } catch (error) {
    adminError.value = error instanceof Error ? error.message : '导入用户失败';
  } finally {
    isExcelWorking.value = false;
  }
}

async function downloadLastImportErrorReport() {
  if (!lastImportErrorJobId.value) return;
  isExcelWorking.value = true;
  adminError.value = '';
  try {
    await downloadImportErrorReport(lastImportErrorJobId.value);
    adminMessage.value = '错误报告已下载';
  } catch (error) {
    adminError.value = error instanceof Error ? error.message : '下载错误报告失败';
  } finally {
    isExcelWorking.value = false;
  }
}

function changeAdminUserPage(page: number) {
  adminUserPage.value = page;
  void loadAdminUsers();
}

function changeAdminUserPageSize(pageSize: number) {
  adminUserPageSize.value = pageSize;
  void loadAdminUsers({ resetPage: true });
}

function isCurrentAdminUser(user: AdminUser) {
  return currentUser.value?.id === user.id;
}

async function setAdminUserEnabled(user: AdminUser, enabled: boolean) {
  if (isCurrentAdminUser(user) && !enabled) return;
  try {
    await ElMessageBox.confirm(
      `确定${enabled ? '启用' : '禁用'}用户「${user.nickname}（${user.username}）」吗？`,
      enabled ? '启用用户' : '禁用用户',
      {
        confirmButtonText: enabled ? '确认启用' : '确认禁用',
        cancelButtonText: '返回',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  isAdminUsersLoading.value = true;
  adminError.value = '';
  try {
    await updateAdminUserEnabled(user.id, enabled);
    await Promise.all([loadAdminUsers(), loadAdminHandlers()]);
  } catch (error) {
    adminError.value = error instanceof Error ? error.message : '更新用户状态失败';
  } finally {
    isAdminUsersLoading.value = false;
  }
}

async function setAdminUserRole(user: AdminUser, role: 'USER' | 'ADMIN') {
  if (isCurrentAdminUser(user) && role === 'USER') return;
  const nextRoleLabel = roleLabel(role);
  try {
    await ElMessageBox.confirm(
      `确定将用户「${user.nickname}（${user.username}）」的角色修改为「${nextRoleLabel}」吗？`,
      '修改用户角色',
      {
        confirmButtonText: '确认修改',
        cancelButtonText: '返回',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  isAdminUsersLoading.value = true;
  adminError.value = '';
  try {
    await updateAdminUserRole(user.id, role);
    await Promise.all([loadAdminUsers(), loadAdminHandlers()]);
  } catch (error) {
    adminError.value = error instanceof Error ? error.message : '更新用户角色失败';
  } finally {
    isAdminUsersLoading.value = false;
  }
}

async function setAdminUserOrganizationConfirmed(user: AdminUser, orgConfirmed: boolean) {
  if (orgConfirmed && !user.departmentId) {
    adminError.value = '用户还没有申请部门，不能确认组织归属';
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定${orgConfirmed ? '确认' : '取消确认'}用户「${user.nickname}（${user.username}）」的组织归属吗？`,
      orgConfirmed ? '确认组织归属' : '取消组织确认',
      {
        confirmButtonText: orgConfirmed ? '确认归属' : '取消确认',
        cancelButtonText: '返回',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  isAdminUsersLoading.value = true;
  adminError.value = '';
  try {
    await updateAdminUserOrganization(user.id, {
      companyId: user.companyId ?? null,
      departmentId: user.departmentId ?? null,
      teamId: user.teamId ?? null,
      orgConfirmed,
    });
    await Promise.all([loadAdminUsers(), loadAdminHandlers()]);
  } catch (error) {
    adminError.value = error instanceof Error ? error.message : '更新用户组织归属失败';
  } finally {
    isAdminUsersLoading.value = false;
  }
}

async function setUserDepartmentAdmin(user: AdminUser, departmentAdmin: boolean) {
  if (!user.departmentId) {
    adminError.value = '用户还没有部门，不能授权部门管理员';
    return;
  }
  if (departmentAdmin && !user.orgConfirmed) {
    adminError.value = '请先确认用户组织归属，再授权部门管理员';
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定${departmentAdmin ? '授权' : '取消'}用户「${user.nickname}（${user.username}）」的部门管理员权限吗？`,
      departmentAdmin ? '授权部门管理员' : '取消部门管理员',
      {
        confirmButtonText: departmentAdmin ? '确认授权' : '取消授权',
        cancelButtonText: '返回',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  isAdminUsersLoading.value = true;
  adminError.value = '';
  try {
    await updateDepartmentAdmin(user.id, user.departmentId, departmentAdmin);
    await Promise.all([loadAdminUsers(), loadAdminHandlers()]);
  } catch (error) {
    adminError.value = error instanceof Error ? error.message : '更新部门管理员授权失败';
  } finally {
    isAdminUsersLoading.value = false;
  }
}

async function loadAdminWorkOrders(options: { resetPage?: boolean } = {}) {
  if (!currentUser.value || !hasPermission('ticket:assign')) {
    currentView.value = 'login';
    return;
  }
  if (options.resetPage) {
    adminWorkOrderPage.value = 1;
  }
  isAdminWorkOrdersLoading.value = true;
  adminError.value = '';
  try {
    if (hasPermission('ticket:assign') && adminHandlers.value.length === 0) {
      await loadAdminHandlers();
    }
    const response = await fetchAdminWorkOrders({
      keyword: adminWorkOrderFilters.keyword,
      status: adminWorkOrderFilters.status,
      priority: adminWorkOrderFilters.priority,
      creatorId: optionalPositiveNumber(adminWorkOrderFilters.creatorId),
      handlerId: optionalPositiveNumber(adminWorkOrderFilters.handlerId),
      createdFrom: adminWorkOrderFilters.createdFrom,
      createdTo: adminWorkOrderFilters.createdTo,
      sort: adminWorkOrderFilters.sort,
      page: adminWorkOrderPage.value,
      pageSize: adminWorkOrderPageSize.value,
    });
    adminWorkOrders.value = response.items;
    adminWorkOrderTotal.value = response.total;
    adminWorkOrderPage.value = response.page;
    adminWorkOrderPageSize.value = response.pageSize;
    adminWorkOrderTotalPages.value = response.totalPages;
    adminLoaded.value = true;
  } catch (error) {
    adminLoaded.value = false;
    adminWorkOrders.value = [];
    adminWorkOrderTotal.value = 0;
    adminWorkOrderTotalPages.value = 0;
    adminError.value = error instanceof Error ? error.message : '获取管理员工单列表失败';
  } finally {
    isAdminWorkOrdersLoading.value = false;
  }
}

function applyAdminWorkOrderFilters() {
  void loadAdminWorkOrders({ resetPage: true });
}

async function exportFilteredAdminWorkOrders() {
  isExcelWorking.value = true;
  adminError.value = '';
  try {
    const job = await exportAdminWorkOrders({
      keyword: adminWorkOrderFilters.keyword,
      status: adminWorkOrderFilters.status,
      priority: adminWorkOrderFilters.priority,
      creatorId: optionalPositiveNumber(adminWorkOrderFilters.creatorId),
      handlerId: optionalPositiveNumber(adminWorkOrderFilters.handlerId),
      createdFrom: adminWorkOrderFilters.createdFrom,
      createdTo: adminWorkOrderFilters.createdTo,
      sort: adminWorkOrderFilters.sort,
    });
    adminMessage.value = `导出任务已创建：#${job.id}`;
    const finishedJob = await waitForFileJob(job.id);
    if (finishedJob.status === 'SUCCESS') {
      await downloadFileJobResult(finishedJob.id, `work-orders-${finishedJob.id}.xlsx`);
      adminMessage.value = '工单导出文件已下载';
    } else {
      throw new Error(finishedJob.errorMessage || '导出工单失败');
    }
  } catch (error) {
    adminError.value = error instanceof Error ? error.message : '导出工单失败';
  } finally {
    isExcelWorking.value = false;
  }
}

async function waitForFileJob(jobId: number) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const job = await fetchFileJob(jobId);
    if (job.status === 'SUCCESS' || job.status === 'FAILED') {
      return job;
    }
    await new Promise((resolve) => window.setTimeout(resolve, 1000));
  }
  throw new Error('导出任务正在后台处理中，请稍后刷新后重试下载');
}

function changeAdminWorkOrderPage(page: number) {
  adminWorkOrderPage.value = page;
  void loadAdminWorkOrders();
}

function changeAdminWorkOrderPageSize(pageSize: number) {
  adminWorkOrderPageSize.value = pageSize;
  void loadAdminWorkOrders({ resetPage: true });
}

async function openWorkOrderDetail(id: number) {
  if (!currentUser.value) {
    currentView.value = 'login';
    return;
  }
  currentView.value = 'workOrderDetail';
  isDetailLoading.value = true;
  detailError.value = '';
  workOrderError.value = '';
  selectedWorkOrder.value = null;
  operationLogs.value = [];
  workOrderComments.value = [];
  workOrderAttachments.value = [];
  commentForm.content = '';
  isEditingWorkOrder.value = false;
  try {
    selectedWorkOrder.value = await fetchWorkOrderDetail(id);
    assignmentForm.handlerId = selectedWorkOrder.value.handlerId ?? undefined;
    await Promise.all([loadOperationLogs(id), loadComments(id), loadAttachments(id)]);
    if (hasPermission('ticket:assign') && adminHandlers.value.length === 0) {
      await loadAdminHandlers();
    }
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '获取工单详情失败';
  } finally {
    isDetailLoading.value = false;
  }
}

async function loadOperationLogs(id = selectedWorkOrder.value?.id) {
  if (!id) return;
  isOperationLogsLoading.value = true;
  try {
    operationLogs.value = await fetchWorkOrderLogs(id);
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '\u83b7\u53d6\u5de5\u5355\u65e5\u5fd7\u5931\u8d25';
    operationLogs.value = [];
  } finally {
    isOperationLogsLoading.value = false;
  }
}

async function loadComments(id = selectedWorkOrder.value?.id) {
  if (!id) return;
  isCommentsLoading.value = true;
  try {
    workOrderComments.value = await fetchWorkOrderComments(id);
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '\u83b7\u53d6\u5de5\u5355\u8bc4\u8bba\u5931\u8d25';
    workOrderComments.value = [];
  } finally {
    isCommentsLoading.value = false;
  }
}

async function loadAttachments(id = selectedWorkOrder.value?.id) {
  if (!id) return;
  isAttachmentsLoading.value = true;
  try {
    workOrderAttachments.value = await fetchWorkOrderAttachments(id);
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '\u83b7\u53d6\u5de5\u5355\u9644\u4ef6\u5931\u8d25';
    workOrderAttachments.value = [];
  } finally {
    isAttachmentsLoading.value = false;
  }
}

async function submitHandlerAssignment() {
  const workOrder = selectedWorkOrder.value;
  const handler = selectedAssignmentHandler.value;
  if (!workOrder || !canAssignSelectedWorkOrder.value || !assignmentForm.handlerId || !handler) return;

  const oldHandler = workOrder.handlerUsername || '未分配';
  const newHandler = `${handler.nickname}（${handler.username}）`;
  try {
      await ElMessageBox.confirm(
      `确定将工单「${workOrder.title}」的处理人从「${oldHandler}」分配为「${newHandler}」吗？`,
      '分配处理人',
      {
        confirmButtonText: '确认分配',
        cancelButtonText: '返回',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  detailError.value = '';
  workOrderMessage.value = '';
  isWorkOrderActionSubmitting.value = true;
  try {
    const updated = await assignWorkOrderHandler(workOrder.id, assignmentForm.handlerId);
    applyUpdatedWorkOrder(updated);
    await Promise.all([loadOperationLogs(updated.id), loadWorkOrderStatistics()]);
    workOrderMessage.value = '处理人分配成功';
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '分配处理人失败';
  } finally {
    isWorkOrderActionSubmitting.value = false;
  }
}

async function submitComment() {
  const workOrder = selectedWorkOrder.value;
  if (!workOrder || !canCommentSelectedWorkOrder.value) return;
  const content = commentForm.content.trim();
  detailError.value = '';
  workOrderMessage.value = '';
  if (!content) {
    detailError.value = '\u8bc4\u8bba\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a';
    return;
  }
  isCommentSubmitting.value = true;
  try {
    await createWorkOrderComment(workOrder.id, { content });
    commentForm.content = '';
    await Promise.all([loadComments(workOrder.id), loadOperationLogs(workOrder.id)]);
    workOrderMessage.value = '\u8bc4\u8bba\u5df2\u53d1\u5e03';
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '\u6dfb\u52a0\u8bc4\u8bba\u5931\u8d25';
  } finally {
    isCommentSubmitting.value = false;
  }
}

async function deleteComment(commentId: number) {
  const workOrder = selectedWorkOrder.value;
  if (!workOrder || !hasPermission('ticket:assign')) return;
  try {
    await ElMessageBox.confirm('\u786e\u5b9a\u5220\u9664\u8fd9\u6761\u8bc4\u8bba\u5417\uff1f', '\u5220\u9664\u8bc4\u8bba', {
      confirmButtonText: '\u786e\u5b9a',
      cancelButtonText: '\u8fd4\u56de',
      type: 'warning',
    });
  } catch {
    return;
  }
  detailError.value = '';
  workOrderMessage.value = '';
  isCommentSubmitting.value = true;
  try {
    await deleteWorkOrderComment(workOrder.id, commentId);
    await Promise.all([loadComments(workOrder.id), loadOperationLogs(workOrder.id)]);
    workOrderMessage.value = '\u8bc4\u8bba\u5df2\u5220\u9664';
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '\u5220\u9664\u8bc4\u8bba\u5931\u8d25';
  } finally {
    isCommentSubmitting.value = false;
  }
}

async function submitAttachment(event: Event) {
  const workOrder = selectedWorkOrder.value;
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!workOrder || !file) return;
  detailError.value = '';
  workOrderMessage.value = '';
  isAttachmentSubmitting.value = true;
  try {
    await uploadWorkOrderAttachment(workOrder.id, file);
    await Promise.all([loadAttachments(workOrder.id), loadOperationLogs(workOrder.id)]);
    workOrderMessage.value = '\u9644\u4ef6\u5df2\u4e0a\u4f20';
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '\u4e0a\u4f20\u9644\u4ef6\u5931\u8d25';
  } finally {
    isAttachmentSubmitting.value = false;
  }
}

function applyUpdatedWorkOrder(updated: WorkOrder) {
  selectedWorkOrder.value = updated;
  assignmentForm.handlerId = updated.handlerId ?? undefined;
  adminWorkOrders.value = adminWorkOrders.value.map((item) => (item.id === updated.id ? updated : item));
  workOrders.value = workOrders.value.map((item) => (item.id === updated.id ? updated : item));
}

async function runStateAction(
  confirmMessage: string,
  confirmTitle: string,
  success: string,
  action: () => Promise<WorkOrder>,
) {
  try {
    await ElMessageBox.confirm(confirmMessage, confirmTitle, {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      type: 'warning',
    });
  } catch {
    return;
  }

  detailError.value = '';
  workOrderMessage.value = '';
  isWorkOrderActionSubmitting.value = true;
  try {
    const updated = await action();
    applyUpdatedWorkOrder(updated);
    await Promise.all([loadOperationLogs(updated.id), hasPermission('statistics:view') ? loadWorkOrderStatistics() : Promise.resolve()]);
    isEditingWorkOrder.value = false;
    workOrderMessage.value = success;
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : success.replace('成功', '失败');
  } finally {
    isWorkOrderActionSubmitting.value = false;
  }
}

function submitAcceptWorkOrder() {
  const workOrder = selectedWorkOrder.value;
  if (!workOrder || !canAcceptSelectedWorkOrder.value) return;
  void runStateAction(`确定接单「${workOrder.title}」吗？`, '管理员接单', '接单成功', () => acceptWorkOrder(workOrder.id));
}

function submitProcessingComplete() {
  const workOrder = selectedWorkOrder.value;
  if (!workOrder || !canSubmitSelectedWorkOrder.value) return;
  void runStateAction(`确定将「${workOrder.title}」提交给创建人确认吗？`, '处理完成', '已提交确认', () =>
    submitWorkOrderForConfirmation(workOrder.id),
  );
}

function submitReturnToProcessing() {
  const workOrder = selectedWorkOrder.value;
  if (!workOrder || !canReturnSelectedWorkOrder.value) return;
  void runStateAction(`确定将「${workOrder.title}」退回处理中吗？`, '退回处理中', '已退回处理中', () =>
    returnWorkOrderToProcessing(workOrder.id),
  );
}

function submitCompletionConfirmation() {
  const workOrder = selectedWorkOrder.value;
  if (!workOrder || !canConfirmSelectedWorkOrder.value) return;
  void runStateAction(`确定确认「${workOrder.title}」已完成吗？`, '确认完成', '工单已完成', () => confirmWorkOrder(workOrder.id));
}

async function submitWorkOrder() {
  if (isWorkOrderCreateSubmitting.value) return;
  workOrderError.value = '';
  workOrderMessage.value = '';
  if (!workOrderForm.title.trim()) {
    workOrderError.value = '标题不能为空';
    return;
  }
  if (!workOrderForm.description.trim()) {
    workOrderError.value = '详细描述不能为空';
    return;
  }
  if (!['低', '中', '高'].includes(workOrderForm.priority)) {
    workOrderError.value = '优先级只能是低、中、高';
    return;
  }
  isWorkOrderCreateSubmitting.value = true;
  try {
    const created = await createWorkOrder({
      title: workOrderForm.title.trim(),
      description: workOrderForm.description.trim(),
      type: workOrderForm.type.trim(),
      priority: workOrderForm.priority,
      idempotencyKey: workOrderCreateIdempotencyKey.value,
    });
    workOrderForm.title = '';
    workOrderForm.description = '';
    workOrderForm.type = '';
    workOrderForm.priority = '中';
    workOrderCreateIdempotencyKey.value = newIdempotencyKey();
    workOrderMessage.value = '工单创建成功';
    await openWorkOrderDetail(created.id);
  } catch (error) {
    workOrderError.value = error instanceof Error ? error.message : '创建工单失败';
  } finally {
    isWorkOrderCreateSubmitting.value = false;
  }
}

function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `wo-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function startEditingWorkOrder() {
  if (!selectedWorkOrder.value || !canManageSelectedWorkOrder.value) return;
  editWorkOrderForm.title = selectedWorkOrder.value.title;
  editWorkOrderForm.description = selectedWorkOrder.value.description;
  editWorkOrderForm.type = selectedWorkOrder.value.type;
  editWorkOrderForm.priority = toPriority(selectedWorkOrder.value.priority);
  detailError.value = '';
  workOrderMessage.value = '';
  isEditingWorkOrder.value = true;
}

function stopEditingWorkOrder() {
  isEditingWorkOrder.value = false;
  detailError.value = '';
}

async function submitWorkOrderEdit() {
  const workOrder = selectedWorkOrder.value;
  if (!workOrder || !canManageSelectedWorkOrder.value) return;
  detailError.value = '';
  workOrderMessage.value = '';
  if (!editWorkOrderForm.title.trim()) {
    detailError.value = '标题不能为空';
    return;
  }
  if (!editWorkOrderForm.description.trim()) {
    detailError.value = '详细描述不能为空';
    return;
  }
  if (!editWorkOrderForm.type.trim()) {
    detailError.value = '工单类型不能为空';
    return;
  }

  isWorkOrderActionSubmitting.value = true;
  try {
    const updated = await updateWorkOrder(workOrder.id, {
      title: editWorkOrderForm.title.trim(),
      description: editWorkOrderForm.description.trim(),
      type: editWorkOrderForm.type.trim(),
      priority: editWorkOrderForm.priority,
    });
    applyUpdatedWorkOrder(updated);
    await loadOperationLogs(updated.id);
    isEditingWorkOrder.value = false;
    workOrderMessage.value = '工单修改成功';
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '修改工单失败';
  } finally {
    isWorkOrderActionSubmitting.value = false;
  }
}

async function submitWorkOrderCancellation() {
  const workOrder = selectedWorkOrder.value;
  if (!workOrder || !canCancelSelectedWorkOrder.value) return;
  try {
    await ElMessageBox.confirm('取消后将不能再修改该工单，确定继续吗？', '取消工单', {
      confirmButtonText: '确定取消',
      cancelButtonText: '返回',
      type: 'warning',
    });
  } catch {
    return;
  }

  detailError.value = '';
  workOrderMessage.value = '';
  isWorkOrderActionSubmitting.value = true;
  try {
    const updated = await cancelWorkOrder(workOrder.id);
    applyUpdatedWorkOrder(updated);
    await Promise.all([loadOperationLogs(updated.id), hasPermission('statistics:view') ? loadWorkOrderStatistics() : Promise.resolve()]);
    isEditingWorkOrder.value = false;
    workOrderMessage.value = '工单已取消';
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '取消工单失败';
  } finally {
    isWorkOrderActionSubmitting.value = false;
  }
}

function openProfile() {
  if (!currentUser.value) {
    currentView.value = 'login';
    return;
  }
  adminError.value = '';
  currentView.value = 'profile';
}

async function openAdmin() {
  if (!currentUser.value || !canOpenAdmin.value) {
    currentView.value = 'login';
    return;
  }
  adminError.value = '';
  try {
    await fetchAdminOverview();
    currentView.value = 'admin';
    await Promise.all([
      hasPermission('organization:manage') ? loadOrganizations() : Promise.resolve(),
      hasPermission('statistics:view') ? loadWorkOrderStatistics() : Promise.resolve(),
      hasPermission('user:view') ? loadAdminUsers() : Promise.resolve(),
      hasPermission('ticket:assign') ? loadAdminHandlers() : Promise.resolve(),
      hasPermission('ticket:assign') || hasPermission('ticket:accept') || hasPermission('ticket:submit') || hasPermission('ticket:return') ? loadAdminWorkOrders() : Promise.resolve(),
    ]);
  } catch (error) {
    adminLoaded.value = false;
    adminError.value = error instanceof Error ? error.message : '获取管理页面失败';
  }
}

async function submitProfile() {
  profileError.value = '';
  profileMessage.value = '';
  if (!profileForm.nickname.trim()) {
    profileError.value = '昵称不能为空';
    return;
  }
  try {
    currentUser.value = await updateProfile({ nickname: profileForm.nickname.trim() });
    profileForm.nickname = currentUser.value.nickname;
    profileMessage.value = '资料已更新';
  } catch (error) {
    profileError.value = error instanceof Error ? error.message : '修改资料失败';
  }
}

async function submitPassword() {
  passwordError.value = '';
  successMessage.value = '';
  if (passwordForm.newPassword.length < 8) {
    passwordError.value = '新密码长度至少 8 位';
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致';
    return;
  }
  try {
    await changePassword({ ...passwordForm });
    stopRealtime();
    currentUser.value = null;
    workOrdersLoaded.value = false;
    adminLoaded.value = false;
    currentView.value = 'login';
    loginForm.password = '';
    passwordForm.currentPassword = '';
    passwordForm.newPassword = '';
    passwordForm.confirmPassword = '';
    successMessage.value = '密码已修改，请重新登录';
  } catch (error) {
    passwordError.value = error instanceof Error ? error.message : '修改密码失败';
  }
}

async function logout() {
  stopRealtime();
  await logoutUser();
  currentUser.value = null;
  workOrders.value = [];
  selectedWorkOrder.value = null;
  operationLogs.value = [];
  workOrderComments.value = [];
  workOrderAttachments.value = [];
  workOrderStatistics.value = null;
  notifications.value = [];
  unreadNotificationCount.value = 0;
  isNotificationsOpen.value = false;
  commentForm.content = '';
  workOrdersLoaded.value = false;
  adminLoaded.value = false;
  currentView.value = 'login';
  successMessage.value = '已退出登录';
}

async function restoreLoginState() {
  try {
    currentUser.value = await getCurrentUser();
    if (currentUser.value) {
      profileForm.nickname = currentUser.value.nickname;
      startRealtime();
      await Promise.all([openWorkOrders(), loadUnreadNotifications()]);
    }
  } catch {
    currentUser.value = null;
    currentView.value = 'login';
  }
}

async function refreshHealth() {
  statuses.backend.status = 'checking';
  statuses.backend.label = '后端连接检查中';
  statuses.database.status = 'checking';
  statuses.database.label = '数据库连接检查中';
  try {
    const backend = await checkBackend();
    statuses.backend.status = backend.status === 'ok' ? 'ok' : 'error';
    statuses.backend.label = statuses.backend.status === 'ok' ? '后端连接正常' : '后端连接异常';
  } catch {
    statuses.backend.status = 'error';
    statuses.backend.label = '后端连接异常';
  }
  try {
    const database = await checkDatabase();
    statuses.database.status = database.status === 'ok' && database.validation === 1 ? 'ok' : 'error';
    statuses.database.label = statuses.database.status === 'ok' ? '数据库连接正常' : '数据库连接异常';
  } catch {
    statuses.database.status = 'error';
    statuses.database.label = '数据库连接异常';
  }
}

onMounted(() => {
  void refreshHealth();
  void restoreLoginState();
  void loadOrganizations();
});

onUnmounted(() => {
  stopRealtime();
});
</script>

<template>
  <el-config-provider>
    <main class="app-shell">
      <section class="status-panel">
        <p class="eyebrow">Work Order System</p>
        <h1>工单管理系统</h1>
        <p class="summary">开发环境连通性检</p>
        <ul class="status-list" aria-label="系统连接状">
          <li v-for="item in statusItems" :key="item.label" class="status-item" :class="`status-${item.status}`">
            <span class="status-icon" aria-hidden="true"></span>
            <span>{{ item.label }}</span>
          </li>
        </ul>
        <el-button type="primary" @click="refreshHealth">重新检</el-button>
      </section>

      <section class="auth-panel">
        <template v-if="currentView === 'register'">
          <p class="eyebrow">Account</p>
          <h2>用户注册</h2>
          <el-alert v-if="registerError" :title="registerError" type="error" show-icon :closable="false" />
          <el-form class="auth-form" label-position="top" @submit.prevent="submitRegister">
            <el-form-item label="用户"><el-input v-model="registerForm.username" maxlength="30" placeholder="请输入 4 到 30 个字符" /></el-form-item>
            <el-form-item label="昵称"><el-input v-model="registerForm.nickname" maxlength="60" placeholder="请输入昵称" /></el-form-item>
            <el-form-item label="邮箱"><el-input v-model="registerForm.email" maxlength="160" placeholder="用于接收验证码和工单邮件" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="registerForm.password" type="password" show-password placeholder="至少 8 位" /></el-form-item>
            <el-form-item label="确认密码"><el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="请再次输入密码" /></el-form-item>
            <el-form-item label="申请公司">
              <el-select v-model="registerForm.companyId" clearable placeholder="可选，需管理员确认" @change="changeRegisterCompany">
                <el-option v-for="company in companies" :key="company.id" :label="company.name" :value="company.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="申请部门">
              <el-select v-model="registerForm.departmentId" clearable placeholder="可选，需管理员确认" @change="changeRegisterDepartment">
                <el-option v-for="department in departments" :key="department.id" :label="department.name" :value="department.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="申请团队">
              <el-select v-model="registerForm.teamId" clearable placeholder="可选，需管理员确">
                <el-option v-for="team in teams" :key="team.id" :label="team.name" :value="team.id" />
              </el-select>
            </el-form-item>
            <div class="form-actions"><el-button native-type="submit" type="primary" :loading="isSubmitting">注册</el-button><el-button @click="currentView = 'login'">去登录</el-button></div>
          </el-form>
        </template>

        <template v-else-if="currentView === 'login'">
          <p class="eyebrow">Account</p>
          <h2>用户登录</h2>
          <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon :closable="false" />
          <el-alert v-if="loginError || workOrderError" :title="loginError || workOrderError" type="error" show-icon :closable="false" />
          <el-form class="auth-form" label-position="top" @submit.prevent="submitLogin">
            <el-form-item label="用户"><el-input v-model="loginForm.username" placeholder="请输入用户名" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" /></el-form-item>
            <div class="form-actions"><el-button native-type="submit" type="primary" :loading="isSubmitting">登录</el-button><el-button @click="currentView = 'register'">去注册</el-button></div>
          </el-form>
        </template>

        <template v-else>
          <div class="protected-header">
            <div>
              <p class="eyebrow">{{ currentView === 'admin' ? 'Admin' : currentView === 'profile' ? 'Profile' : 'Work Orders' }}</p>
              <h2>{{ pageTitle }}</h2>
            </div>
            <el-button @click="logout">退出登</el-button>
          </div>

                    <nav class="role-menu" aria-label="功能菜单">
            <el-button :type="currentView === 'workOrders' || currentView === 'workOrderDetail' ? 'primary' : 'default'" @click="openWorkOrders">工单</el-button>
            <el-button :type="currentView === 'profile' ? 'primary' : 'default'" @click="openProfile">个人资料</el-button>
            <el-button v-if="canOpenAdmin" :type="currentView === 'admin' ? 'primary' : 'default'" @click="openAdmin">管理</el-button>
            <el-button :type="isNotificationsOpen ? 'primary' : 'default'" @click="toggleNotifications">
              通知<span v-if="unreadNotificationCount > 0">（{{ unreadNotificationCount }}）</span>
            </el-button>
          </nav>

          <section v-if="isNotificationsOpen" class="profile-section notification-panel">
            <div class="section-header">
              <h3>站内通知</h3>
              <el-button size="small" :disabled="notifications.length === 0" @click="markAllNotificationsReadAction">全部已读</el-button>
            </div>
            <p v-if="isNotificationsLoading" class="empty-state">通知加载中</p>
            <p v-else-if="notifications.length === 0" class="empty-state">暂无通知</p>
            <div v-else class="notification-list">
              <button
                v-for="notification in notifications"
                :key="notification.id"
                class="notification-item"
                :class="{ unread: !notification.read }"
                type="button"
                @click="openNotification(notification)"
              >
                <span class="item-title">{{ notification.title }}</span>
                <span class="item-meta">{{ notification.content }} · {{ formatTime(notification.createdAt) }}</span>
              </button>
            </div>
          </section>

          <p class="summary">欢迎，{{ currentUser?.nickname }}。当前角色：{{ currentRoleLabels }}。</p>
          <el-alert v-if="adminError" :title="adminError" type="error" show-icon :closable="false" />
          <el-alert v-if="adminMessage" :title="adminMessage" type="success" show-icon :closable="false" />

          <template v-if="currentView === 'admin'">
            <section class="profile-section">
              <div class="section-header">
                <h3>工单统计看板</h3>
                <el-button size="small" :loading="isWorkOrderStatisticsLoading" @click="loadWorkOrderStatistics()">刷新</el-button>
              </div>
              <el-form class="statistics-filters" label-position="top" @submit.prevent="applyStatisticsFilters">
                <el-form-item label="开始日">
                  <el-input v-model="adminStatisticsFilters.createdFrom" type="date" clearable aria-label="统计开始日期" />
                </el-form-item>
                <el-form-item label="结束日期">
                  <el-input v-model="adminStatisticsFilters.createdTo" type="date" clearable aria-label="统计结束日期" />
                </el-form-item>
                <el-button native-type="submit" type="primary" :loading="isWorkOrderStatisticsLoading">应用日期范围</el-button>
              </el-form>
              <p class="item-meta">统计口径：平均处理时长按{{ workOrderStatistics?.averageProcessingRule || '首次接单到用户确认完成' }}；超时按{{ workOrderStatistics?.overdueRule || '状态为待处理且创建时间超过 48 小时' }}。</p>
              <p v-if="isWorkOrderStatisticsLoading" class="empty-text">工单统计加载</p>
              <template v-else-if="workOrderStatistics">
                <div class="statistics-card-grid">
                  <article class="statistics-card">
                    <span>工单总数</span>
                    <strong>{{ workOrderStatistics.total }}</strong>
                  </article>
                  <article class="statistics-card">
                    <span>平均处理时长</span>
                    <strong>{{ formatDurationMinutes(workOrderStatistics.averageProcessingMinutes) }}</strong>
                  </article>
                  <article class="statistics-card warning">
                    <span>超时未处理</span>
                    <strong>{{ workOrderStatistics.overdueUnhandledCount }}</strong>
                  </article>
                  <article class="statistics-card warning">
                    <span>SLA 即将超时</span>
                    <strong>{{ workOrderStatistics.slaNearOverdueCount || 0 }}</strong>
                  </article>
                  <article class="statistics-card warning">
                    <span>首次响应超时</span>
                    <strong>{{ workOrderStatistics.firstResponseOverdueCount || 0 }}</strong>
                  </article>
                  <article class="statistics-card warning">
                    <span>解决超时</span>
                    <strong>{{ workOrderStatistics.resolutionOverdueCount || 0 }}</strong>
                  </article>
                </div>
                <p v-if="!hasWorkOrderStatistics" class="empty-text">当前日期范围内暂无工单统计数据</p>
                <div class="statistics-charts" v-else>
                  <section class="statistics-chart" aria-label="各状态数">
                    <h4>各状态数</h4>
                    <div v-for="item in workOrderStatistics.statusCounts" :key="item.label" class="bar-row">
                      <span>{{ item.label }}</span>
                      <div class="bar-track"><span class="bar-fill" :style="{ width: statisticBarWidth(item.count, statusStatisticMax) }"></span></div>
                      <strong>{{ item.count }}</strong>
                    </div>
                  </section>
                  <section class="statistics-chart" aria-label="各优先级数量">
                    <h4>各优先级数量</h4>
                    <div v-for="item in workOrderStatistics.priorityCounts" :key="item.label" class="bar-row">
                      <span>{{ item.label }}</span>
                      <div class="bar-track"><span class="bar-fill priority" :style="{ width: statisticBarWidth(item.count, priorityStatisticMax) }"></span></div>
                      <strong>{{ item.count }}</strong>
                    </div>
                  </section>
                  <section class="statistics-chart" aria-label="每日新增趋势">
                    <h4>每日新增趋势</h4>
                    <p v-if="workOrderStatistics.dailyNewCounts.length === 0" class="empty-text">暂无每日新增数据</p>
                    <div v-for="item in workOrderStatistics.dailyNewCounts" :key="item.date" class="bar-row">
                      <span>{{ item.date }}</span>
                      <div class="bar-track"><span class="bar-fill trend" :style="{ width: statisticBarWidth(item.count, dailyStatisticMax) }"></span></div>
                      <strong>{{ item.count }}</strong>
                    </div>
                  </section>
                  <section class="statistics-chart" aria-label="各管理员处理数量">
                    <h4>各管理员处理数量</h4>
                    <p v-if="workOrderStatistics.adminProcessingCounts.length === 0" class="empty-text">暂无管理员处理数</p>
                    <div v-for="item in workOrderStatistics.adminProcessingCounts" :key="item.handlerId" class="bar-row">
                      <span>{{ item.handlerNickname }}（{{ item.handlerUsername }}</span>
                      <div class="bar-track"><span class="bar-fill admin" :style="{ width: statisticBarWidth(item.count, adminStatisticMax) }"></span></div>
                      <strong>{{ item.count }}</strong>
                    </div>
                  </section>
                  <section class="statistics-chart" aria-label="SLA 超时优先级">
                    <h4>SLA 超时优先级</h4>
                    <p v-if="(workOrderStatistics.slaOverduePriorityCounts || []).length === 0" class="empty-text">暂无 SLA 超时数据</p>
                    <div v-for="item in workOrderStatistics.slaOverduePriorityCounts || []" :key="item.label" class="bar-row">
                      <span>{{ item.label }}</span>
                      <div class="bar-track"><span class="bar-fill warning" :style="{ width: statisticBarWidth(item.count, slaPriorityStatisticMax) }"></span></div>
                      <strong>{{ item.count }}</strong>
                    </div>
                  </section>
                </div>
              </template>
              <p v-else class="empty-text">暂无统计数据</p>
            </section>

            <section class="profile-section">
              <div class="section-header">
                <h3>用户管理</h3>
                <el-button size="small" @click="loadAdminUsers()">刷新</el-button>
              </div>
              <div class="section-actions">
                <el-button size="small" :loading="isExcelWorking" @click="downloadUserTemplate">下载导入模板</el-button>
                <el-button size="small" :loading="isExcelWorking" @click="userImportInput?.click()">批量导入用户</el-button>
                <el-button
                  v-if="lastImportErrorJobId"
                  size="small"
                  type="warning"
                  :loading="isExcelWorking"
                  @click="downloadLastImportErrorReport"
                >下载错误报告</el-button>
                <input ref="userImportInput" class="visually-hidden" type="file" accept=".xlsx" @change="uploadUserImport" />
              </div>
              <el-form class="admin-user-filters" label-position="top" @submit.prevent="applyAdminUserFilters">
                <el-form-item label="用户名或昵称">
                  <el-input v-model="adminUserFilters.keyword" clearable placeholder="搜索用户名或昵称" @clear="applyAdminUserFilters" />
                </el-form-item>
                <el-button native-type="submit" type="primary">搜索</el-button>
              </el-form>
              <p class="item-meta">共 {{ adminUserTotal }} 个用户，第 {{ adminUserPage }} / {{ adminUserTotalPages || 1 }} 页，每页 {{ adminUserPageSize }} 个</p>
              <p v-if="isAdminUsersLoading" class="empty-state">用户加载</p>
              <p v-else-if="adminUsers.length === 0" class="empty-state">暂无用户</p>
              <div v-else class="user-management-list">
                <article v-for="user in adminUsers" :key="user.id" class="user-management-item">
                  <div>
                    <strong>{{ user.nickname }}（{{ user.username }}）</strong>
                    <span class="item-meta">
                      {{ roleLabel(user.role) }} · {{ user.enabled ? '已启用' : '已禁用' }} · 创建于 {{ formatTime(user.createdAt) }} · 更新于 {{ formatTime(user.updatedAt) }}
                    </span>
                    <span class="item-meta">
                      {{ organizationLabel(user) }} · {{ orgStatusLabel(user.orgConfirmed) }} · {{ user.departmentAdmin ? '部门管理员' : '普通部门成员' }}
                    </span>
                  </div>
                  <div class="detail-actions">
                    <el-button
                      size="small"
                      :type="user.enabled ? 'danger' : 'primary'"
                      :disabled="isCurrentAdminUser(user) && user.enabled"
                      :loading="isAdminUsersLoading"
                      @click="setAdminUserEnabled(user, !user.enabled)"
                    >{{ user.enabled ? '禁用' : '启用' }}</el-button>
                    <el-button
                      v-if="user.role === 'USER'"
                      size="small"
                      :loading="isAdminUsersLoading"
                      @click="setAdminUserRole(user, 'ADMIN')"
                    >设为管理员</el-button>
                    <el-button
                      v-else
                      size="small"
                      :disabled="isCurrentAdminUser(user)"
                      :loading="isAdminUsersLoading"
                      @click="setAdminUserRole(user, 'USER')"
                    >降级为用户</el-button>
                    <el-button
                      size="small"
                      :type="user.orgConfirmed ? 'warning' : 'primary'"
                      :disabled="!user.departmentId"
                      :loading="isAdminUsersLoading"
                      @click="setAdminUserOrganizationConfirmed(user, !user.orgConfirmed)"
                    >{{ user.orgConfirmed ? '取消组织确认' : '确认组织归属' }}</el-button>
                    <el-button
                      size="small"
                      :type="user.departmentAdmin ? 'warning' : 'success'"
                      :disabled="!user.departmentId || !user.orgConfirmed"
                      :loading="isAdminUsersLoading"
                      @click="setUserDepartmentAdmin(user, !user.departmentAdmin)"
                      >{{ user.departmentAdmin ? '取消部门管理员' : '设为部门管理员' }}</el-button>
                  </div>
                </article>
              </div>
              <el-pagination
                v-if="adminUserTotal > 0"
                class="work-order-pagination"
                layout="prev, pager, next, sizes"
                :total="adminUserTotal"
                :current-page="adminUserPage"
                :page-size="adminUserPageSize"
                :page-sizes="[5, 10, 20, 50]"
                @current-change="changeAdminUserPage"
                @size-change="changeAdminUserPageSize"
              />
            </section>

            <section class="profile-section">
              <div class="section-header">
                <h3>&#31649;&#29702;&#21592;&#24037;&#21333;&#21015;&#34920;</h3>
                <el-button size="small" @click="loadAdminWorkOrders">&#21047;&#26032;</el-button>
              </div>
              <div class="section-actions">
                <el-button size="small" type="primary" :loading="isExcelWorking" @click="exportFilteredAdminWorkOrders">导出当前筛选</el-button>
              </div>
              <el-form class="admin-work-order-filters" label-position="top" @submit.prevent="applyAdminWorkOrderFilters">
                <el-form-item label="&#26631;&#39064;&#20851;&#38190;&#23383;">
                  <el-input v-model="adminWorkOrderFilters.keyword" clearable placeholder="&#25628;&#32034;&#26631;&#39064;" @clear="applyAdminWorkOrderFilters" />
                </el-form-item>
                <el-form-item label="&#29366;&#24577;">
                  <el-select v-model="adminWorkOrderFilters.status" clearable placeholder="&#20840;&#37096;&#29366;&#24577;" @change="applyAdminWorkOrderFilters">
                    <el-option label="&#24453;&#22788;&#29702;" value="&#24453;&#22788;&#29702;" />
                    <el-option label="&#22788;&#29702;&#20013;" value="&#22788;&#29702;&#20013;" />
                    <el-option label="&#24453;&#30830;&#35748;" value="&#24453;&#30830;&#35748;" />
                    <el-option label="&#24050;&#21462;&#28040;" value="&#24050;&#21462;&#28040;" />
                    <el-option label="&#24050;&#23436;&#25104;" value="&#24050;&#23436;&#25104;" />
                  </el-select>
                </el-form-item>
                <el-form-item label="&#20248;&#20808;&#32423;">
                  <el-select v-model="adminWorkOrderFilters.priority" clearable placeholder="&#20840;&#37096;&#20248;&#20808;&#32423;" @change="applyAdminWorkOrderFilters">
                    <el-option label="&#20302;" value="&#20302;" />
                    <el-option label="&#20013;" value="&#20013;" />
                    <el-option label="&#39640;" value="&#39640;" />
                  </el-select>
                </el-form-item>
                <el-form-item label="&#21019;&#24314;&#20154; ID">
                  <el-input-number v-model="adminWorkOrderFilters.creatorId" :min="1" :step="1" controls-position="right" placeholder="&#20840;&#37096;" @change="applyAdminWorkOrderFilters" />
                </el-form-item>
                <el-form-item label="&#22788;&#29702;&#20154; ID">
                  <el-input-number v-model="adminWorkOrderFilters.handlerId" :min="1" :step="1" controls-position="right" placeholder="&#20840;&#37096;" @change="applyAdminWorkOrderFilters" />
                </el-form-item>
                <el-form-item label="&#24320;&#22987;&#26085;&#26399;">
                  <el-date-picker v-model="adminWorkOrderFilters.createdFrom" value-format="YYYY-MM-DD" type="date" placeholder="&#24320;&#22987;&#26085;&#26399;" @change="applyAdminWorkOrderFilters" />
                </el-form-item>
                <el-form-item label="&#32467;&#26463;&#26085;&#26399;">
                  <el-date-picker v-model="adminWorkOrderFilters.createdTo" value-format="YYYY-MM-DD" type="date" placeholder="&#32467;&#26463;&#26085;&#26399;" @change="applyAdminWorkOrderFilters" />
                </el-form-item>
                <el-form-item label="&#21019;&#24314;&#26102;&#38388;">
                  <el-select v-model="adminWorkOrderFilters.sort" @change="applyAdminWorkOrderFilters">
                    <el-option label="&#26368;&#26032;&#20248;&#20808;" value="createdAtDesc" />
                    <el-option label="&#26368;&#26089;&#20248;&#20808;" value="createdAtAsc" />
                  </el-select>
                </el-form-item>
                <el-button native-type="submit" type="primary">&#25628;&#32034;</el-button>
              </el-form>
              <p class="item-meta">&#20849; {{ adminWorkOrderTotal }} &#26465;&#65292;&#31532; {{ adminWorkOrderPage }} / {{ adminWorkOrderTotalPages || 1 }} &#39029;&#65292;&#27599;&#39029; {{ adminWorkOrderPageSize }} &#26465;</p>
              <p v-if="isAdminWorkOrdersLoading" class="empty-state">&#24037;&#21333;&#21152;&#36733;&#20013;</p>
              <p v-else-if="adminLoaded && adminWorkOrders.length === 0" class="empty-state">&#26242;&#26080;&#24037;&#21333;</p>
              <div v-else class="work-order-list">
                <button v-for="item in adminWorkOrders" :key="item.id" class="work-order-item" type="button" @click="openWorkOrderDetail(item.id)">
                  <span class="item-title">{{ item.title }}</span>
                  <span class="item-meta">
                    {{ item.status }} &#183; {{ item.priority }} &#183; {{ item.departmentName || '未确认部门' }} &#183; &#21019;&#24314;&#20154; {{ item.creatorUsername }} &#183; &#22788;&#29702;&#20154; {{ item.handlerUsername || '\u672a\u5206\u914d' }} &#183; {{ formatTime(item.createdAt) }}
                  </span>
                  <span class="item-meta">{{ slaSummary(item) }}</span>
                </button>
              </div>
              <el-pagination
                v-if="adminLoaded && adminWorkOrderTotal > 0"
                class="work-order-pagination"
                layout="prev, pager, next, sizes"
                :total="adminWorkOrderTotal"
                :current-page="adminWorkOrderPage"
                :page-size="adminWorkOrderPageSize"
                :page-sizes="[5, 10, 20, 50]"
                @current-change="changeAdminWorkOrderPage"
                @size-change="changeAdminWorkOrderPageSize"
              />
            </section>
          </template>

          <template v-else-if="currentView === 'workOrders'">
            <el-alert v-if="workOrderError" :title="workOrderError" type="error" show-icon :closable="false" />
            <section class="profile-section">
              <div class="section-header">
                <h3>&#24050;&#26377;&#24037;&#21333;</h3>
                <el-button size="small" @click="openWorkOrders">&#21047;&#26032;</el-button>
              </div>
              <el-form class="work-order-filters" label-position="top" @submit.prevent="applyWorkOrderFilters">
                <el-form-item label="&#26631;&#39064;&#20851;&#38190;&#23383;">
                  <el-input v-model="workOrderFilters.keyword" clearable placeholder="&#25628;&#32034;&#26631;&#39064;" @clear="applyWorkOrderFilters" />
                </el-form-item>
                <el-form-item label="&#29366;&#24577;">
                  <el-select v-model="workOrderFilters.status" clearable placeholder="&#20840;&#37096;&#29366;&#24577;" @change="applyWorkOrderFilters">
                    <el-option label="&#24453;&#22788;&#29702;" value="&#24453;&#22788;&#29702;" />
                    <el-option label="&#22788;&#29702;&#20013;" value="&#22788;&#29702;&#20013;" />
                    <el-option label="&#24453;&#30830;&#35748;" value="&#24453;&#30830;&#35748;" />
                    <el-option label="&#24050;&#21462;&#28040;" value="&#24050;&#21462;&#28040;" />
                    <el-option label="&#24050;&#23436;&#25104;" value="&#24050;&#23436;&#25104;" />
                  </el-select>
                </el-form-item>
                <el-form-item label="&#20248;&#20808;&#32423;">
                  <el-select v-model="workOrderFilters.priority" clearable placeholder="&#20840;&#37096;&#20248;&#20808;&#32423;" @change="applyWorkOrderFilters">
                    <el-option label="&#20302;" value="&#20302;" />
                    <el-option label="&#20013;" value="&#20013;" />
                    <el-option label="&#39640;" value="&#39640;" />
                  </el-select>
                </el-form-item>
                <el-form-item label="&#21019;&#24314;&#26102;&#38388;">
                  <el-select v-model="workOrderFilters.sort" @change="applyWorkOrderFilters">
                    <el-option label="&#26368;&#26032;&#20248;&#20808;" value="createdAtDesc" />
                    <el-option label="&#26368;&#26089;&#20248;&#20808;" value="createdAtAsc" />
                  </el-select>
                </el-form-item>
                <el-button native-type="submit" type="primary">&#25628;&#32034;</el-button>
              </el-form>
              <p class="item-meta">&#20849; {{ workOrderTotal }} &#26465;&#65292;&#31532; {{ workOrderPage }} / {{ workOrderTotalPages || 1 }} &#39029;&#65292;&#27599;&#39029; {{ workOrderPageSize }} &#26465;</p>
              <p v-if="isWorkOrdersLoading" class="empty-state">&#24037;&#21333;&#21152;&#36733;&#20013;</p>
              <p v-else-if="workOrdersLoaded && workOrders.length === 0" class="empty-state">&#26242;&#26080;&#24037;&#21333;</p>
              <div v-else class="work-order-list">
                <button v-for="item in workOrders" :key="item.id" class="work-order-item" type="button" @click="openWorkOrderDetail(item.id)">
                  <span class="item-title">{{ item.title }}</span>
                  <span class="item-meta">{{ item.status }} &#183; {{ item.priority }} &#183; {{ item.departmentName || '未确认部门' }} &#183; {{ item.creatorUsername }} &#183; {{ formatTime(item.createdAt) }}</span>
                  <span class="item-meta">{{ slaSummary(item) }}</span>
                </button>
              </div>
              <el-pagination
                v-if="workOrdersLoaded && workOrderTotal > 0"
                class="work-order-pagination"
                layout="prev, pager, next, sizes"
                :total="workOrderTotal"
                :current-page="workOrderPage"
                :page-size="workOrderPageSize"
                :page-sizes="[5, 10, 20, 50]"
                @current-change="changeWorkOrderPage"
                @size-change="changeWorkOrderPageSize"
              />
            </section>

            <section class="profile-section">
              <h3>创建工单</h3>
              <el-form class="auth-form" label-position="top" @submit.prevent="submitWorkOrder">
                <el-form-item label="标题"><el-input v-model="workOrderForm.title" maxlength="120" placeholder="请输入标题" /></el-form-item>
                <el-form-item label="详细描述"><el-input v-model="workOrderForm.description" type="textarea" :rows="4" placeholder="请输入详细描述" /></el-form-item>
                <el-form-item label="工单类型"><el-input v-model="workOrderForm.type" maxlength="60" placeholder="例如：设备维修" /></el-form-item>
                <el-form-item label="优先">
                  <el-select v-model="workOrderForm.priority" aria-label="优先">
                    <el-option label="低" value="低" />
                    <el-option label="中" value="中" />
                    <el-option label="高" value="高" />
                  </el-select>
                </el-form-item>
                <el-button native-type="submit" type="primary" :loading="isWorkOrderCreateSubmitting">创建工单</el-button>
              </el-form>
            </section>
          </template>

          <template v-else-if="currentView === 'workOrderDetail'">
            <el-alert v-if="workOrderMessage" :title="workOrderMessage" type="success" show-icon :closable="false" />
            <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false" />
            <p v-if="isDetailLoading" class="empty-state">详情加载</p>
            <section v-else-if="selectedWorkOrder" class="profile-section detail-panel">
              <template v-if="isEditingWorkOrder">
                <h3>修改工单</h3>
                <el-form class="auth-form" label-position="top" @submit.prevent="submitWorkOrderEdit">
                  <el-form-item label="标题"><el-input v-model="editWorkOrderForm.title" maxlength="120" /></el-form-item>
                  <el-form-item label="详细描述"><el-input v-model="editWorkOrderForm.description" type="textarea" :rows="4" /></el-form-item>
                  <el-form-item label="工单类型"><el-input v-model="editWorkOrderForm.type" maxlength="60" /></el-form-item>
                  <el-form-item label="优先">
                    <el-select v-model="editWorkOrderForm.priority" aria-label="修改优先">
                      <el-option label="低" value="低" />
                      <el-option label="中" value="中" />
                      <el-option label="高" value="高" />
                    </el-select>
                  </el-form-item>
                  <div class="detail-actions">
                    <el-button native-type="submit" type="primary" :loading="isWorkOrderActionSubmitting">保存修改</el-button>
                    <el-button :disabled="isWorkOrderActionSubmitting" @click="stopEditingWorkOrder">放弃修改</el-button>
                  </div>
                </el-form>
              </template>
              <template v-else>
                <h3>{{ selectedWorkOrder.title }}</h3>
                <dl class="detail-list">
                  <div><dt>描述</dt><dd>{{ selectedWorkOrder.description }}</dd></div>
                  <div><dt>类型</dt><dd>{{ selectedWorkOrder.type }}</dd></div>
                  <div><dt>优先级</dt><dd>{{ selectedWorkOrder.priority }}</dd></div>
                  <div><dt>状态</dt><dd>{{ selectedWorkOrder.status }}</dd></div>
                  <div><dt>所属部</dt><dd>{{ organizationLabel(selectedWorkOrder) }}</dd></div>
                  <div><dt>创建人</dt><dd>{{ selectedWorkOrder.creatorUsername }}</dd></div>
                  <div><dt>处理人</dt><dd>{{ selectedWorkOrder.handlerUsername || '未分配' }}</dd></div>
                  <div><dt>创建时间</dt><dd>{{ formatTime(selectedWorkOrder.createdAt) }}</dd></div>
                  <div><dt>SLA 状态</dt><dd>{{ slaStatusLabel(selectedWorkOrder.slaStatus) }}</dd></div>
                  <div><dt>首次响应截止</dt><dd>{{ selectedWorkOrder.firstResponseDueAt ? formatTime(selectedWorkOrder.firstResponseDueAt) : '未设置' }}</dd></div>
                  <div><dt>解决截止</dt><dd>{{ selectedWorkOrder.resolutionDueAt ? formatTime(selectedWorkOrder.resolutionDueAt) : '未设置' }}</dd></div>
                  <div><dt>首次响应时间</dt><dd>{{ selectedWorkOrder.firstRespondedAt ? formatTime(selectedWorkOrder.firstRespondedAt) : '未响应' }}</dd></div>
                  <div><dt>解决时间</dt><dd>{{ selectedWorkOrder.resolvedAt ? formatTime(selectedWorkOrder.resolvedAt) : '未解决' }}</dd></div>
                </dl>
                <section class="attachment-section" aria-label="工单附件">
                  <div class="section-header">
                    <h4>工单附件</h4>
                    <label class="file-upload-button">
                      <input
                        type="file"
                        accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,image/jpeg,image/png,image/gif,image/webp,application/pdf"
                        :disabled="isAttachmentSubmitting"
                        @change="submitAttachment"
                      />
                      <span>{{ isAttachmentSubmitting ? '上传中' : '上传附件' }}</span>
                    </label>
                  </div>
                  <p v-if="isAttachmentsLoading" class="empty-state">附件加载中...</p>
                  <p v-else-if="workOrderAttachments.length === 0" class="empty-state">暂无附件</p>
                  <div v-else class="attachment-list">
                    <article v-for="attachment in workOrderAttachments" :key="attachment.id" class="attachment-item">
                      <div>
                        <strong>{{ attachment.originalFilename }}</strong>
                        <span class="item-meta">
                          {{ formatFileSize(attachment.fileSize) }} ·
                          {{ attachment.uploaderNickname || attachment.uploaderUsername }} ·
                          {{ formatTime(attachment.createdAt) }}
                        </span>
                      </div>
                      <el-button
                        tag="a"
                        size="small"
                        :href="workOrderAttachmentDownloadUrl(selectedWorkOrder.id, attachment.id)"
                      >下载</el-button>
                    </article>
                  </div>
                </section>
                <section class="comment-section" aria-label="工单评论">
                  <div class="section-header">
                    <h4>工单评论</h4>
                  </div>
                  <p v-if="isCommentsLoading" class="empty-state">评论加载中...</p>
                  <p v-else-if="workOrderComments.length === 0" class="empty-state">暂无评论</p>
                  <div v-else class="comment-list">
                    <article v-for="comment in workOrderComments" :key="comment.id" class="comment-item">
                      <header class="comment-header">
                        <div>
                          <strong>{{ comment.authorNickname || comment.authorUsername }}</strong>
                          <span class="item-meta">{{ comment.authorUsername }} · {{ roleLabel(comment.authorRole) }} · {{ formatTime(comment.createdAt) }}</span>
                        </div>
                        <el-button
                          v-if="isAdmin && hasPermission('ticket:comment')"
                          size="small"
                          type="danger"
                          plain
                          :loading="isCommentSubmitting"
                          @click="deleteComment(comment.id)"
                        >删除</el-button>
                      </header>
                      <p class="comment-content">{{ comment.content }}</p>
                    </article>
                  </div>
                  <el-form v-if="canCommentSelectedWorkOrder" class="comment-form" label-position="top" @submit.prevent="submitComment">
                    <el-form-item label="添加评论">
                      <el-input
                        v-model="commentForm.content"
                        type="textarea"
                        :rows="3"
                        maxlength="1000"
                        show-word-limit
                        placeholder="请输入评论内容"
                        :disabled="isCommentSubmitting"
                      />
                    </el-form-item>
                    <el-button native-type="submit" type="primary" :loading="isCommentSubmitting">发表评论</el-button>
                  </el-form>
                  <p v-else class="empty-state">已取消工单不能继续评论</p>
                </section>
                <section class="operation-log-section" aria-label="工单操作记录">
                  <h4>工单操作记录</h4>
                  <p v-if="isOperationLogsLoading" class="empty-state">操作记录加载中...</p>
                  <p v-else-if="operationLogs.length === 0" class="empty-state">暂无操作记录</p>
                  <el-timeline v-else class="operation-timeline">
                    <el-timeline-item
                      v-for="log in operationLogs"
                      :key="log.id"
                      :timestamp="formatTime(log.createdAt)"
                      placement="top"
                    >
                      <strong>{{ operationActionLabel(log.action) }}</strong>
                      <span class="operation-actor">{{ operationActor(log) }}</span>
                      <p v-if="operationChangeText(log)" class="operation-change">{{ operationChangeText(log) }}</p>
                    </el-timeline-item>
                  </el-timeline>
                </section>
                <el-form v-if="canAssignSelectedWorkOrder" class="assignment-form" label-position="top" @submit.prevent="submitHandlerAssignment">
                  <el-form-item label="选择处理">
                    <el-select
                      v-model="assignmentForm.handlerId"
                      filterable
                      placeholder="请选择启用状态的管理员"
                      :loading="isAdminHandlersLoading"
                      :disabled="isWorkOrderActionSubmitting"
                    >
                      <el-option
                        v-for="handler in adminHandlers"
                        :key="handler.id"
                        :label="`${handler.nickname}（${handler.username}）`"
                        :value="handler.id"
                      />
                    </el-select>
                  </el-form-item>
                  <el-button
                    native-type="submit"
                    type="primary"
                    :disabled="!selectedAssignmentHandler"
                    :loading="isWorkOrderActionSubmitting"
                  >确认分配</el-button>
                </el-form>
                <div class="detail-actions">
                  <el-button @click="openWorkOrders">返回工单</el-button>
                  <el-button
                    v-if="canAcceptSelectedWorkOrder"
                    type="primary"
                    :loading="isWorkOrderActionSubmitting"
                    @click="submitAcceptWorkOrder"
                  >接单</el-button>
                  <el-button
                    v-if="canSubmitSelectedWorkOrder"
                    type="primary"
                    :loading="isWorkOrderActionSubmitting"
                    @click="submitProcessingComplete"
                  >处理完成</el-button>
                  <el-button
                    v-if="canReturnSelectedWorkOrder"
                    :loading="isWorkOrderActionSubmitting"
                    @click="submitReturnToProcessing"
                  >退回处理中</el-button>
                  <el-button
                    v-if="canConfirmSelectedWorkOrder"
                    type="success"
                    :loading="isWorkOrderActionSubmitting"
                    @click="submitCompletionConfirmation"
                  >确认完成</el-button>
                  <el-button v-if="canManageSelectedWorkOrder" type="primary" @click="startEditingWorkOrder">修改</el-button>
                  <el-button
                    v-if="canCancelSelectedWorkOrder"
                    type="danger"
                    :loading="isWorkOrderActionSubmitting"
                    @click="submitWorkOrderCancellation"
                  >取消工单</el-button>
                </div>
              </template>
            </section>
            <p v-else-if="!detailError" class="empty-state">暂无工单详情</p>
          </template>

          <div v-else class="profile-grid">
            <section class="profile-section">
              <h3>个人资料</h3>
              <p>用户名：{{ currentUser?.username }}</p>
              <p>角色：{{ currentRoleLabels }}</p>
              <el-alert v-if="profileMessage" :title="profileMessage" type="success" show-icon :closable="false" />
              <el-alert v-if="profileError" :title="profileError" type="error" show-icon :closable="false" />
              <el-form class="auth-form" label-position="top" @submit.prevent="submitProfile">
                <el-form-item label="昵称"><el-input v-model="profileForm.nickname" maxlength="60" /></el-form-item>
                <el-button native-type="submit" type="primary">保存资料</el-button>
              </el-form>
            </section>

            <section class="profile-section">
              <h3>修改密码</h3>
              <el-alert v-if="passwordError" :title="passwordError" type="error" show-icon :closable="false" />
              <el-form class="auth-form" label-position="top" @submit.prevent="submitPassword">
                <el-form-item label="原密"><el-input v-model="passwordForm.currentPassword" type="password" show-password /></el-form-item>
                <el-form-item label="新密"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item>
                <el-form-item label="确认新密"><el-input v-model="passwordForm.confirmPassword" type="password" show-password /></el-form-item>
                <el-button native-type="submit" type="primary">修改密码</el-button>
              </el-form>
            </section>
          </div>
        </template>
      </section>
    </main>
  </el-config-provider>
</template>
