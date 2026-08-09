import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { ElMessageBox } from 'element-plus';
import App from './App.vue';

const okResponse = (body: unknown, status = 200) => Promise.resolve({ ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) } as Response);
const noContent = () => Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve(null) } as Response);
const paged = (items: unknown[], overrides: Partial<{ total: number; page: number; pageSize: number; totalPages: number }> = {}) => ({
  items,
  total: overrides.total ?? items.length,
  page: overrides.page ?? 1,
  pageSize: overrides.pageSize ?? 10,
  totalPages: overrides.totalPages ?? (items.length === 0 ? 0 : 1),
});
const user = { id: 1, username: 'demo', nickname: '演示用户', role: 'USER' };
const admin = { id: 2, username: 'admin', nickname: '管理员', role: 'ADMIN' };
const adminUserRows = [
  { ...admin, enabled: true, createdAt: '2026-08-07T00:00:00Z', updatedAt: '2026-08-07T00:00:00Z' },
  { ...user, enabled: true, createdAt: '2026-08-07T01:00:00Z', updatedAt: '2026-08-07T01:00:00Z' },
];
const workOrder = {
  id: 10,
  title: '打印机故障',
  description: '无法打印',
  type: '设备维修',
  priority: '高',
  status: '待处理',
  creatorId: 1,
  creatorUsername: 'demo',
  handlerId: 2,
  handlerUsername: 'admin',
  createdAt: '2026-08-07T00:00:00Z',
};
const handlers = [
  { id: 2, username: 'admin', nickname: 'Admin' },
  { id: 3, username: 'handler', nickname: 'Handler' },
];
const workOrderStatistics = {
  total: 3,
  statusCounts: [
    { label: '待处理', count: 1 },
    { label: '处理中', count: 1 },
    { label: '待确认', count: 0 },
    { label: '已完成', count: 1 },
    { label: '已取消', count: 0 },
  ],
  priorityCounts: [
    { label: '低', count: 0 },
    { label: '中', count: 1 },
    { label: '高', count: 2 },
  ],
  dailyNewCounts: [
    { date: '2026-08-07', count: 2 },
    { date: '2026-08-08', count: 1 },
  ],
  averageProcessingMinutes: 135,
  adminProcessingCounts: [{ handlerId: 2, handlerUsername: 'admin', handlerNickname: 'Admin', count: 2 }],
  overdueUnhandledCount: 1,
  averageProcessingRule: '首次接单到用户确认完成',
  overdueRule: '状态为待处理且创建时间超过 48 小时',
};
const emptyWorkOrderStatistics = {
  ...workOrderStatistics,
  total: 0,
  statusCounts: workOrderStatistics.statusCounts.map((item) => ({ ...item, count: 0 })),
  priorityCounts: workOrderStatistics.priorityCounts.map((item) => ({ ...item, count: 0 })),
  dailyNewCounts: [],
  averageProcessingMinutes: 0,
  adminProcessingCounts: [],
  overdueUnhandledCount: 0,
};
const operationLogs = [
  {
    id: 1,
    workOrderId: 10,
    actorId: 1,
    actorUsername: 'demo',
    actorNickname: 'Demo',
    action: 'create',
    fieldName: null,
    oldValue: null,
    newValue: workOrder.title,
    detailsJson: null,
    createdAt: '2026-08-07T00:00:00Z',
  },
  {
    id: 2,
    workOrderId: 10,
    actorId: 2,
    actorUsername: 'admin',
    actorNickname: 'Admin',
    action: 'assign_handler',
    fieldName: 'handler',
    oldValue: null,
    newValue: 'admin',
    detailsJson: null,
    createdAt: '2026-08-07T01:00:00Z',
  },
];
const comments = [
  {
    id: 1,
    workOrderId: 10,
    authorId: 1,
    authorUsername: 'demo',
    authorNickname: 'Demo',
    authorRole: 'USER',
    content: '普通评论',
    createdAt: '2026-08-07T02:00:00Z',
  },
  {
    id: 2,
    workOrderId: 10,
    authorId: 2,
    authorUsername: 'admin',
    authorNickname: 'Admin',
    authorRole: 'ADMIN',
    content: '<script>alert(1)</script>',
    createdAt: '2026-08-07T03:00:00Z',
  },
];
const attachments = [
  {
    id: 1,
    workOrderId: 10,
    uploaderId: 1,
    uploaderUsername: 'demo',
    uploaderNickname: 'Demo',
    originalFilename: 'report.pdf',
    contentType: 'application/pdf',
    fileSize: 2048,
    createdAt: '2026-08-07T04:00:00Z',
  },
];

