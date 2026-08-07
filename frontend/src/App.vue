<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessageBox } from 'element-plus';
import {
  acceptWorkOrder,
  assignWorkOrderHandler,
  fetchAdminHandlers,
  fetchAdminOverview,
  fetchAdminWorkOrders,
  returnWorkOrderToProcessing,
  submitWorkOrderForConfirmation,
  type AdminHandler,
} from './api/admin';
import { checkBackend, checkDatabase, type HealthStatus } from './api/health';
import {
  cancelWorkOrder,
  confirmWorkOrder,
  createWorkOrderComment,
  createWorkOrder,
  deleteWorkOrderComment,
  fetchWorkOrderComments,
  fetchWorkOrderDetail,
  fetchWorkOrderLogs,
  fetchWorkOrders,
  updateWorkOrder,
  type WorkOrder,
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

const registerForm = reactive({ username: '', nickname: '', password: '', confirmPassword: '' });
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
const assignmentForm = reactive({ handlerId: undefined as number | undefined });

const currentView = ref<View>('login');
const currentUser = ref<CurrentUser | null>(null);
const workOrders = ref<WorkOrder[]>([]);
const workOrderTotal = ref(0);
const workOrderPage = ref(1);
const workOrderPageSize = ref(10);
const workOrderTotalPages = ref(0);
const adminWorkOrders = ref<WorkOrder[]>([]);
const adminHandlers = ref<AdminHandler[]>([]);
const adminWorkOrderTotal = ref(0);
const adminWorkOrderPage = ref(1);
const adminWorkOrderPageSize = ref(10);
const adminWorkOrderTotalPages = ref(0);
const selectedWorkOrder = ref<WorkOrder | null>(null);
const operationLogs = ref<WorkOrderOperationLog[]>([]);
const workOrderComments = ref<WorkOrderComment[]>([]);
const registerError = ref('');
const loginError = ref('');
const successMessage = ref('');
const workOrderError = ref('');
const workOrderMessage = ref('');
const detailError = ref('');
const adminError = ref('');
const profileError = ref('');
const profileMessage = ref('');
const passwordError = ref('');
const isSubmitting = ref(false);
const isWorkOrdersLoading = ref(false);
const isDetailLoading = ref(false);
const isAdminWorkOrdersLoading = ref(false);
const isAdminHandlersLoading = ref(false);
const isOperationLogsLoading = ref(false);
const isCommentsLoading = ref(false);
const isWorkOrderActionSubmitting = ref(false);
const isCommentSubmitting = ref(false);
const isEditingWorkOrder = ref(false);
const workOrdersLoaded = ref(false);
const adminLoaded = ref(false);
const statusItems = computed(() => [statuses.frontend, statuses.backend, statuses.database]);
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN');
const canManageSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  const user = currentUser.value;
  return Boolean(workOrder && user && workOrder.status === '待处理' && (isAdmin.value || workOrder.creatorId === user.id));
});
const canAssignSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && isAdmin.value && workOrder.status === '待处理');
});
const selectedAssignmentHandler = computed(() => adminHandlers.value.find((handler) => handler.id === assignmentForm.handlerId));
const canAcceptSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && isAdmin.value && workOrder.status === '待处理' && (!workOrder.handlerId || workOrder.handlerId === currentUser.value?.id));
});
const canSubmitSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && isAdmin.value && workOrder.status === '处理中' && workOrder.handlerId === currentUser.value?.id);
});
const canReturnSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && isAdmin.value && workOrder.status === '待确认' && workOrder.handlerId === currentUser.value?.id);
});
const canConfirmSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && currentUser.value && workOrder.status === '待确认' && workOrder.creatorId === currentUser.value.id);
});
const canCancelSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && currentUser.value && workOrder.status === '待处理' && workOrder.creatorId === currentUser.value.id);
});
const canCommentSelectedWorkOrder = computed(() => {
  const workOrder = selectedWorkOrder.value;
  return Boolean(workOrder && currentUser.value && workOrder.status !== '已取消');
});
const pageTitle = computed(() => {
  if (currentView.value === 'admin') return '管理页面';
  if (currentView.value === 'profile') return '个人资料';
  if (currentView.value === 'workOrderDetail') return '工单详情';
  return '工单列表';
});

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value || '无创建时间';
  return date.toLocaleString('zh-CN', { hour12: false });
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
    USER: '\u7528\u6237',
  };
  return labels[role] || role;
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
  if (username.length < 4 || username.length > 30) return '用户名长度必须为 4 到 30 个字符';
  if (!nickname) return '昵称不能为空';
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
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword,
    });
    successMessage.value = '注册成功，请登录';
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
    await openWorkOrders();
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

