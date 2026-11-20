import './style.css';

const app = document.querySelector<HTMLDivElement>('#app');
if (!app) throw new Error('Missing app element');

const configured = Boolean(import.meta.env.VITE_API_BASE_URL);
app.innerHTML = `
  <section class="shell">
    <span class="eyebrow">GOLDPRICE MONITOR</span>
    <h1>金价监控</h1>
    <p>桌面应用基础工程已就绪。</p>
    <div class="status"><span class="dot"></span>${configured ? 'API 地址已配置' : '等待配置 API 地址'}</div>
    <small>金价展示与监控功能将在 API 契约明确后接入。</small>
  </section>
`;