function mountApp() {
  return mount(App, { global: { plugins: [ElementPlus] } });
}

function mockApi(currentUser: unknown = null, orders: unknown[] = [], statisticsResponse: unknown = workOrderStatistics) {
  const detailOrder = orders.find((order) => (order as { id?: number }).id === 10) || workOrder;
  vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
    const url = input.toString();
    const method = init?.method || 'GET';
    if (url === '/api/system/status') return okResponse({ status: 'ok', service: 'work-order-system', timestamp: 'now' });
    if (url === '/api/system/database') return okResponse({ status: 'ok', database: 'mysql', validation: 1 });
    if (url === '/api/auth/me') return currentUser ? okResponse(currentUser) : okResponse({ message: '请先登录' }, 401);
    if (url === '/api/work-orders' && method === 'POST') return okResponse(workOrder);
    if (url === '/api/work-orders/10' && method === 'GET') return okResponse(detailOrder);
    if (url === '/api/work-orders/10/logs' && method === 'GET') return okResponse(operationLogs);
    if (url === '/api/work-orders/10/comments' && method === 'GET') return okResponse(comments);
    if (url === '/api/work-orders/10/comments' && method === 'POST') return okResponse({ ...comments[0], id: 3, content: '新评论' });
    if (url === '/api/work-orders/10/attachments' && method === 'GET') return okResponse(attachments);
    if (url === '/api/work-orders/10/attachments' && method === 'POST') return okResponse({ ...attachments[0], id: 2, originalFilename: 'new.pdf' });
    if (url === '/api/work-orders/10/comments/1' && method === 'DELETE') return noContent();
    if (url === '/api/work-orders/10' && method === 'PUT') return okResponse(workOrder);
    if (url === '/api/work-orders/10/cancel' && method === 'POST') return okResponse({ ...workOrder, status: '已取消' });
    if (url.startsWith('/api/work-orders') && method === 'GET') return currentUser ? okResponse(paged(orders)) : okResponse({ message: '请先登录' }, 401);
    if (url === '/api/admin/overview') return currentUser && (currentUser as { role?: string }).role === 'ADMIN' ? okResponse({ status: 'ok', area: 'admin' }) : okResponse({ message: 'Access denied' }, 403);
    if (url === '/api/admin/handlers') return currentUser && (currentUser as { role?: string }).role === 'ADMIN' ? okResponse(handlers) : okResponse({ message: 'Access denied' }, 403);
    if (url.startsWith('/api/admin/users') && method === 'GET') return currentUser && (currentUser as { role?: string }).role === 'ADMIN' ? okResponse(paged(adminUserRows)) : okResponse({ message: 'Access denied' }, 403);
    if (url === '/api/admin/users/1/enabled' && method === 'PUT') return currentUser && (currentUser as { role?: string }).role === 'ADMIN' ? okResponse({ ...adminUserRows[1], enabled: JSON.parse(init?.body as string).enabled }) : okResponse({ message: 'Access denied' }, 403);
    if (url === '/api/admin/users/1/role' && method === 'PUT') return currentUser && (currentUser as { role?: string }).role === 'ADMIN' ? okResponse({ ...adminUserRows[1], role: JSON.parse(init?.body as string).role }) : okResponse({ message: 'Access denied' }, 403);
    if (url.startsWith('/api/admin/work-orders/statistics') && method === 'GET') return currentUser && (currentUser as { role?: string }).role === 'ADMIN' ? okResponse(statisticsResponse) : okResponse({ message: 'Access denied' }, 403);
    if (url.startsWith('/api/admin/work-orders') && method === 'GET') return currentUser && (currentUser as { role?: string }).role === 'ADMIN' ? okResponse(paged(orders)) : okResponse({ message: 'Access denied' }, 403);
    if (url === '/api/admin/work-orders/10/handler' && method === 'PUT') return currentUser && (currentUser as { role?: string }).role === 'ADMIN' ? okResponse({ ...workOrder, handlerId: 3, handlerUsername: 'handler' }) : okResponse({ message: 'Access denied' }, 403);
    if (url === '/api/admin/work-orders/10/accept' && method === 'PUT') return okResponse({ ...workOrder, status: '处理中', handlerId: 2, handlerUsername: 'admin' });
    if (url === '/api/admin/work-orders/10/submit' && method === 'PUT') return okResponse({ ...workOrder, status: '待确认', handlerId: 2, handlerUsername: 'admin' });
    if (url === '/api/admin/work-orders/10/return' && method === 'PUT') return okResponse({ ...workOrder, status: '处理中', handlerId: 2, handlerUsername: 'admin' });
    if (url === '/api/work-orders/10/confirm' && method === 'POST') return okResponse({ ...workOrder, status: '已完成' });
    if (url === '/api/auth/logout' && method === 'POST') return noContent();
    return Promise.reject(new Error(`Unexpected request: ${method} ${url}`));
  });
}