async function loadAdminHandlers() {
  if (!currentUser.value || !isAdmin.value) {
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

async function loadAdminWorkOrders(options: { resetPage?: boolean } = {}) {
  if (!currentUser.value || !isAdmin.value) {
    currentView.value = 'login';
    return;
  }
  if (options.resetPage) {
    adminWorkOrderPage.value = 1;
  }
  isAdminWorkOrdersLoading.value = true;
  adminError.value = '';
  try {
    if (adminHandlers.value.length === 0) {
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
  commentForm.content = '';
  isEditingWorkOrder.value = false;
  try {
    selectedWorkOrder.value = await fetchWorkOrderDetail(id);
    assignmentForm.handlerId = selectedWorkOrder.value.handlerId ?? undefined;
    await Promise.all([loadOperationLogs(id), loadComments(id)]);
    if (isAdmin.value && adminHandlers.value.length === 0) {
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
    await loadOperationLogs(updated.id);
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
  if (!workOrder || !isAdmin.value) return;
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
    await loadOperationLogs(updated.id);
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
  try {
    const created = await createWorkOrder({
      title: workOrderForm.title.trim(),
      description: workOrderForm.description.trim(),
      type: workOrderForm.type.trim(),
      priority: workOrderForm.priority,
    });
    workOrderForm.title = '';
    workOrderForm.description = '';
    workOrderForm.type = '';
    workOrderForm.priority = '中';
    workOrderMessage.value = '工单创建成功';
    await openWorkOrderDetail(created.id);
  } catch (error) {
    workOrderError.value = error instanceof Error ? error.message : '创建工单失败';
  }
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
    await loadOperationLogs(updated.id);
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
  if (!currentUser.value) {
    currentView.value = 'login';
    return;
  }
  adminError.value = '';
  try {
    await fetchAdminOverview();
    currentView.value = 'admin';
    await loadAdminHandlers();
    await loadAdminWorkOrders();
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
  await logoutUser();
  currentUser.value = null;
  workOrders.value = [];
  selectedWorkOrder.value = null;
  operationLogs.value = [];
  workOrderComments.value = [];
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
      await openWorkOrders();
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
});
</script>

<template>
  <el-config-provider>
    <main class="app-shell">
      <section class="status-panel">
        <p class="eyebrow">Work Order System</p>
        <h1>工单管理系统</h1>
        <p class="summary">开发环境连通性检查</p>
        <ul class="status-list" aria-label="系统连接状态">
          <li v-for="item in statusItems" :key="item.label" class="status-item" :class="`status-${item.status}`">
            <span class="status-icon" aria-hidden="true"></span>
            <span>{{ item.label }}</span>
          </li>
        </ul>
        <el-button type="primary" @click="refreshHealth">重新检查</el-button>
      </section>

      <section class="auth-panel">
        <template v-if="currentView === 'register'">
          <p class="eyebrow">Account</p>
          <h2>用户注册</h2>
          <el-alert v-if="registerError" :title="registerError" type="error" show-icon :closable="false" />
          <el-form class="auth-form" label-position="top" @submit.prevent="submitRegister">
            <el-form-item label="用户名"><el-input v-model="registerForm.username" maxlength="30" placeholder="请输入 4 到 30 个字符" /></el-form-item>
            <el-form-item label="昵称"><el-input v-model="registerForm.nickname" maxlength="60" placeholder="请输入昵称" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="registerForm.password" type="password" show-password placeholder="至少 8 位" /></el-form-item>
            <el-form-item label="确认密码"><el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="请再次输入密码" /></el-form-item>
            <div class="form-actions"><el-button native-type="submit" type="primary" :loading="isSubmitting">注册</el-button><el-button @click="currentView = 'login'">去登录</el-button></div>
          </el-form>
        </template>

        <template v-else-if="currentView === 'login'">
          <p class="eyebrow">Account</p>
          <h2>用户登录</h2>
          <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon :closable="false" />
          <el-alert v-if="loginError || workOrderError" :title="loginError || workOrderError" type="error" show-icon :closable="false" />
          <el-form class="auth-form" label-position="top" @submit.prevent="submitLogin">
            <el-form-item label="用户名"><el-input v-model="loginForm.username" placeholder="请输入用户名" /></el-form-item>
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
            <el-button @click="logout">退出登录</el-button>
          </div>

          <nav class="role-menu" aria-label="功能菜单">
            <el-button :type="currentView === 'workOrders' || currentView === 'workOrderDetail' ? 'primary' : 'default'" @click="openWorkOrders">工单</el-button>
            <el-button :type="currentView === 'profile' ? 'primary' : 'default'" @click="openProfile">个人资料</el-button>
            <el-button v-if="isAdmin" :type="currentView === 'admin' ? 'primary' : 'default'" @click="openAdmin">管理</el-button>
          </nav>

          <p class="summary">欢迎，{{ currentUser?.nickname }}。当前角色：{{ currentUser?.role }}。</p>
          <el-alert v-if="adminError" :title="adminError" type="error" show-icon :closable="false" />

          <template v-if="currentView === 'admin'">
            <section class="profile-section">
              <div class="section-header">
                <h3>&#31649;&#29702;&#21592;&#24037;&#21333;&#21015;&#34920;</h3>
                <el-button size="small" @click="loadAdminWorkOrders">&#21047;&#26032;</el-button>
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
                    {{ item.status }} &#183; {{ item.priority }} &#183; &#21019;&#24314;&#20154; {{ item.creatorUsername }} &#183; &#22788;&#29702;&#20154; {{ item.handlerUsername || '\u672a\u5206\u914d' }} &#183; {{ formatTime(item.createdAt) }}
                  </span>
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
                  <span class="item-meta">{{ item.status }} &#183; {{ item.priority }} &#183; {{ item.creatorUsername }} &#183; {{ formatTime(item.createdAt) }}</span>
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
                <el-form-item label="优先级">
                  <el-select v-model="workOrderForm.priority" aria-label="优先级">
                    <el-option label="低" value="低" />
                    <el-option label="中" value="中" />
                    <el-option label="高" value="高" />
                  </el-select>
                </el-form-item>
                <el-button native-type="submit" type="primary">创建工单</el-button>
              </el-form>
            </section>
          </template>

          <template v-else-if="currentView === 'workOrderDetail'">
            <el-alert v-if="workOrderMessage" :title="workOrderMessage" type="success" show-icon :closable="false" />
            <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false" />
            <p v-if="isDetailLoading" class="empty-state">详情加载中</p>
            <section v-else-if="selectedWorkOrder" class="profile-section detail-panel">
              <template v-if="isEditingWorkOrder">
                <h3>修改工单</h3>
                <el-form class="auth-form" label-position="top" @submit.prevent="submitWorkOrderEdit">
                  <el-form-item label="标题"><el-input v-model="editWorkOrderForm.title" maxlength="120" /></el-form-item>
                  <el-form-item label="详细描述"><el-input v-model="editWorkOrderForm.description" type="textarea" :rows="4" /></el-form-item>
                  <el-form-item label="工单类型"><el-input v-model="editWorkOrderForm.type" maxlength="60" /></el-form-item>
                  <el-form-item label="优先级">
                    <el-select v-model="editWorkOrderForm.priority" aria-label="修改优先级">
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
                  <div><dt>创建人</dt><dd>{{ selectedWorkOrder.creatorUsername }}</dd></div>
                  <div><dt>处理人</dt><dd>{{ selectedWorkOrder.handlerUsername || '未分配' }}</dd></div>
                  <div><dt>创建时间</dt><dd>{{ formatTime(selectedWorkOrder.createdAt) }}</dd></div>
                </dl>
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
                          v-if="isAdmin"
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
                  <el-form-item label="选择处理人">
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
              <p>角色：{{ currentUser?.role }}</p>
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
                <el-form-item label="原密码"><el-input v-model="passwordForm.currentPassword" type="password" show-password /></el-form-item>
                <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item>
                <el-form-item label="确认新密码"><el-input v-model="passwordForm.confirmPassword" type="password" show-password /></el-form-item>
                <el-button native-type="submit" type="primary">修改密码</el-button>
              </el-form>
            </section>
          </div>
        </template>
      </section>
    </main>
  </el-config-provider>
</template>
