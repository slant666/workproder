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

function mountApp() {
  return mount(App, { global: { plugins: [ElementPlus] } });
}

function mockApi(currentUser: unknown = null, orders: unknown[] = []) {
  const detailOrder = orders.find((order) => (order as { id?: number }).id === 10) || workOrder;
  vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
    const url = input.toString();
    const method = init?.method || 'GET';
    if (url === '/api/system/status') return okResponse({ status: 'ok', service: 'work-order-system', timestamp: 'now' });
    if (url === '/api/system/database') return okResponse({ status: 'ok', database: 'mysql', validation: 1 });
    if (url === '/api/auth/me') return currentUser ? okResponse(currentUser) : okResponse({ message: '请先登录' }, 401);
    if (url === '/api/work-orders' && method === 'POST') return okResponse(workOrder);
    if (url === '/api/work-orders/10' && method === 'GET') return okResponse(detailOrder);
    if (url === '/api/work-orders/10' && method === 'PUT') return okResponse(workOrder);
    if (url === '/api/work-orders/10/cancel' && method === 'POST') return okResponse({ ...workOrder, status: '已取消' });
    if (url.startsWith('/api/work-orders') && method === 'GET') return currentUser ? okResponse(paged(orders)) : okResponse({ message: '请先登录' }, 401);
    if (url === '/api/admin/overview') return currentUser && (currentUser as { role?: string }).role === 'ADMIN' ? okResponse({ status: 'ok', area: 'admin' }) : okResponse({ message: 'Access denied' }, 403);
    if (url.startsWith('/api/admin/work-orders') && method === 'GET') return currentUser && (currentUser as { role?: string }).role === 'ADMIN' ? okResponse(paged(orders)) : okResponse({ message: 'Access denied' }, 403);
    if (url === '/api/auth/logout' && method === 'POST') return noContent();
    return Promise.reject(new Error(`Unexpected request: ${method} ${url}`));
  });
}

async function mountWithApi(currentUser: unknown = null, orders: unknown[] = []) {
  mockApi(currentUser, orders);
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
    const adminListCall = vi.mocked(globalThis.fetch).mock.calls.find(([url]) => url.toString().startsWith('/api/admin/work-orders'));
    expect(adminListCall).toBeTruthy();
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

  it('only shows actions for a manageable pending work order', async () => {
    const otherOrder = { ...workOrder, creatorId: 2, creatorUsername: 'other' };
    const completedOrder = { ...workOrder, status: '已完成' };

    const userWrapper = await mountWithApi(user, [otherOrder]);
    await userWrapper.find('.work-order-item').trigger('click');
    await flushPromises();
    expect(userWrapper.findAll('button').map((button) => button.text())).not.toContain('修改');
    expect(userWrapper.findAll('button').map((button) => button.text())).not.toContain('取消工单');
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
    expect(adminWrapper.findAll('button').map((button) => button.text())).toContain('取消工单');
  });

  it('updates a pending work order with only editable fields', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = input.toString();
      const method = init?.method || 'GET';
      if (url === '/api/system/status') return okResponse({ status: 'ok', service: 'work-order-system', timestamp: 'now' });
      if (url === '/api/system/database') return okResponse({ status: 'ok', database: 'mysql', validation: 1 });
      if (url === '/api/auth/me') return okResponse(user);
      if (url === '/api/work-orders/10' && method === 'GET') return okResponse(workOrder);
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