async function mountWithApi(currentUser: unknown = null, orders: unknown[] = [], statisticsResponse: unknown = workOrderStatistics) {
  mockApi(currentUser, orders, statisticsResponse);
  const wrapper = mountApp();
  await flushPromises();
  return wrapper;
}

async function fillInputs(wrapper: ReturnType<typeof mountApp>, values: string[]) {
  const inputs = wrapper.findAll('input');
  expect(inputs.length).toBeGreaterThanOrEqual(values.length);
  for (const [index, value] of values.entries()) await inputs[index]!.setValue(value);
}

describe('App', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('renders health states', async () => {
    const wrapper = await mountWithApi();
    expect(wrapper.text()).toContain('前端运行正常');
    expect(wrapper.text()).toContain('后端连接正常');
    expect(wrapper.text()).toContain('数据库连接正常');
  });

  it('logs in and opens protected work order list', async () => {
    const wrapper = await mountWithApi();
    vi.mocked(globalThis.fetch).mockImplementation((input, init) => {
      const url = input.toString();
      if (url === '/api/auth/login' && init?.method === 'POST') return okResponse(user);
      if (url.startsWith('/api/work-orders')) return okResponse(paged([]));
      return okResponse({ message: '请先登录' }, 401);
    });
    await fillInputs(wrapper, ['demo', 'password123']);
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('工单列表');
    expect(wrapper.text()).toContain('欢迎，演示用户');
  });

  it('shows user menu without admin entry', async () => {
    const wrapper = await mountWithApi(user);
    expect(wrapper.text()).toContain('工单');
    expect(wrapper.text()).toContain('个人资料');
    expect(wrapper.text()).not.toContain('管理页面');
    expect(wrapper.findAll('button').map((button) => button.text())).not.toContain('管理');
  });

  it('shows admin menu and opens admin work order list', async () => {
    const wrapper = await mountWithApi(admin, [workOrder]);
    expect(wrapper.findAll('button').map((button) => button.text())).toContain('\u7ba1\u7406');
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('\u7ba1\u7406\u5458\u5de5\u5355\u5217\u8868');
    expect(wrapper.text()).toContain(workOrder.title);
    const adminListCall = vi.mocked(globalThis.fetch).mock.calls.find(([url]) =>
      url.toString().startsWith('/api/admin/work-orders') && !url.toString().startsWith('/api/admin/work-orders/statistics'),
    );
    expect(adminListCall).toBeTruthy();
  });

  it('loads work order statistics when opening the admin page', async () => {
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('工单统计看板');
    expect(wrapper.text()).toContain('工单总数');
    expect(wrapper.text()).toContain('平均处理时长');
    expect(wrapper.text()).toContain('2 小时 15 分钟');
    expect(wrapper.text()).toContain('超时未处理');
    expect(wrapper.text()).toContain('每日新增趋势');
    expect(wrapper.text()).toContain('各管理员处理数量');
    const statisticsCall = vi.mocked(globalThis.fetch).mock.calls.find(([url]) =>
      url.toString().startsWith('/api/admin/work-orders/statistics'),
    );
    expect(statisticsCall).toBeTruthy();
  });

  it('requests work order statistics with date range', async () => {
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    const statisticsForm = wrapper.find('.statistics-filters');
    const inputs = statisticsForm.findAll('input');
    await inputs[0]!.setValue('2026-08-01');
    await inputs[1]!.setValue('2026-08-08');
    await statisticsForm.trigger('submit');
    await flushPromises();

    const statisticsCall = vi.mocked(globalThis.fetch).mock.calls
      .map(([url]) => url.toString())
      .find((url) => url.startsWith('/api/admin/work-orders/statistics?'));
    expect(statisticsCall).toContain('createdFrom=2026-08-01');
    expect(statisticsCall).toContain('createdTo=2026-08-08');
  });

  it('shows empty state when work order statistics has no data', async () => {
    const wrapper = await mountWithApi(admin, [], emptyWorkOrderStatistics);
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('当前日期范围内暂无工单统计数据');
    expect(wrapper.text()).toContain('0 分钟');
  });

  it('loads admin users when opening the admin page', async () => {
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('用户管理');
    expect(wrapper.text()).toContain('管理员（admin）');
    expect(wrapper.text()).toContain('演示用户（demo）');
    const userListCall = vi.mocked(globalThis.fetch).mock.calls
      .map(([url]) => url.toString())
      .find((url) => url.startsWith('/api/admin/users?'));
    expect(userListCall).toContain('page=1');
    expect(userListCall).toContain('pageSize=10');
  });

  it('requests filtered admin users from the first page', async () => {
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    const filterForm = wrapper.find('.admin-user-filters');
    await filterForm.find('input')!.setValue('demo');
    await filterForm.trigger('submit');
    await flushPromises();

    const userListCall = [...vi.mocked(globalThis.fetch).mock.calls]
      .reverse()
      .map(([url]) => url.toString())
      .find((url) => url.startsWith('/api/admin/users?'));
    expect(userListCall).toContain('keyword=demo');
    expect(userListCall).toContain('page=1');
    expect(userListCall).toContain('pageSize=10');
  });

  it('requests the selected admin user page', async () => {
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    const pagination = wrapper.findAllComponents({ name: 'ElPagination' })[0]!;
    pagination.vm.$emit('current-change', 2);
    await flushPromises();

    const userListCall = [...vi.mocked(globalThis.fetch).mock.calls]
      .reverse()
      .map(([url]) => url.toString())
      .find((url) => url.startsWith('/api/admin/users?'));
    expect(userListCall).toContain('page=2');
    expect(userListCall).toContain('pageSize=10');
  });

  it('requires confirmation before changing an admin user role and respects cancellation', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue(new Error('cancel') as never);
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    await wrapper.findAll('.user-management-item button').find((button) => button.text() === '设为管理员')!.trigger('click');
    await flushPromises();

    expect(ElMessageBox.confirm).toHaveBeenCalled();
    const roleUpdateCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/admin/users/1/role' && init?.method === 'PUT');
    expect(roleUpdateCall).toBeFalsy();
  });

  it('prevents disabling or downgrading the current admin in the UI', async () => {
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    const selfButtons = wrapper.findAll('.user-management-item')[0]!.findAll('button');
    const disableSelfButton = selfButtons.find((button) => button.text() === '禁用')!;
    const downgradeSelfButton = selfButtons.find((button) => button.text() === '降级为用户')!;
    expect(disableSelfButton.attributes('disabled')).toBeDefined();
    expect(downgradeSelfButton.attributes('disabled')).toBeDefined();
  });

  it('updates user enabled state after confirmation and refreshes admin users', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue({ action: 'confirm' } as never);
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    const enabledUserButtons = wrapper.findAll('.user-management-item')[1]!.findAll('button');
    await enabledUserButtons.find((button) => button.text() === '禁用')!.trigger('click');
    await flushPromises();

    const enabledUpdateCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/admin/users/1/enabled' && init?.method === 'PUT');
    expect(enabledUpdateCall).toBeTruthy();
    expect(JSON.parse(enabledUpdateCall![1]!.body as string)).toEqual({ enabled: false });
    const userListCalls = vi.mocked(globalThis.fetch).mock.calls.filter(([url, init]) => url.toString().startsWith('/api/admin/users') && (init?.method || 'GET') === 'GET');
    expect(userListCalls.length).toBeGreaterThanOrEqual(2);
  });

  it('requests filtered admin work orders from the first page', async () => {
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.findAll('button').find((button) => button.text() === '\u7ba1\u7406')!.trigger('click');
    await flushPromises();

    const filterForm = wrapper.find('.admin-work-order-filters');
    await filterForm.find('input')!.setValue('printer');
    await filterForm.trigger('submit');
    await flushPromises();

    const adminListCall = vi.mocked(globalThis.fetch).mock.calls
      .map(([url]) => url.toString())
      .find((url) => url.startsWith('/api/admin/work-orders?') && url.includes('keyword=printer'));
    expect(adminListCall).toContain('sort=createdAtDesc');
    expect(adminListCall).toContain('page=1');
    expect(adminListCall).toContain('pageSize=10');
  });


  it('shows empty state when work order list has no data', async () => {
    const wrapper = await mountWithApi(user, []);
    expect(wrapper.text()).toContain('暂无工单');
  });

  it('requests filtered work orders from the first page', async () => {
    const wrapper = await mountWithApi(user, []);
    const filterForm = wrapper.find('.work-order-filters');

    await filterForm.find('input')!.setValue('printer');
    await filterForm.trigger('submit');
    await flushPromises();

    const listCall = vi.mocked(globalThis.fetch).mock.calls
      .map(([url]) => url.toString())
      .find((url) => url.startsWith('/api/work-orders?') && url.includes('keyword=printer'));
    expect(listCall).toContain('sort=createdAtDesc');
    expect(listCall).toContain('page=1');
    expect(listCall).toContain('pageSize=10');
  });

  it('opens work order detail from the list and shows all fields', async () => {
    const wrapper = await mountWithApi(user, [workOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('工单详情');
    expect(wrapper.text()).toContain('打印机故障');
    expect(wrapper.text()).toContain('描述无法打印');
    expect(wrapper.text()).toContain('类型设备维修');
    expect(wrapper.text()).toContain('优先级高');
    expect(wrapper.text()).toContain('状态待处理');
    expect(wrapper.text()).toContain('创建人demo');
    expect(wrapper.text()).toContain('创建时间');
    expect(wrapper.findAll('button').map((button) => button.text())).toContain('修改');
    expect(wrapper.findAll('button').map((button) => button.text())).toContain('取消工单');
  });

  it('loads and renders work order operation logs on the detail page', async () => {
    const wrapper = await mountWithApi(user, [workOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    const logsCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10/logs' && (init?.method || 'GET') === 'GET');
    expect(logsCall).toBeTruthy();
    expect(wrapper.text()).toContain('工单操作记录');
    expect(wrapper.text()).toContain('创建工单');
    expect(wrapper.text()).toContain('分配处理人');
    expect(wrapper.text()).toContain('Demo（demo）');
    expect(wrapper.text()).toContain('admin');
  });

  it('loads and safely renders work order comments on the detail page', async () => {
    const wrapper = await mountWithApi(user, [workOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    const commentsCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10/comments' && (init?.method || 'GET') === 'GET');
    expect(commentsCall).toBeTruthy();
    expect(wrapper.text()).toContain('工单评论');
    expect(wrapper.text()).toContain('普通评论');
    expect(wrapper.text()).toContain('<script>alert(1)</script>');
    expect(wrapper.html()).not.toContain('<script>alert(1)</script>');
    expect(wrapper.html()).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
  });

  it('loads and renders work order attachments on the detail page', async () => {
    const wrapper = await mountWithApi(user, [workOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    const attachmentsCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10/attachments' && (init?.method || 'GET') === 'GET');
    expect(attachmentsCall).toBeTruthy();
    expect(wrapper.text()).toContain('report.pdf');
    expect(wrapper.text()).toContain('2.0 KB');
    expect(wrapper.find('a[href="/api/work-orders/10/attachments/1/download"]').exists()).toBe(true);
  });

  it('uploads an attachment and refreshes attachments and operation logs', async () => {
    const wrapper = await mountWithApi(user, [workOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    const input = wrapper.find('.file-upload-button input');
    const file = new File(['hello'], 'new.pdf', { type: 'application/pdf' });
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true });
    await input.trigger('change');
    await flushPromises();

    const uploadCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10/attachments' && init?.method === 'POST');
    expect(uploadCall).toBeTruthy();
    expect(uploadCall![1]!.body).toBeInstanceOf(FormData);
    const attachmentListCalls = vi.mocked(globalThis.fetch).mock.calls.filter(([url, init]) => url.toString() === '/api/work-orders/10/attachments' && (init?.method || 'GET') === 'GET');
    const logCalls = vi.mocked(globalThis.fetch).mock.calls.filter(([url, init]) => url.toString() === '/api/work-orders/10/logs' && (init?.method || 'GET') === 'GET');
    expect(attachmentListCalls.length).toBeGreaterThanOrEqual(2);
    expect(logCalls.length).toBeGreaterThanOrEqual(2);
  });

  it('adds a non-empty comment and refreshes comments and operation logs', async () => {
    const wrapper = await mountWithApi(user, [workOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    const commentForm = wrapper.find('.comment-form');
    await commentForm.find('textarea').setValue('新评论');
    await commentForm.trigger('submit');
    await flushPromises();

    const createCommentCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10/comments' && init?.method === 'POST');
    expect(createCommentCall).toBeTruthy();
    expect(JSON.parse(createCommentCall![1]!.body as string)).toEqual({ content: '新评论' });
    const commentListCalls = vi.mocked(globalThis.fetch).mock.calls.filter(([url, init]) => url.toString() === '/api/work-orders/10/comments' && (init?.method || 'GET') === 'GET');
    const logCalls = vi.mocked(globalThis.fetch).mock.calls.filter(([url, init]) => url.toString() === '/api/work-orders/10/logs' && (init?.method || 'GET') === 'GET');
    expect(commentListCalls.length).toBeGreaterThanOrEqual(2);
    expect(logCalls.length).toBeGreaterThanOrEqual(2);
  });

  it('rejects blank comments in the UI', async () => {
    const wrapper = await mountWithApi(user, [workOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    await wrapper.find('.comment-form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('评论内容不能为空');
    const createCommentCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10/comments' && init?.method === 'POST');
    expect(createCommentCall).toBeFalsy();
  });

  it('lets admins delete comments but hides delete actions from regular users', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue({ action: 'confirm' } as never);

    const userWrapper = await mountWithApi(user, [workOrder]);
    await userWrapper.find('.work-order-item').trigger('click');
    await flushPromises();
    expect(userWrapper.findAll('.comment-item button').map((button) => button.text())).not.toContain('删除');
    userWrapper.unmount();

    vi.restoreAllMocks();
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue({ action: 'confirm' } as never);
    const adminWrapper = await mountWithApi(admin, [workOrder]);
    await adminWrapper.find('.work-order-item').trigger('click');
    await flushPromises();
    await adminWrapper.find('.comment-item button').trigger('click');
    await flushPromises();

    const deleteCommentCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10/comments/1' && init?.method === 'DELETE');
    expect(deleteCommentCall).toBeTruthy();
    expect(adminWrapper.text()).toContain('评论已删除');
  });

  it('does not show the comment form for cancelled work orders', async () => {
    const cancelledOrder = { ...workOrder, status: '已取消' };
    const wrapper = await mountWithApi(user, [cancelledOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    expect(wrapper.find('.comment-form').exists()).toBe(false);
    expect(wrapper.text()).toContain('已取消工单不能继续评论');
  });

  it('only shows actions for a manageable pending work order', async () => {
    const otherOrder = { ...workOrder, creatorId: 99, creatorUsername: 'other' };
    const completedOrder = { ...workOrder, status: '已完成' };

    const userWrapper = await mountWithApi(user, [otherOrder]);
    await userWrapper.find('.work-order-item').trigger('click');
    await flushPromises();
    expect(userWrapper.findAll('button').map((button) => button.text())).not.toContain('修改');
    expect(userWrapper.findAll('button').map((button) => button.text())).not.toContain('取消工单');
    expect(userWrapper.findAll('button').map((button) => button.text())).not.toContain('确认分配');
    userWrapper.unmount();

    vi.restoreAllMocks();
    const completedWrapper = await mountWithApi(user, [completedOrder]);
    await completedWrapper.find('.work-order-item').trigger('click');
    await flushPromises();
    expect(completedWrapper.findAll('button').map((button) => button.text())).not.toContain('修改');
    expect(completedWrapper.findAll('button').map((button) => button.text())).not.toContain('取消工单');
    completedWrapper.unmount();

    vi.restoreAllMocks();
    const adminWrapper = await mountWithApi(admin, [otherOrder]);
    await adminWrapper.find('.work-order-item').trigger('click');
    await flushPromises();
    expect(adminWrapper.findAll('button').map((button) => button.text())).toContain('修改');
    expect(adminWrapper.findAll('button').map((button) => button.text())).not.toContain('取消工单');
    expect(adminWrapper.findAll('button').map((button) => button.text())).toContain('确认分配');
  });

  it('runs allowed status actions from the detail page', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue({ action: 'confirm' } as never);
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '接单')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('接单成功');
    expect(wrapper.text()).toContain('状态处理中');

    await wrapper.findAll('button').find((button) => button.text() === '处理完成')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('已提交确认');
    expect(wrapper.text()).toContain('状态待确认');
  });

  it('lets the creator confirm a waiting work order', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue({ action: 'confirm' } as never);
    const waitingOrder = { ...workOrder, status: '待确认', handlerId: 2, handlerUsername: 'admin' };
    const wrapper = await mountWithApi(user, [waitingOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '确认完成')!.trigger('click');
    await flushPromises();

    const confirmCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10/confirm' && init?.method === 'POST');
    expect(confirmCall).toBeTruthy();
    expect(wrapper.text()).toContain('工单已完成');
    expect(wrapper.text()).toContain('状态已完成');
  });

  it('assigns a pending work order handler after confirmation', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue({ action: 'confirm' } as never);
    const wrapper = await mountWithApi(admin, [workOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    wrapper.find('.assignment-form').findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 3);
    await flushPromises();
    await wrapper.find('.assignment-form').trigger('submit');
    await flushPromises();

    const assignCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/admin/work-orders/10/handler' && init?.method === 'PUT');
    expect(assignCall).toBeTruthy();
    expect(JSON.parse(assignCall![1]!.body as string)).toEqual({ handlerId: 3 });
    expect(ElMessageBox.confirm).toHaveBeenCalled();
    expect(wrapper.text()).toContain('处理人分配成功');
    expect(wrapper.text()).toContain('处理人handler');
  });

  it('updates a pending work order with only editable fields', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = input.toString();
      const method = init?.method || 'GET';
      if (url === '/api/system/status') return okResponse({ status: 'ok', service: 'work-order-system', timestamp: 'now' });
      if (url === '/api/system/database') return okResponse({ status: 'ok', database: 'mysql', validation: 1 });
      if (url === '/api/auth/me') return okResponse(user);
      if (url === '/api/work-orders/10' && method === 'GET') return okResponse(workOrder);
      if (url === '/api/work-orders/10/logs' && method === 'GET') return okResponse(operationLogs);
      if (url === '/api/work-orders/10/comments' && method === 'GET') return okResponse(comments);
      if (url === '/api/work-orders/10/attachments' && method === 'GET') return okResponse(attachments);
      if (url.startsWith('/api/work-orders') && method === 'GET') return okResponse(paged([workOrder]));
      if (url === '/api/work-orders/10' && method === 'PUT') {
        return okResponse({ ...workOrder, title: '新标题', description: '新描述', type: '账号问题' });
      }
      return Promise.reject(new Error(`Unexpected request: ${method} ${url}`));
    });
    const wrapper = mountApp();
    await flushPromises();
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '修改')!.trigger('click');

    const inputs = wrapper.findAll('.detail-panel input');
    await inputs[0]!.setValue('新标题');
    await wrapper.find('.detail-panel textarea').setValue('新描述');
    await inputs[1]!.setValue('账号问题');
    await wrapper.find('.detail-panel form').trigger('submit');
    await flushPromises();

    const updateCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10' && init?.method === 'PUT');
    expect(updateCall).toBeTruthy();
    expect(JSON.parse(updateCall![1]!.body as string)).toEqual({
      title: '新标题',
      description: '新描述',
      type: '账号问题',
      priority: '高',
    });
    expect(wrapper.text()).toContain('工单修改成功');
    expect(wrapper.text()).toContain('新标题');
  });

  it('cancels a pending work order through the dedicated endpoint', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue({ action: 'confirm' } as never);
    const wrapper = await mountWithApi(user, [workOrder]);
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '取消工单')!.trigger('click');
    await flushPromises();

    const cancelCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10/cancel' && init?.method === 'POST');
    expect(cancelCall).toBeTruthy();
    expect(wrapper.text()).toContain('工单已取消');
    expect(wrapper.text()).toContain('状态已取消');
    expect(wrapper.findAll('button').map((button) => button.text())).not.toContain('修改');
  });

  it('shows detail error without leaked content', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = input.toString();
      const method = init?.method || 'GET';
      if (url === '/api/system/status') return okResponse({ status: 'ok', service: 'work-order-system', timestamp: 'now' });
      if (url === '/api/system/database') return okResponse({ status: 'ok', database: 'mysql', validation: 1 });
      if (url === '/api/auth/me') return okResponse(user);
      if (url === '/api/work-orders/11') return okResponse({ message: 'Access denied', title: '不应出现的标题' }, 403);
      if (url.startsWith('/api/work-orders') && method === 'GET') return okResponse(paged([{ ...workOrder, id: 11, title: '可见列表项' }]));
      return Promise.reject(new Error(`Unexpected request: ${method} ${url}`));
    });
    const wrapper = mountApp();
    await flushPromises();
    await wrapper.find('.work-order-item').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('Access denied');
    expect(wrapper.text()).not.toContain('不应出现的标题');
  });

  it('creates work order and opens detail view by loading detail API', async () => {
    const wrapper = await mountWithApi(user);
    const createForm = wrapper.findAll('form')[wrapper.findAll('form').length - 1]!;
    const inputs = createForm.findAll('input');
    await inputs[0]!.setValue('?????');
    await createForm.find('textarea')!.setValue('????');
    await inputs[1]!.setValue('????');
    await createForm.trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('工单创建成功');
    expect(wrapper.text()).toContain('工单详情');
    expect(wrapper.text()).toContain('打印机故障');
    const createCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders' && init?.method === 'POST');
    const detailCall = vi.mocked(globalThis.fetch).mock.calls.find(([url, init]) => url.toString() === '/api/work-orders/10' && (init?.method || 'GET') === 'GET');
    expect(createCall).toBeTruthy();
    expect(detailCall).toBeTruthy();
    expect(JSON.parse(createCall![1]!.body as string)).not.toHaveProperty('creatorId');
  });

  it('shows validation error when creating work order without title', async () => {
    const wrapper = await mountWithApi(user);
    await wrapper.findAll('form')[wrapper.findAll('form').length - 1]!.trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('标题不能为空');
  });

  it('shows current username, nickname, and role on profile page', async () => {
    const wrapper = await mountWithApi(user);
    await wrapper.findAll('button').find((button) => button.text() === '个人资料')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('用户名：demo');
    expect(wrapper.text()).toContain('角色：USER');
    expect(wrapper.text()).toContain('演示用户');
  });

  it('updates nickname without editing username or role', async () => {
    const wrapper = await mountWithApi(user);
    await wrapper.findAll('button').find((button) => button.text() === '个人资料')!.trigger('click');
    await flushPromises();
    vi.mocked(globalThis.fetch).mockImplementation((input, init) => {
      const url = input.toString();
      if (url === '/api/auth/profile' && init?.method === 'PATCH') return okResponse({ ...user, nickname: '新昵称' });
      if (url === '/api/system/status') return okResponse({ status: 'ok', service: 'work-order-system', timestamp: 'now' });
      if (url === '/api/system/database') return okResponse({ status: 'ok', database: 'mysql', validation: 1 });
      if (url === '/api/auth/me') return okResponse(user);
      if (url.startsWith('/api/work-orders')) return okResponse(paged([]));
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    const nicknameInput = wrapper.findAll('input').find((input) => input.element.value === '演示用户')!;
    await nicknameInput.setValue('新昵称');
    await wrapper.findAll('form')[0]!.trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('资料已更新');
    expect(wrapper.text()).toContain('用户名：demo');
    expect(wrapper.text()).toContain('角色：USER');
  });

  it('changes password and returns to login view', async () => {
    const wrapper = await mountWithApi(user);
    await wrapper.findAll('button').find((button) => button.text() === '个人资料')!.trigger('click');
    await flushPromises();
    vi.mocked(globalThis.fetch).mockImplementation((input, init) => {
      const url = input.toString();
      if (url === '/api/auth/password' && init?.method === 'POST') return noContent();
      if (url === '/api/system/status') return okResponse({ status: 'ok', service: 'work-order-system', timestamp: 'now' });
      if (url === '/api/system/database') return okResponse({ status: 'ok', database: 'mysql', validation: 1 });
      if (url === '/api/auth/me') return okResponse(user);
      if (url.startsWith('/api/work-orders')) return okResponse(paged([]));
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    const inputs = wrapper.findAll('input');
    await inputs[1]!.setValue('password123');
    await inputs[2]!.setValue('newpass123');
    await inputs[3]!.setValue('newpass123');
    await wrapper.findAll('form')[1]!.trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('用户登录');
    expect(wrapper.text()).toContain('密码已修改，请重新登录');
    expect(wrapper.text()).not.toContain('工单列表');
  });
});
