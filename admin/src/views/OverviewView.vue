<template>
  <div class="page"><div class="eyebrow">OVERVIEW / 01</div><h1>管理总览</h1><p class="lead">查看金价、配置监控规则并跟踪服务运行状态。</p>
    <div class="card-grid">
      <RouterLink to="/prices" class="feature-card"><span class="card-number">01 / DATA</span><h2>金价数据</h2><p>查看金价信息</p><span class="card-link">前往查看 →</span></RouterLink>
      <RouterLink to="/rules" class="feature-card"><span class="card-number">02 / RULES</span><h2>监控规则</h2><p>管理监控配置</p><span class="card-link">前往查看 →</span></RouterLink>
      <RouterLink to="/status" class="feature-card"><span class="card-number">03 / STATUS</span><h2>运行状态</h2><p>跟踪服务状态</p><span class="card-link">前往查看 →</span></RouterLink>
    </div>
    <el-alert title="业务接口尚未接入" description="页面入口与 API 客户端已就绪；接口路径和数据结构需与 API 服务契约确认。" type="info" :closable="false" show-icon />
  </div>
</template>
