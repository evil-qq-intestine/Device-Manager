/* ============================================================
   Device Manager（设备管家）· Vue 3 + Element Plus SPA
   设备在线监控 / Wake-on-LAN 控制台
   ============================================================ */
(function () {
    "use strict";

    const { createApp, nextTick } = Vue;
    const ElMessage = ElementPlus.ElMessage;
    const ElMessageBox = ElementPlus.ElMessageBox;

    /* -------------------- i18n -------------------- */
    const MESSAGES = { zh: null, en: null };

    const SUPPORTED_LANGS = ["zh", "en"];

    async function loadI18n() {
        await Promise.all(SUPPORTED_LANGS.map(async (lang) => {
            const res = await fetch("/i18n/" + lang + ".json", { cache: "no-cache" });
            if (!res.ok) throw new Error("Failed to load i18n/" + lang + ".json: HTTP " + res.status);
            MESSAGES[lang] = await res.json();
        }));
    }

    /* -------------------- 工具函数 -------------------- */
    const MAC_REGEX = /^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/;

    function decodeJwt(token) {
        try {
            const part = token.split(".")[1];
            const base64 = part.replace(/-/g, "+").replace(/_/g, "/");
            const bin = atob(base64);
            const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
            return JSON.parse(new TextDecoder().decode(bytes));
        } catch (e) {
            return null;
        }
    }

    function normalizeDevice(raw) {
        const m = raw.monitor || {};
        return {
            id: m.monitorId != null ? m.monitorId : raw.deviceId,
            mac: raw.mac,
            ip: raw.ip,
            name: raw.deviceName,
            monitorMode: raw.monitorMode || m.monitorMode || "PING",
            ipMode: raw.ipMode || m.ipMode || "IPV4",
            pingInterval: raw.pingInterval != null ? raw.pingInterval : (m.pingInterval != null ? m.pingInterval : 30),
            pingTimeout: raw.pingTimeout != null ? raw.pingTimeout : (m.pingTimeout != null ? m.pingTimeout : 3),
            responseTimeout: raw.responseTimeout != null ? raw.responseTimeout : (m.responseTimeout != null ? m.responseTimeout : 60),
            wakeTimeout: raw.wakeTimeout != null ? raw.wakeTimeout : (m.wakeTimeout != null ? m.wakeTimeout : 120),
            status: raw.status || m.status || "UNKNOWN",
            lastOnlineTime: raw.lastOnlineTime || null,
            sshConfigured: !!raw.sshConfigured,
            sshHost: raw.sshHost || "",
            sshPort: raw.sshPort || null,
            sshUser: raw.sshUser || "",
            sshType: raw.sshType || "BASH"
        };
    }

    /* -------------------- 图表实例（非响应式） -------------------- */
    let statusChart = null;
    let rateChart = null;

    /* -------------------- 应用 -------------------- */
    const template = `
<el-config-provider :locale="elLocale">
<div v-if="!authed" class="login">
  <div class="login__card glass">
    <div class="login__logo">
      <svg viewBox="0 0 24 24" fill="none" stroke="url(#loginGrad)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
        <defs>
          <linearGradient id="loginGrad" x1="0" y1="0" x2="24" y2="24">
            <stop offset="0" stop-color="#0071e3"/><stop offset="1" stop-color="#af52de"/>
          </linearGradient>
        </defs>
        <rect x="2" y="4" width="20" height="13" rx="2"/>
        <path d="M8 21h8M12 17v4"/>
        <path d="M6.5 9.5 9 12l-2.5 2.5M12.5 13H16"/>
      </svg>
    </div>
    <h1 class="login__title">{{ t('app.name') }}</h1>
    <p class="login__subtitle">{{ t('app.tagline') }}</p>
    <el-form class="login__form" @submit.prevent="doLogin">
      <el-form-item>
        <el-input v-model="loginForm.username" size="large" :placeholder="t('login.username')" @keyup.enter="doLogin">
          <template #prefix><el-icon><User/></el-icon></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-input v-model="loginForm.password" size="large" type="password" show-password :placeholder="t('login.password')" @keyup.enter="doLogin">
          <template #prefix><el-icon><Lock/></el-icon></template>
        </el-input>
      </el-form-item>
      <el-button type="primary" class="login__submit" :loading="loggingIn" @click="doLogin">{{ t('login.submit') }}</el-button>
    </el-form>
    <div class="login__hint">{{ t('login.hint') }}</div>
  </div>
  <div class="login__version mono">{{ t('app.name') }} · v{{ version }}</div>
</div>

<div v-else class="app">
  <header class="topbar">
    <div class="brand">
      <span class="brand__mark">
        <svg viewBox="0 0 24 24" fill="none" stroke="#0071e3" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
          <rect x="2" y="4" width="20" height="13" rx="2"/>
          <path d="M8 21h8M12 17v4"/>
          <path d="M6.5 9.5 9 12l-2.5 2.5M12.5 13H16"/>
        </svg>
      </span>
      <span class="brand__name">{{ t('app.name') }}</span>
    </div>
    <nav class="topbar__nav">
      <div class="nav-pill" :class="{active: view==='dashboard'}" @click="view='dashboard'">
        <el-icon><Odometer/></el-icon><span>{{ t('nav.dashboard') }}</span>
      </div>
      <div class="nav-pill" :class="{active: view==='script'}" @click="openScriptView">
        <el-icon><Tickets/></el-icon><span>{{ t('nav.script') }}</span>
      </div>
      <div v-if="isAdmin" class="nav-pill" :class="{active: view==='admin'}" @click="view='admin'">
        <el-icon><UserFilled/></el-icon><span>{{ t('nav.admin') }}</span>
      </div>
    </nav>
    <div class="topbar__spacer"></div>
    <button v-if="updateAvailable" class="update-badge" :title="t('update.title')" @click="openUpdate">
      <el-icon><Download/></el-icon><span>{{ t('update.available', {latest: latestVersion}) }}</span>
    </button>
    <div class="live-dot"><i></i>{{ t('toolbar.live') }}</div>
    <button class="theme-toggle" :title="t('settings.title')" @click="settingsDialog.visible = true">
      <el-icon><Setting/></el-icon>
    </button>
    <el-dropdown trigger="click" @command="onUserCommand">
      <div class="user-chip">
        <div class="user-chip__avatar">{{ avatarText }}</div>
        <span class="user-chip__name">{{ user.username }}</span>
        <span class="role-tag" :class="{'role-tag--user': !isAdmin}">{{ user.role }}</span>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile"><el-icon><User/></el-icon>{{ t('profile.title') }}</el-dropdown-item>
          <el-dropdown-item command="password"><el-icon><Key/></el-icon>{{ t('profile.password') }}</el-dropdown-item>
          <el-dropdown-item command="logout" divided><el-icon><SwitchButton/></el-icon>{{ t('common.logout') }}</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </header>

  <main class="main" :class="{ 'main--wide': view === 'script' && scriptEditorMode === 'editor' }">
    <template v-if="view==='dashboard'">
      <div class="section-title"><span class="spark"></span><h2>{{ t('dashboard.title') }}</h2></div>

      <div class="stats">
        <div class="stat glass" style="--stat-color: var(--accent-blue)">
          <div class="stat__label"><el-icon><Monitor/></el-icon>{{ t('stats.total') }}</div>
          <div class="stat__value">{{ stats.total }}</div>
        </div>
        <div class="stat glass" style="--stat-color: var(--accent-green)">
          <div class="stat__label"><el-icon><CircleCheck/></el-icon>{{ t('stats.online') }}</div>
          <div class="stat__value" style="color: var(--accent-green)">{{ stats.online }}</div>
        </div>
        <div class="stat glass" style="--stat-color: #8e8e93">
          <div class="stat__label"><el-icon><VideoPause/></el-icon>{{ t('stats.offline') }}</div>
          <div class="stat__value" style="color:#8e8e93">{{ stats.offline }}</div>
        </div>
        <div class="stat glass" style="--stat-color: var(--accent-orange)">
          <div class="stat__label"><el-icon><Loading/></el-icon>{{ t('stats.probing') }}</div>
          <div class="stat__value" style="color: var(--accent-orange)">{{ stats.probing }}</div>
        </div>
      </div>

      <div class="panels">
        <div class="panel glass">
          <div class="panel__head"><h3>{{ t('panel.status') }}</h3></div>
          <div ref="statusChartEl" class="chart"></div>
        </div>
        <div class="panel glass">
          <div class="panel__head"><h3>{{ t('panel.rate') }}</h3></div>
          <div ref="rateChartEl" class="chart"></div>
        </div>
      </div>

      <div class="toolbar">
        <el-input v-model="keyword" :placeholder="t('toolbar.search')" clearable style="max-width:260px">
          <template #prefix><el-icon><Search/></el-icon></template>
        </el-input>
        <div class="toolbar__spacer"></div>
        <el-button type="primary" @click="openCreate"><el-icon><Plus/></el-icon>&nbsp;{{ t('toolbar.add') }}</el-button>
        <el-button @click="refresh" :loading="loading"><el-icon><Refresh/></el-icon></el-button>
      </div>

      <div v-if="loading && !devices.length" class="device-grid">
        <div v-for="i in 6" :key="i" class="glass" style="height:214px;border-radius:20px;opacity:.45"></div>
      </div>
      <div v-else-if="!filteredDevices.length" class="glass" style="padding:46px;border-radius:20px">
        <el-empty :description="t('device.empty')" />
      </div>
      <div v-else class="device-grid">
        <div v-for="(d, idx) in filteredDevices" :key="d.id" class="device-card glass"
             :class="[statusClass(d.status), {'flash': changedIds[d.id]}]" :style="{animationDelay: (idx*40)+'ms'}">
          <div class="device-card__top">
            <div class="status-orb">
              <el-icon v-if="d.status==='PROBE'" class="spin-ring" :size="20"><Loading/></el-icon>
              <el-icon v-else :size="20"><component :is="statusIcon(d.status)"/></el-icon>
            </div>
            <div class="device-card__meta">
              <div class="device-card__name">{{ d.name || d.mac }}</div>
              <div class="device-card__mac">{{ d.mac }}</div>
              <div class="device-card__ip">
                <el-icon><Position/></el-icon>
                <span>{{ d.ip || '-' }}</span>
                <span class="chip" :class="{'chip--v6': d.ipMode==='IPV6'}">{{ d.ipMode }}</span>
              </div>
            </div>
          </div>

          <div class="device-card__stats">
            <div class="device-card__stat"><span>{{ t('device.status') }}</span>
              <span class="status-badge" :class="statusBadgeClass(d.status)"><i></i>{{ statusText(d.status) }}</span>
            </div>
            <div class="device-card__stat"><span>{{ t('device.mode') }}</span>
              <span class="chip" :class="{'chip--hb': d.monitorMode==='HEARTBEAT'}">{{ d.monitorMode }}</span>
            </div>
            <div class="device-card__stat"><span>{{ t('device.lastOnline') }}</span>
              <b :title="d.lastOnlineTime || ''">{{ relativeTime(d.lastOnlineTime) }}</b>
            </div>
          </div>

          <div class="device-card__actions">
            <el-button type="primary" @click="wake(d)"><el-icon><Promotion/></el-icon>&nbsp;{{ t('device.wake') }}</el-button>
            <el-button type="warning" plain @click="shutdownDevice(d)"><el-icon><SwitchButton/></el-icon>&nbsp;{{ t('device.shutdown') }}</el-button>
            <el-dropdown trigger="click" @command="cmd => onDeviceCommand(cmd, d)">
              <el-button><el-icon><MoreFilled/></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="downloadScript"><el-icon><Download/></el-icon>{{ t('device.script') }}</el-dropdown-item>
                  <el-dropdown-item command="edit"><el-icon><Edit/></el-icon>{{ t('device.edit') }}</el-dropdown-item>
                  <el-dropdown-item command="monitor" divided><el-icon><Setting/></el-icon>{{ t('device.monitor') }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided><el-icon><Delete/></el-icon>{{ t('device.delete') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </template>

    <template v-else-if="view==='script'">
      <div class="section-title" :class="{ 'section-title--compact-hidden': scriptEditorMode === 'editor' }"><span class="spark"></span><h2>{{ t('nav.script') }}</h2></div>
      <script-task-panel :devices="devices" @mode-change="scriptEditorMode = $event" />
    </template>

    <template v-else-if="view==='admin'">
      <div class="section-title"><span class="spark"></span><h2>{{ t('admin.title') }}</h2></div>
      <div class="admin-panel glass">
        <div class="admin-toolbar">
          <el-input v-model="adminKeyword" :placeholder="t('admin.search')" clearable style="max-width:240px">
            <template #prefix><el-icon><Search/></el-icon></template>
          </el-input>
          <div class="admin-toolbar__spacer"></div>
          <el-button type="primary" @click="openCreateUser"><el-icon><Plus/></el-icon>&nbsp;{{ t('admin.addUser') }}</el-button>
          <el-button @click="loadUsers" :loading="usersLoading"><el-icon><Refresh/></el-icon></el-button>
        </div>
        <el-table :data="filteredUsers" style="width:100%">
          <el-table-column prop="userId" :label="t('admin.id')" width="90" />
          <el-table-column prop="name" :label="t('admin.name')" min-width="160" />
          <el-table-column :label="t('admin.role')" width="150">
            <template #default="{ row }">
              <span class="role-tag" :class="{'role-tag--user': row.role!=='ADMIN'}">{{ row.role }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.actions')" width="230" align="right">
            <template #default="{ row }">
              <el-button size="small" :disabled="row.name===user.username" @click="openChangePassword(row)"><el-icon><Key/></el-icon>&nbsp;{{ t('admin.changePwd') }}</el-button>
              <el-button size="small" type="danger" :disabled="row.name===user.username" @click="removeUser(row)"><el-icon><Delete/></el-icon></el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </main>

  <el-dialog v-model="deviceDialog.visible" :title="deviceDialog.mode==='create' ? t('form.addTitle') : t('form.editTitle')" width="480px">
    <el-form label-position="top">
      <el-form-item :label="t('form.name')">
        <el-input v-model="deviceDialog.form.deviceName" :placeholder="t('form.namePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('form.mac')">
        <el-input v-model="deviceDialog.form.mac" :placeholder="t('form.macPlaceholder')" class="mono" />
      </el-form-item>
      <el-form-item :label="t('form.ip')">
        <el-input v-model="deviceDialog.form.ip" :placeholder="t('form.ipPlaceholder')" class="mono" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="deviceDialog.visible=false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="saveDevice">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="monitorDialog.visible" :title="t('monitor.title')" width="480px">
    <el-form label-position="top">
      <el-form-item :label="t('monitor.mode')">
        <el-select v-model="monitorDialog.form.monitorMode" style="width:100%">
          <el-option label="PING" value="PING" />
          <el-option label="HEARTBEAT" value="HEARTBEAT" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('monitor.ipMode')">
        <el-input :model-value="monitorDialog.form.ipMode" disabled class="mono" />
        <div class="login__hint" style="margin-top:4px">{{ t('monitor.ipModeHint') }}</div>
      </el-form-item>

      <template v-if="monitorDialog.form.monitorMode === 'PING'">
        <el-form-item :label="t('monitor.interval')">
          <el-input-number v-model="monitorDialog.form.pingInterval" :min="1" :max="86400" style="width:100%" />
        </el-form-item>
        <el-form-item :label="t('monitor.pingTimeout')">
          <el-input-number v-model="monitorDialog.form.pingTimeout" :min="1" :max="3600" style="width:100%" />
        </el-form-item>
        <el-form-item :label="t('monitor.responseTimeout')">
          <el-input-number v-model="monitorDialog.form.responseTimeout" :min="1" :max="86400" style="width:100%" />
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item :label="t('monitor.heartbeatTimeout')">
          <el-input-number v-model="monitorDialog.form.responseTimeout" :min="1" :max="86400" style="width:100%" />
        </el-form-item>
      </template>

      <el-form-item :label="t('monitor.wakeTimeout')">
        <el-input-number v-model="monitorDialog.form.wakeTimeout" :min="1" :max="86400" style="width:100%" />
      </el-form-item>

      <div class="login__hint" style="margin-top:0">{{ t('monitor.hint') }}</div>
    </el-form>
    <template #footer>
      <el-button @click="monitorDialog.visible=false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="saveMonitor">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="scriptDialog.visible" :title="t('script.title')" width="640px" :fullscreen="viewport.phone">
    <el-radio-group v-model="scriptDialog.os" @change="loadScript" style="margin-bottom:14px">
      <el-radio-button label="linux">Linux</el-radio-button>
      <el-radio-button label="windows">Windows</el-radio-button>
    </el-radio-group>
    <pre class="script-preview">{{ scriptDialog.preview || t('script.loading') }}</pre>
    <template #footer>
      <el-button @click="scriptDialog.visible=false">{{ t('common.close') }}</el-button>
      <el-button type="primary" @click="downloadScript"><el-icon><Download/></el-icon>&nbsp;{{ t('script.download') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="profileDialog.visible" :title="t('profile.title')" width="420px">
    <el-form label-position="top">
      <el-form-item :label="t('profile.username')">
        <el-input v-model="profileDialog.username" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="profileDialog.visible=false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="saveProfile">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="selfPwdDialog.visible" :title="t('password.title')" width="420px">
    <el-form label-position="top">
      <el-form-item :label="t('password.old')">
        <el-input v-model="selfPwdDialog.oldPassword" type="password" show-password @keyup.enter="changeOwnPassword" />
      </el-form-item>
      <el-form-item :label="t('password.new')">
        <el-input v-model="selfPwdDialog.newPassword" type="password" show-password @keyup.enter="changeOwnPassword" />
      </el-form-item>
      <div class="login__hint" style="margin-top:0">{{ t('password.hint') }}</div>
    </el-form>
    <template #footer>
      <el-button @click="selfPwdDialog.visible=false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="changeOwnPassword">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="adminCreate.visible" :title="t('admin.createTitle')" width="420px">
    <el-form label-position="top">
      <el-form-item :label="t('admin.username')">
        <el-input v-model="adminCreate.form.username" />
      </el-form-item>
      <el-form-item :label="t('admin.password')">
        <el-input v-model="adminCreate.form.password" show-password />
      </el-form-item>
      <el-form-item :label="t('admin.role')">
        <el-select v-model="adminCreate.form.role" style="width:100%">
          <el-option label="USER" value="USER" />
          <el-option label="ADMIN" value="ADMIN" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="adminCreate.visible=false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="createUser">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="adminPwd.visible" :title="t('admin.changePwd')" width="420px">
    <el-form label-position="top">
      <el-form-item :label="t('admin.adminPassword')">
        <el-input v-model="adminPwd.adminPassword" type="password" show-password @keyup.enter="changePassword" />
      </el-form-item>
      <el-form-item :label="t('admin.newPassword')">
        <el-input v-model="adminPwd.password" type="password" show-password @keyup.enter="changePassword" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="adminPwd.visible=false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="changePassword">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="deviceSshDialog.visible" :title="t('deviceSsh.title')" width="540px" :fullscreen="viewport.phone" @closed="clearDeviceSshSecrets">
    <el-form label-position="top">
      <div class="field-hint" style="margin-bottom:12px">{{ t('deviceSsh.hint') }}</div>
      <el-row :gutter="12">
        <el-col :span="14">
          <el-form-item :label="t('deviceSsh.host')"><el-input v-model="deviceSshDialog.form.sshHost" /></el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item :label="t('deviceSsh.port')">
            <el-input-number v-model="deviceSshDialog.form.sshPort" :min="1" :max="65535" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item :label="t('deviceSsh.user')"><el-input v-model="deviceSshDialog.form.sshUser" /></el-form-item>
      <el-form-item :label="t('deviceSsh.type')">
        <el-select v-model="deviceSshDialog.form.sshType" style="width:100%">
          <el-option label="Linux (Bash)" value="BASH" />
          <el-option label="Windows (PowerShell)" value="POWERSHELL" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <template #label><span>{{ t('deviceSsh.privateKey') }}<hint-icon :text="t('deviceSsh.privateKeyHint')" /></span></template>
        <el-input v-model="deviceSshDialog.form.sshPrivateKey" type="textarea" :rows="4" class="mono" autocomplete="off" />
      </el-form-item>
      <el-form-item :label="t('deviceSsh.passphrase')">
        <el-input v-model="deviceSshDialog.form.sshKeyPassphrase" show-password autocomplete="off" />
      </el-form-item>
      <el-form-item v-if="deviceSshDialog.form.sshType === 'BASH'">
        <template #label><span>{{ t('deviceSsh.sudoPassword') }}<hint-icon><sudo-hint /></hint-icon></span></template>
        <el-input v-model="deviceSshDialog.form.sudoPassword" show-password autocomplete="off" />
      </el-form-item>
      <el-form-item v-else :label="t('deviceSsh.sudoPassword')">
        <div class="field-hint">{{ t('deviceSsh.sudoWindowsHint') }}</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="deviceSshDialog.visible=false">{{ t('common.cancel') }}</el-button>
      <el-button @click="testDeviceSsh">{{ t('deviceSsh.test') }}</el-button>
      <el-button type="primary" :loading="saving" @click="saveDeviceSsh">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="updateDialog.visible" :title="t('update.title')" width="620px" :fullscreen="viewport.phone">
    <div class="update-row"><span>{{ t('update.current') }}</span><span class="mono">v{{ versionInfo ? versionInfo.current : version }}</span></div>
    <div class="update-row"><span>{{ t('update.latest') }}</span><span class="mono">{{ latestVersion ? ('v' + latestVersion) : '-' }}</span></div>
    <template v-if="updateAvailable">
      <div v-if="versionInfo.releaseNotes" style="margin-top:14px">
        <div class="field-hint">{{ t('update.notes') }}</div>
        <pre class="update-notes">{{ versionInfo.releaseNotes }}</pre>
      </div>
    </template>
    <div v-else class="field-hint" style="margin-top:14px">{{ t('update.upToDate') }}</div>
    <div v-if="isAdmin" style="margin-top:16px">
      <el-checkbox v-model="versionSettings.directUpdateEnabled" @change="saveVersionSettings">{{ t('update.enableDirect') }}</el-checkbox>
      <div class="field-hint">{{ t('update.directHint') }}</div>
    </div>
    <template #footer>
      <el-button @click="checkVersion" :loading="checkingVersion"><el-icon><Refresh/></el-icon>&nbsp;{{ t('update.check') }}</el-button>
      <el-button v-if="isAdmin" @click="downloadWatchdog">{{ t('update.watchdog') }}</el-button>
      <el-button @click="updateDialog.visible=false">{{ t('common.close') }}</el-button>
      <el-button v-if="isAdmin && versionInfo && versionInfo.directUpdateSupported" type="primary" :loading="saving" @click="doDirectUpdate">{{ t('update.direct') }}</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="settingsDialog.visible" :title="t('settings.title')" width="380px">
    <div class="settings-row">
      <span class="settings-row__label">{{ t('settings.language') }}</span>
      <el-radio-group v-model="lang" size="small">
        <el-radio-button label="zh">中文</el-radio-button>
        <el-radio-button label="en">English</el-radio-button>
      </el-radio-group>
    </div>
    <div class="settings-row">
      <span class="settings-row__label">{{ t('settings.theme') }}</span>
      <el-radio-group v-model="theme" size="small">
        <el-radio-button label="light">{{ t('settings.light') }}</el-radio-button>
        <el-radio-button label="dark">{{ t('settings.dark') }}</el-radio-button>
      </el-radio-group>
    </div>
  </el-dialog>
</div>
</el-config-provider>
`;

    const HintIcon = {
        name: "HintIcon",
        props: { text: { type: String, default: "" } },
        inject: ["nd"],
        template: `<el-tooltip placement="top" effect="dark" :show-after="120" :trigger="nd.viewport.phone ? 'click' : 'hover'" popper-class="hint-popper">
            <el-icon class="hint-icon"><QuestionFilled/></el-icon>
            <template #content><slot>{{ text }}</slot></template>
          </el-tooltip>`
    };

    const SudoHint = {
        name: "SudoHint",
        inject: ["nd"],
        template: `<div>
            <div>{{ nd.t('scriptTask.sudoHint') }}</div>
            <pre class="hint-pre">&lt;ssh-user&gt; ALL=(root) NOPASSWD: /sbin/shutdown, /usr/sbin/shutdown, /sbin/poweroff, /usr/sbin/poweroff, /sbin/halt, /usr/sbin/halt</pre>
          </div>`
    };

    const app = createApp({
        template,
        data() {
            return {
                authed: false,
                token: localStorage.getItem("devicemanager.token") || "",
                user: { username: "", role: "", userId: null },
                version: "1.0.8",
                versionInfo: null,
                checkingVersion: false,
                updateDialog: { visible: false },
                settingsDialog: { visible: false },
                versionSettings: { directUpdateEnabled: false },
                lang: localStorage.getItem("devicemanager.lang") || ((navigator.language || "en").toLowerCase().startsWith("zh") ? "zh" : "en"),
                theme: localStorage.getItem("devicemanager.theme") || (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"),
                viewport: {
                    phone: window.matchMedia("(max-width: 768px)").matches,
                    tablet: window.matchMedia("(min-width: 769px) and (max-width: 1024px)").matches
                },
                view: "dashboard",
                scriptEditorMode: "list",
                loggingIn: false,
                saving: false,
                loading: false,
                usersLoading: false,
                loginForm: { username: "", password: "" },
                devices: [],
                users: [],
                keyword: "",
                adminKeyword: "",
                changedIds: {},
                pollTimer: null,
                deviceDialog: { visible: false, mode: "create", id: null, form: { deviceName: "", mac: "", ip: "" } },
                monitorDialog: { visible: false, id: null, form: { monitorMode: "PING", ipMode: "IPV4", pingInterval: 30, pingTimeout: 3, responseTimeout: 60, wakeTimeout: 120 } },
                scriptDialog: { visible: false, id: null, mac: "", os: "linux", preview: "" },
                profileDialog: { visible: false, username: "" },
                selfPwdDialog: { visible: false, oldPassword: "", newPassword: "" },
                adminCreate: { visible: false, form: { username: "", password: "", role: "USER" } },
                adminPwd: { visible: false, id: null, password: "", adminPassword: "" },
                scriptSelectedId: null,
                deviceSshDialog: { visible: false, deviceId: null, form: { sshHost: "", sshPort: 22, sshUser: "", sshType: "BASH", sshPrivateKey: "", sshKeyPassphrase: "", sudoPassword: "" } }
            };
        },
        provide() {
            const self = this;
            return {
                nd: {
                    t: (key, params) => self.t(key, params),
                    api: (path, options) => self.api(path, options),
                    get viewport() { return self.viewport; }
                }
            };
        },
        computed: {
            isAdmin() {
                return this.user.role === "ADMIN";
            },
            updateAvailable() {
                return !!(this.versionInfo && this.versionInfo.updateAvailable);
            },
            latestVersion() {
                return (this.versionInfo && this.versionInfo.latest) || "";
            },
            elLocale() {
                return this.lang === "zh" && window.ElementPlusLocaleZhCn ? window.ElementPlusLocaleZhCn : undefined;
            },
            avatarText() {
                return (this.user.username || "?").charAt(0).toUpperCase();
            },
            stats() {
                let online = 0, offline = 0, probing = 0;
                for (const d of this.devices) {
                    if (d.status === "ONLINE") online++;
                    else if (d.status === "OFFLINE") offline++;
                    else probing++;
                }
                return { total: this.devices.length, online, offline, probing };
            },
            filteredDevices() {
                const kw = this.keyword.trim().toLowerCase();
                if (!kw) return this.devices;
                return this.devices.filter((d) =>
                    (d.name || "").toLowerCase().includes(kw) ||
                    (d.mac || "").toLowerCase().includes(kw) ||
                    (d.ip || "").toLowerCase().includes(kw));
            },
            scriptSelected() {
                if (this.scriptSelectedId == null) return null;
                return this.devices.find((d) => d.id === this.scriptSelectedId) || null;
            },
            filteredUsers() {
                const kw = this.adminKeyword.trim().toLowerCase();
                if (!kw) return this.users;
                return this.users.filter((u) =>
                    (u.name || "").toLowerCase().includes(kw) ||
                    String(u.userId).includes(kw));
            }
        },
        watch: {
            devices: {
                handler() { nextTick(() => this.updateCharts()); },
                deep: true
            },
            view() {
                nextTick(() => this.updateCharts());
            },
            lang() {
                localStorage.setItem("devicemanager.lang", this.lang);
                nextTick(() => this.updateCharts());
            },
            theme() {
                this.applyTheme();
            },
            authed() {
                nextTick(() => this.measureHeader());
            }
        },
        mounted() {
            window.addEventListener("resize", this.onResize);
            this._mqPhone = window.matchMedia("(max-width: 768px)");
            this._mqTablet = window.matchMedia("(min-width: 769px) and (max-width: 1024px)");
            this._onMq = () => this.syncViewport();
            this._mqPhone.addEventListener("change", this._onMq);
            this._mqTablet.addEventListener("change", this._onMq);
            this.syncViewport();
            this.applyTheme();
            this.fetchVersion();
            if (this.token) {
                const payload = decodeJwt(this.token);
                if (payload && (!payload.exp || payload.exp * 1000 > Date.now())) {
                    this.setToken(this.token);
                    this.authed = true;
                    this.refresh();
                    this.startPolling();
                } else {
                    this.clearToken();
                }
            }
        },
        beforeUnmount() {
            window.removeEventListener("resize", this.onResize);
            if (this._mqPhone) this._mqPhone.removeEventListener("change", this._onMq);
            if (this._mqTablet) this._mqTablet.removeEventListener("change", this._onMq);
            this.stopPolling();
            this.disposeCharts();
        },
        methods: {
            /* ---------- i18n ---------- */
            t(path, params) {
                const dict = MESSAGES[this.lang] || MESSAGES.zh || {};
                let value = path.split(".").reduce((o, k) => (o && o[k] != null ? o[k] : null), dict);
                if (value == null) value = path;
                if (params) value = value.replace(/\{(\w+)\}/g, (m, k) => (params[k] != null ? params[k] : m));
                return value;
            },
            setLang(lang) {
                this.lang = lang;
            },
            applyTheme() {
                document.documentElement.classList.toggle("dark", this.theme === "dark");
                localStorage.setItem("devicemanager.theme", this.theme);
                nextTick(() => this.updateCharts());
            },
            toggleTheme() {
                this.theme = this.theme === "dark" ? "light" : "dark";
            },

            /* ---------- 认证 ---------- */
            async api(path, options) {
                options = options || {};
                const headers = Object.assign({}, options.headers);
                if (options.body !== undefined) headers["Content-Type"] = "application/json";
                if (this.token) headers["Authorization"] = "Bearer " + this.token;
                const res = await fetch(path, {
                    method: options.method || "GET",
                    headers,
                    body: options.body !== undefined ? JSON.stringify(options.body) : undefined
                });
                if (res.status === 401) {
                    this.handleUnauthorized();
                    throw new Error(this.t("error.unauthorized"));
                }
                if (options.raw) {
                    if (!res.ok) throw new Error(await this.readError(res));
                    return res;
                }
                if (res.status === 204) return null;
                const text = await res.text();
                let data = null;
                if (text) {
                    try { data = JSON.parse(text); } catch (e) { data = text; }
                }
                if (!res.ok) {
                    throw new Error((data && data.message) ? data.message : this.t("error.request") + " (" + res.status + ")");
                }
                return data;
            },
            async readError(res) {
                try {
                    const text = await res.text();
                    const json = JSON.parse(text);
                    return json.message || ("HTTP " + res.status);
                } catch (e) {
                    return "HTTP " + res.status;
                }
            },
            setToken(token) {
                this.token = token;
                localStorage.setItem("devicemanager.token", token);
                const payload = decodeJwt(token);
                if (payload) {
                    this.user = { username: payload.sub, role: payload.role || "USER", userId: payload.userId };
                }
            },
            clearToken() {
                this.token = "";
                localStorage.removeItem("devicemanager.token");
                this.user = { username: "", role: "", userId: null };
            },
            handleUnauthorized() {
                this.clearToken();
                this.authed = false;
                this.devices = [];
                this.users = [];
                this.stopPolling();
                this.disposeCharts();
                ElMessage.warning(this.t("error.unauthorized"));
            },
            async doLogin() {
                if (!this.loginForm.username || !this.loginForm.password) {
                    ElMessage.warning(this.t("error.credentials"));
                    return;
                }
                this.loggingIn = true;
                try {
                    const data = await this.api("/api/auth", { method: "POST", body: this.loginForm });
                    this.setToken(data.token);
                    this.authed = true;
                    ElMessage.success(this.t("login.welcome", { name: this.user.username }));
                    await this.refresh();
                    this.startPolling();
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.loggingIn = false;
                }
            },
            logout() {
                this.stopPolling();
                this.disposeCharts();
                this.clearToken();
                this.authed = false;
                this.devices = [];
                this.users = [];
                this.view = "dashboard";
                this.scriptSelectedId = null;
                this.loginForm = { username: "", password: "" };
            },
            onUserCommand(cmd) {
                if (cmd === "logout") this.logout();
                else if (cmd === "profile") {
                    this.profileDialog.username = this.user.username;
                    this.profileDialog.visible = true;
                } else if (cmd === "password") {
                    this.openSelfPassword();
                }
            },
            openSelfPassword() {
                this.selfPwdDialog.oldPassword = "";
                this.selfPwdDialog.newPassword = "";
                this.selfPwdDialog.visible = true;
            },
            async changeOwnPassword() {
                const f = this.selfPwdDialog;
                if (!f.oldPassword || !f.newPassword) {
                    ElMessage.warning(this.t("error.credentials"));
                    return;
                }
                if (f.newPassword.length < 6 || f.newPassword.length > 32) {
                    ElMessage.warning(this.t("error.passwordLength"));
                    return;
                }
                this.saving = true;
                try {
                    const data = await this.api("/me/password", {
                        method: "PUT",
                        body: { oldPassword: f.oldPassword, newPassword: f.newPassword }
                    });
                    if (data && data.token) this.setToken(data.token);
                    ElMessage.success(this.t("password.changed"));
                    f.visible = false;
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.saving = false;
                }
            },
            async saveProfile() {
                if (!this.profileDialog.username) return;
                this.saving = true;
                try {
                    const data = await this.api("/me/username", { method: "PUT", body: { username: this.profileDialog.username } });
                    if (data && data.token) this.setToken(data.token);
                    ElMessage.success(this.t("common.success"));
                    this.profileDialog.visible = false;
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.saving = false;
                }
            },

            /* ---------- 设备 ---------- */
            async fetchVersion() {
                try {
                    const v = await this.api("/api/version");
                    if (v && typeof v === "object") {
                        this.versionInfo = v;
                        if (v.current) this.version = v.current;
                    } else if (typeof v === "string" && v) {
                        this.version = v;
                    }
                } catch (e) { /* ignore */ }
            },
            /* ---------- 版本更新 ---------- */
            openUpdate() {
                this.updateDialog.visible = true;
                if (this.isAdmin) this.loadVersionSettings();
            },
            async checkVersion() {
                this.checkingVersion = true;
                try {
                    const v = await this.api("/api/version/check", { method: "POST" });
                    if (v && typeof v === "object") {
                        this.versionInfo = v;
                        if (v.current) this.version = v.current;
                    }
                    ElMessage.success(v && v.updateAvailable
                        ? this.t("update.available", { latest: this.latestVersion })
                        : this.t("update.upToDate"));
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.checkingVersion = false;
                }
            },
            async loadVersionSettings() {
                try {
                    const s = await this.api("/api/version/settings");
                    this.versionSettings.directUpdateEnabled = !!(s && s.directUpdateEnabled);
                } catch (e) { /* ignore */ }
            },
            async saveVersionSettings() {
                try {
                    await this.api("/api/version/settings", {
                        method: "PUT",
                        body: { directUpdateEnabled: this.versionSettings.directUpdateEnabled }
                    });
                    ElMessage.success(this.t("common.success"));
                    await this.fetchVersion();
                } catch (e) {
                    ElMessage.error(e.message);
                }
            },
            async doDirectUpdate() {
                try {
                    await ElMessageBox.confirm(
                        this.t("update.confirmDirect", { latest: this.latestVersion }),
                        this.t("update.title"),
                        { type: "warning" });
                } catch (e) {
                    return;
                }
                this.saving = true;
                try {
                    const r = await this.api("/api/version/update", { method: "POST" });
                    ElMessage.success((r && r.message) || this.t("common.success"));
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.saving = false;
                }
            },
            async downloadWatchdog() {
                try {
                    const res = await this.api("/api/version/watchdog", { raw: true });
                    const blob = await res.blob();
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement("a");
                    a.href = url;
                    a.download = "device-manager-watchdog.sh";
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    URL.revokeObjectURL(url);
                } catch (e) {
                    ElMessage.error(e.message);
                }
            },
            async refresh() {
                if (!this.authed) return;
                this.loading = true;
                try {
                    const list = await this.api("/api/device");
                    this.applyDevices(list);
                } catch (e) {
                    if (this.authed) ElMessage.error(e.message);
                } finally {
                    this.loading = false;
                }
            },
            applyDevices(list) {
                const prev = {};
                this.devices.forEach((d) => { prev[d.id] = d.status; });
                const next = (list || []).map(normalizeDevice);
                next.forEach((d) => {
                    if (prev[d.id] && prev[d.id] !== d.status) {
                        this.changedIds[d.id] = true;
                        setTimeout(() => { this.changedIds[d.id] = false; }, 1300);
                    }
                });
                this.devices = next;
            },
            openCreate() {
                this.deviceDialog.mode = "create";
                this.deviceDialog.id = null;
                this.deviceDialog.form = { deviceName: "", mac: "", ip: "" };
                this.deviceDialog.visible = true;
            },
            openEdit(d) {
                this.deviceDialog.mode = "edit";
                this.deviceDialog.id = d.id;
                this.deviceDialog.form = { deviceName: d.name || "", mac: d.mac || "", ip: d.ip || "" };
                this.deviceDialog.visible = true;
            },
            async saveDevice() {
                const f = this.deviceDialog.form;
                if (!MAC_REGEX.test((f.mac || "").trim())) {
                    ElMessage.warning(this.t("error.invalidMac"));
                    return;
                }
                if (!f.ip || !f.ip.trim()) {
                    ElMessage.warning(this.t("error.invalidIp"));
                    return;
                }
                this.saving = true;
                try {
                    if (this.deviceDialog.mode === "create") {
                        await this.api("/api/device", {
                            method: "POST",
                            body: {
                                mac: f.mac.trim(),
                                ip: f.ip.trim(),
                                deviceName: f.deviceName
                            }
                        });
                    } else {
                        await this.api("/api/device/" + this.deviceDialog.id, {
                            method: "PUT",
                            body: { mac: f.mac.trim(), ip: f.ip.trim(), deviceName: f.deviceName }
                        });
                    }
                    ElMessage.success(this.t("common.success"));
                    this.deviceDialog.visible = false;
                    await this.refresh();
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.saving = false;
                }
            },
            onDeviceCommand(cmd, d) {
                if (cmd === "monitor") this.openMonitor(d);
                else if (cmd === "delete") this.removeDevice(d);
                else if (cmd === "downloadScript") this.openScript(d);
                else if (cmd === "edit") this.openEdit(d);
            },
            openScriptView() {
                this.view = "script";
            },
            selectScriptDevice(d) {
                this.scriptSelectedId = d ? d.id : null;
            },
            async openDeviceSsh(d) {
                this.deviceSshDialog.deviceId = d.id;
                this.deviceSshDialog.form = {
                    sshHost: "", sshPort: 22, sshUser: "", sshType: "BASH",
                    sshPrivateKey: "", sshKeyPassphrase: "", sudoPassword: ""
                };
                try {
                    const cfg = await this.api("/api/device/" + d.id + "/ssh");
                    if (cfg) {
                        this.deviceSshDialog.form.sshHost = cfg.sshHost || "";
                        this.deviceSshDialog.form.sshPort = cfg.sshPort || 22;
                        this.deviceSshDialog.form.sshUser = cfg.sshUser || "";
                        this.deviceSshDialog.form.sshType = cfg.sshType || "BASH";
                    }
                } catch (e) {
                    ElMessage.error(e.message);
                }
                this.deviceSshDialog.visible = true;
            },
            clearDeviceSshSecrets() {
                const f = this.deviceSshDialog.form;
                if (!f) return;
                f.sshPrivateKey = "";
                f.sshKeyPassphrase = "";
                f.sudoPassword = "";
            },
            async saveDeviceSsh() {
                const f = this.deviceSshDialog.form;
                if (!f.sshHost || !f.sshUser || !f.sshPort) {
                    ElMessage.warning(this.t("deviceSsh.required"));
                    return;
                }
                this.saving = true;
                try {
                    await this.api("/api/device/" + this.deviceSshDialog.deviceId + "/ssh", { method: "PUT", body: f });
                    ElMessage.success(this.t("deviceSsh.saved"));
                    this.clearDeviceSshSecrets();
                    this.deviceSshDialog.visible = false;
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.saving = false;
                }
            },
            async testDeviceSsh() {
                const f = this.deviceSshDialog.form;
                if (!f.sshHost || !f.sshUser || !f.sshPort || !f.sshPrivateKey) {
                    ElMessage.warning(this.t("scriptTask.sshRequired"));
                    return;
                }
                try {
                    const res = await this.api("/api/script-task/test-ssh", {
                        method: "POST",
                        body: {
                            sshHost: f.sshHost, sshPort: f.sshPort, sshUser: f.sshUser,
                            sshPrivateKey: f.sshPrivateKey, sshKeyPassphrase: f.sshKeyPassphrase
                        }
                    });
                    ElMessage.success(this.t("deviceSsh.testOk", { fp: res.fingerprint }));
                } catch (e) {
                    ElMessage.error(e.message);
                }
            },
            shutdownDevice(d) {
                const name = d.name || d.mac;
                ElMessageBox.confirm(this.t("device.confirmShutdown", { name }), this.t("device.shutdownTitle"), {
                    type: "warning",
                    confirmButtonText: this.t("common.confirm"),
                    cancelButtonText: this.t("common.cancel")
                }).then(async () => {
                    try {
                        const res = await this.api("/api/device/" + d.id + "/shutdown", { method: "POST" });
                        if (res && res.shutdownTriggered) ElMessage.success(this.t("device.shutdownSent"));
                        else ElMessage.error((res && res.errorMessage) || this.t("device.shutdownFail"));
                    } catch (e) {
                        ElMessage.error(e.message);
                    }
                }).catch(() => {});
            },
            openMonitor(d) {
                this.monitorDialog.id = d.id;
                this.monitorDialog.form = {
                    monitorMode: d.monitorMode,
                    ipMode: this.detectIpMode(d.ip),
                    pingInterval: d.pingInterval,
                    pingTimeout: d.pingTimeout,
                    responseTimeout: d.responseTimeout,
                    wakeTimeout: d.wakeTimeout
                };
                this.monitorDialog.visible = true;
            },
            detectIpMode(ip) {
                if (!ip) return "INVALID";
                const s = String(ip).trim();
                const v4 = /^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/;
                if (v4.test(s)) return "IPV4";
                if (s.includes(":")) return "IPV6";
                return "INVALID";
            },
            async saveMonitor() {
                const f = this.monitorDialog.form;
                const payload = {
                    monitorMode: f.monitorMode,
                    responseTimeout: f.responseTimeout,
                    wakeTimeout: f.wakeTimeout
                };
                if (f.monitorMode === "PING") {
                    if (f.pingTimeout >= f.pingInterval) {
                        ElMessage.warning(this.t("error.pingTimeout"));
                        return;
                    }
                    payload.pingInterval = f.pingInterval;
                    payload.pingTimeout = f.pingTimeout;
                }
                // 不发送 ipMode，由后端根据设备 IP 自动判定
                this.saving = true;
                try {
                    await this.api("/api/device/monitor/" + this.monitorDialog.id, { method: "PUT", body: payload });
                    ElMessage.success(this.t("common.success"));
                    this.monitorDialog.visible = false;
                    await this.refresh();
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.saving = false;
                }
            },
            async wake(d) {
                try {
                    await this.api("/api/device/" + d.id + "/wake", { method: "POST" });
                    ElMessage.success(this.t("device.wakeSent", { name: d.name || d.mac }));
                } catch (e) {
                    ElMessage.error(e.message);
                }
            },
            removeDevice(d) {
                const name = d.name || d.mac;
                ElMessageBox.confirm(this.t("device.confirmDelete", { name }), this.t("device.deleteTitle"), {
                    type: "warning",
                    confirmButtonText: this.t("common.confirm"),
                    cancelButtonText: this.t("common.cancel")
                }).then(async () => {
                    try {
                        await this.api("/api/device/" + d.id, { method: "DELETE" });
                        ElMessage.success(this.t("common.success"));
                        await this.refresh();
                    } catch (e) {
                        ElMessage.error(e.message);
                    }
                }).catch(() => {});
            },

            /* ---------- 脚本 ---------- */
            openScript(d) {
                this.scriptDialog.id = d.id;
                this.scriptDialog.mac = d.mac;
                this.scriptDialog.os = "linux";
                this.scriptDialog.preview = "";
                this.scriptDialog.visible = true;
                this.loadScript();
            },
            scriptUrl() {
                return "/api/device/" + this.scriptDialog.id + "/heartbeat-script?os=" + this.scriptDialog.os;
            },
            async loadScript() {
                this.scriptDialog.preview = "";
                try {
                    const res = await this.api(this.scriptUrl(), { raw: true });
                    this.scriptDialog.preview = await res.text();
                } catch (e) {
                    this.scriptDialog.preview = e.message;
                }
            },
            async downloadScript() {
                try {
                    const res = await this.api(this.scriptUrl(), { raw: true });
                    const blob = await res.blob();
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement("a");
                    a.href = url;
                    a.download = "heartbeat-" + this.scriptDialog.id + (this.scriptDialog.os === "windows" ? ".ps1" : ".sh");
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    URL.revokeObjectURL(url);
                } catch (e) {
                    ElMessage.error(e.message);
                }
            },

            /* ---------- 管理端 ---------- */
            async loadUsers() {
                this.usersLoading = true;
                try {
                    const list = await this.api("/api/admin");
                    this.users = list || [];
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.usersLoading = false;
                }
            },
            openCreateUser() {
                this.adminCreate.form = { username: "", password: "", role: "USER" };
                this.adminCreate.visible = true;
            },
            async createUser() {
                const f = this.adminCreate.form;
                if (!f.username || !f.password) {
                    ElMessage.warning(this.t("error.credentials"));
                    return;
                }
                this.saving = true;
                try {
                    await this.api("/api/admin", { method: "POST", body: f });
                    ElMessage.success(this.t("common.success"));
                    this.adminCreate.visible = false;
                    await this.loadUsers();
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.saving = false;
                }
            },
            openChangePassword(row) {
                this.adminPwd.id = row.userId;
                this.adminPwd.password = "";
                this.adminPwd.adminPassword = "";
                this.adminPwd.visible = true;
            },
            async changePassword() {
                if (!this.adminPwd.adminPassword || !this.adminPwd.password) return;
                this.saving = true;
                try {
                    await this.api("/api/admin/" + this.adminPwd.id, { method: "PUT", body: { adminPassword: this.adminPwd.adminPassword, newPassword: this.adminPwd.password } });
                    ElMessage.success(this.t("common.success"));
                    this.adminPwd.visible = false;
                } catch (e) {
                    ElMessage.error(e.message);
                } finally {
                    this.saving = false;
                }
            },
            removeUser(row) {
                ElMessageBox.confirm(this.t("admin.confirmDelete", { name: row.name }), this.t("admin.deleteTitle"), {
                    type: "warning",
                    confirmButtonText: this.t("common.confirm"),
                    cancelButtonText: this.t("common.cancel")
                }).then(async () => {
                    try {
                        await this.api("/api/admin/" + row.userId, { method: "DELETE" });
                        ElMessage.success(this.t("common.success"));
                        await this.loadUsers();
                    } catch (e) {
                        ElMessage.error(e.message);
                    }
                }).catch(() => {});
            },

            /* ---------- 展示辅助 ---------- */
            statusText(status) {
                return this.t("status." + String(status || "unknown").toLowerCase());
            },
            statusClass(status) {
                return "status-" + String(status || "unknown").toLowerCase();
            },
            statusBadgeClass(status) {
                return "s-" + String(status || "unknown").toLowerCase();
            },
            statusIcon(status) {
                return {
                    ONLINE: "CircleCheck",
                    OFFLINE: "VideoPause",
                    PROBE: "Loading",
                    UNKNOWN: "QuestionFilled"
                }[status] || "QuestionFilled";
            },
            relativeTime(iso) {
                if (!iso) return this.t("device.never");
                const diffSec = dayjs(iso).diff(dayjs(), "second");
                const abs = Math.abs(diffSec);
                const rtf = new Intl.RelativeTimeFormat(this.lang === "zh" ? "zh-CN" : "en", { numeric: "auto" });
                if (abs < 60) return rtf.format(Math.round(diffSec), "second");
                if (abs < 3600) return rtf.format(Math.round(diffSec / 60), "minute");
                if (abs < 86400) return rtf.format(Math.round(diffSec / 3600), "hour");
                return rtf.format(Math.round(diffSec / 86400), "day");
            },

            /* ---------- 轮询 ---------- */
            startPolling() {
                this.stopPolling();
                this.pollTimer = setInterval(() => {
                    if (document.hidden) return;
                    this.refresh();
                }, 5000);
            },
            stopPolling() {
                if (this.pollTimer) {
                    clearInterval(this.pollTimer);
                    this.pollTimer = null;
                }
            },

            /* ---------- 图表 ---------- */
            updateCharts() {
                if (!this.authed || this.view !== "dashboard") return;
                const s = this.stats;
                const dark = this.theme === "dark";
                const c = {
                    legend: dark ? "#a1a1a6" : "#6e6e73",
                    label: dark ? "#f5f5f7" : "#1d1d1f",
                    pieBorder: dark ? "#1c1c1e" : "#ffffff",
                    online: dark ? "#30d158" : "#34c759",
                    offline: "#8e8e93",
                    probe: dark ? "#ff9f0a" : "#ff9500",
                    blue: dark ? "#0a84ff" : "#0071e3",
                    track: dark ? "rgba(10,132,255,0.20)" : "rgba(0,113,227,0.12)"
                };
                const statusEl = this.$refs.statusChartEl;
                const rateEl = this.$refs.rateChartEl;
                if (statusEl) {
                    if (!statusChart || statusChart.isDisposed()) statusChart = echarts.init(statusEl);
                    statusChart.setOption({
                        backgroundColor: "transparent",
                        tooltip: { trigger: "item" },
                        legend: { bottom: 0, textStyle: { color: c.legend }, itemWidth: 10, itemHeight: 10 },
                        series: [{
                            type: "pie",
                            radius: ["54%", "74%"],
                            center: ["50%", "44%"],
                            itemStyle: { borderColor: c.pieBorder, borderWidth: 2 },
                            label: {
                                show: true, position: "center",
                                formatter: () => String(s.total),
                                color: c.label, fontSize: 28, fontWeight: 700
                            },
                            labelLine: { show: false },
                            data: [
                                { value: s.online, name: this.t("status.online"), itemStyle: { color: c.online } },
                                { value: s.offline, name: this.t("status.offline"), itemStyle: { color: c.offline } },
                                { value: s.probing, name: this.t("status.probe"), itemStyle: { color: c.probe } }
                            ]
                        }]
                    });
                }
                if (rateEl) {
                    if (!rateChart || rateChart.isDisposed()) rateChart = echarts.init(rateEl);
                    const rate = s.total ? Math.round((s.online / s.total) * 100) : 0;
                    rateChart.setOption({
                        backgroundColor: "transparent",
                        series: [{
                            type: "gauge",
                            startAngle: 210,
                            endAngle: -30,
                            min: 0,
                            max: 100,
                            progress: { show: true, width: 16, itemStyle: { color: c.blue } },
                            axisLine: { lineStyle: { width: 16, color: [[1, c.track]] } },
                            axisTick: { show: false },
                            splitLine: { show: false },
                            axisLabel: { show: false },
                            pointer: { show: false },
                            anchor: { show: false },
                            detail: {
                                valueAnimation: true,
                                fontSize: 36,
                                fontWeight: 800,
                                color: c.label,
                                formatter: "{value}%",
                                offsetCenter: [0, "-4%"]
                            },
                            title: {
                                show: true,
                                offsetCenter: [0, "34%"],
                                color: c.legend,
                                fontSize: 13
                            },
                            data: [{ value: rate, name: this.t("panel.onlineRate") }]
                        }]
                    });
                }
            },
            disposeCharts() {
                if (statusChart) { statusChart.dispose(); statusChart = null; }
                if (rateChart) { rateChart.dispose(); rateChart = null; }
            },
            syncViewport() {
                if (this._mqPhone) this.viewport.phone = this._mqPhone.matches;
                if (this._mqTablet) this.viewport.tablet = this._mqTablet.matches;
                this.measureHeader();
            },
            measureHeader() {
                const header = this.$el && this.$el.querySelector ? this.$el.querySelector(".topbar") : null;
                const h = header ? header.offsetHeight : 0;
                document.documentElement.style.setProperty("--header-h", (h || 0) + "px");
            },
            onResize() {
                if (statusChart) statusChart.resize();
                if (rateChart) rateChart.resize();
                this.measureHeader();
            }
        }
    });

    app.use(ElementPlus);
    app.component("HintIcon", HintIcon);
    app.component("SudoHint", SudoHint);
    if (window.ScriptTaskPanel) {
        app.component("ScriptTaskPanel", window.ScriptTaskPanel);
    }
    if (window.ScriptCodeEditor) {
        app.component("ScriptCodeEditor", window.ScriptCodeEditor);
    }
    if (window.ScriptCheckPanel) {
        app.component("ScriptCheckPanel", window.ScriptCheckPanel);
    }
    for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
        app.component(name, comp);
    }
    loadI18n()
        .catch((err) => console.error("i18n load failed; falling back to message keys", err))
        .finally(() => app.mount("#app"));

})();
