/* ============================================================
   ScriptTaskPanel · 脚本任务（一个脚本挂多台目标设备）
   列表（表格）与 VS Code 排版的全页编辑器两态切换：
   - 列表：表格 + 设备过滤 + 新建/执行/日志/关机/编辑
   - 编辑器：左 Explorer（全部脚本 + 设备树）、中 Monaco + Problems + 状态栏、
             右配置栏；平滑过渡（vsc-push）
   ============================================================ */
(function () {
    "use strict";

    function emptyForm() {
        return {
            name: "",
            description: "",
            scriptContent: "",
            triggerType: "ON_BOOT",
            executeAt: "",
            cronExpression: "",
            shutdownMode: "NONE",
            shutdownDelaySeconds: 60,
            wakeLeadSeconds: 0,
            enabled: true,
            targetDeviceIds: []
        };
    }

    window.ScriptTaskPanel = {
        name: "ScriptTaskPanel",
        props: {
            devices: { type: Array, default: () => [] }
        },
        inject: ["nd"],
        emits: ["mode-change"],
        template: `
<div class="stp">
  <transition name="vsc-push" mode="out-in" @after-enter="onTabsAfterEnter">
    <div v-if="mode === 'list'" key="list" class="stp__list">
      <div class="stp__bar">
        <el-button type="primary" @click="openCreate"><el-icon><Plus/></el-icon>&nbsp;{{ t('scriptTask.add') }}</el-button>
        <el-select v-model="selectedDeviceId" clearable filterable :placeholder="t('scriptTask.allDevices')" class="stp__device-select">
          <el-option v-for="d in devices" :key="d.id" :value="d.id" :label="d.name || d.mac" />
        </el-select>
        <div class="stp__spacer"></div>
        <el-button @click="load" :loading="loading"><el-icon><Refresh/></el-icon></el-button>
      </div>

      <template v-if="filteredTasks.length">
      <div v-if="phone" class="stp-cards" v-loading="loading">
        <div v-for="row in filteredTasks" :key="row.id" class="stp-card glass">
          <div class="stp-card__head">
            <div class="stp-card__title">
              <div class="stp__name">{{ row.name }}</div>
              <div class="stp__desc" v-if="row.description">{{ row.description }}</div>
            </div>
            <el-switch :model-value="row.enabled" @change="toggle(row)" />
          </div>
          <div class="stp-card__meta">
            <span class="chip">{{ triggerLabel(row.triggerType) }}</span>
            <span :class="resultSummary(row).cls">{{ resultSummary(row).text }}</span>
          </div>
          <div class="stp__targets" v-if="row.targets && row.targets.length">
            <span v-for="tg in row.targets" :key="tg.deviceId" class="chip" :class="{ 'chip--warn': !tg.sshConfigured }">
              {{ tg.name || tg.mac }}
            </span>
          </div>
          <div class="stp-card__actions">
            <el-button size="small" type="primary" @click="execute(row)">{{ t('scriptTask.execute') }}</el-button>
            <el-button size="small" @click="openLogs(row)">{{ t('scriptTask.logs') }}</el-button>
            <el-button size="small" type="warning" plain @click="shutdown(row)">{{ t('scriptTask.shutdown') }}</el-button>
            <el-button size="small" @click="openEdit(row)"><el-icon><Edit/></el-icon></el-button>
            <el-button size="small" type="danger" @click="remove(row)"><el-icon><Delete/></el-icon></el-button>
          </div>
        </div>
      </div>
      <el-table v-else :data="filteredTasks" v-loading="loading" style="width:100%">
        <el-table-column :label="t('scriptTask.name')" min-width="150">
          <template #default="{ row }">
            <div class="stp__name">{{ row.name }}</div>
            <div class="stp__desc" v-if="row.description">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('scriptTask.targets')" min-width="160" class-name="stp__targets-cell">
          <template #default="{ row }">
            <div class="stp__targets">
              <span v-for="tg in (row.targets || []).slice(0, 1)" :key="tg.deviceId"
                    class="chip" :class="{ 'chip--warn': !tg.sshConfigured }">
                {{ tg.name || tg.mac }}
              </span>
              <el-popover v-if="row.targets && row.targets.length > 1" placement="top" :trigger="phone ? 'click' : 'hover'"
                          :width="240" popper-class="stp__targets-popper">
                <template #reference>
                  <span class="chip chip--more">+{{ row.targets.length - 1 }}</span>
                </template>
                <div class="stp__targets-list">
                  <div v-for="tg in row.targets" :key="tg.deviceId" class="stp__targets-item">
                    <span class="stp__targets-name">{{ tg.name || tg.mac }}</span>
                    <span v-if="!tg.sshConfigured" class="stp__warn">{{ t('deviceSsh.notConfigured') }}</span>
                    <span v-else-if="tg.lastRunning" class="stp__muted">{{ t('scriptTask.running') }}</span>
                    <span v-else-if="tg.lastExitCode == null" class="stp__muted">{{ t('scriptTask.never') }}</span>
                    <span v-else :class="tg.lastExitCode === 0 ? 'stp__ok' : 'stp__fail'">
                      {{ tg.lastExitCode === 0 ? t('scriptTask.success') : t('scriptTask.failed') }}
                    </span>
                  </div>
                </div>
              </el-popover>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('scriptTask.triggerType')" width="110">
          <template #default="{ row }"><span class="chip">{{ triggerLabel(row.triggerType) }}</span></template>
        </el-table-column>
        <el-table-column :label="t('scriptTask.lastResult')" width="120">
          <template #default="{ row }">
            <span :class="resultSummary(row).cls">{{ resultSummary(row).text }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('scriptTask.enabled')" width="80">
          <template #default="{ row }"><el-switch :model-value="row.enabled" @change="toggle(row)" /></template>
        </el-table-column>
        <el-table-column :label="t('admin.actions')" width="330" align="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="execute(row)">{{ t('scriptTask.execute') }}</el-button>
            <el-button size="small" @click="openLogs(row)">{{ t('scriptTask.logs') }}</el-button>
            <el-button size="small" type="warning" plain @click="shutdown(row)">{{ t('scriptTask.shutdown') }}</el-button>
            <el-button size="small" @click="openEdit(row)"><el-icon><Edit/></el-icon></el-button>
            <el-button size="small" type="danger" @click="remove(row)"><el-icon><Delete/></el-icon></el-button>
          </template>
        </el-table-column>
      </el-table>
      </template>
      <el-empty v-else :description="t('scriptTask.empty')" v-loading="loading" />
    </div>

    <div v-else key="editor" class="stp__editor">
      <div class="vsc">
        <div class="vsc__scrim" :class="{ on: !!drawer }" @click="drawer = null"></div>
        <aside class="vsc__pane vsc__explorer" :class="{ 'is-open': drawer === 'explorer' }">
          <div class="vsc__pane-head">
            <span class="vsc__pane-title">{{ t('scriptExplorer.title') }}</span>
            <span class="vsc__pane-close" @click="drawer = null"><el-icon><Close/></el-icon></span>
            <el-button size="small" type="primary" plain @click="openCreate">
              <el-icon><Plus/></el-icon>&nbsp;{{ t('scriptExplorer.new') }}</el-button>
          </div>
          <div class="vsc__tree">
            <div class="vsc__group">
              <div class="vsc__group-title" @click="explorer.allOpen = !explorer.allOpen">
                <span class="vsc__chevron" :class="{ open: explorer.allOpen }">▸</span>
                <span class="vsc__group-name">{{ t('scriptExplorer.allScripts') }}</span>
                <span class="vsc__group-count">{{ filteredTasks.length }}</span>
              </div>
              <div class="vsc__group-body" v-show="explorer.allOpen">
                <div v-for="task in filteredTasks" :key="task.id" class="vsc__file"
                     :class="{ active: editor.id === task.id }" @click="openEdit(task)">
                  <span class="vsc__file-icon" :class="task.enabled ? 'is-on' : 'is-off'">$</span>
                  <span class="vsc__file-name">{{ task.name }}</span>
                </div>
                <div v-if="!tasks.length" class="vsc__empty">{{ t('scriptTask.empty') }}</div>
                <div v-else-if="selectedDeviceId != null && !filteredTasks.length" class="vsc__empty">{{ t('scriptExplorer.noTarget') }}</div>
              </div>
            </div>
            <div class="vsc__group">
              <div class="vsc__group-title" @click="explorer.deviceOpen = !explorer.deviceOpen">
                <span class="vsc__chevron" :class="{ open: explorer.deviceOpen }">▸</span>
                <span class="vsc__group-name">{{ t('scriptExplorer.devices') }}</span>
                <span class="vsc__group-count">{{ devices.length }}</span>
              </div>
              <div class="vsc__group-body" v-show="explorer.deviceOpen">
                <div class="vsc__file" :class="{ active: selectedDeviceId == null }" @click="selectedDeviceId = null">
                  <span class="vsc__file-icon is-all">⊞</span>
                  <span class="vsc__file-name">{{ t('scriptTask.allDevices') }}</span>
                </div>
                <div v-for="d in devices" :key="d.id">
                  <div class="vsc__file" :class="{ active: selectedDeviceId === d.id }" @click="toggleDevice(d)">
                    <span class="vsc__chevron" :class="{ open: isDeviceOpen(d.id) }">▸</span>
                    <span class="vsc__device-dot" :class="deviceStatusClass(d.status)"></span>
                    <span class="vsc__file-name">{{ d.name || d.mac }}</span>
                    <span class="vsc__chip" v-if="d.sshConfigured">SSH</span>
                  </div>
                  <div class="vsc__group-body" v-show="isDeviceOpen(d.id)">
                    <div v-for="task in deviceTasks(d)" :key="task.id" class="vsc__file vsc__file--child"
                         :class="{ active: editor.id === task.id }" @click="openEdit(task)">
                      <span class="vsc__file-icon" :class="task.enabled ? 'is-on' : 'is-off'">$</span>
                      <span class="vsc__file-name">{{ task.name }}</span>
                    </div>
                    <div v-if="!deviceTasks(d).length" class="vsc__empty">{{ t('scriptExplorer.noTasks') }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </aside>

        <section class="vsc__center">
          <div class="vsc__tabs">
            <span class="vsc__drawer-btn" @click="drawer = drawer === 'explorer' ? null : 'explorer'"><el-icon><Menu/></el-icon></span>
            <span class="vsc__back" @click="closeActive"><el-icon><Back/></el-icon>&nbsp;{{ t('scriptExplorer.back') }}</span>
            <div class="vsc__tabs-fade" :class="{ 'is-left': tabsView.left, 'is-right': tabsView.right }">
              <div class="vsc__tabs-track" ref="tabsTrack" @scroll="onTabsScroll">
                <div v-for="tab in tabs" :key="tab.key" class="vsc__tab"
                     :class="{ active: tab.key === activeKey }" @click="activate(tab)">
                  <span class="vsc__tab-name">{{ tabLabel(tab) }}</span>
                  <span class="vsc__tab-dot" v-if="tabDirty(tab)"></span>
                  <span class="vsc__tab-close" @click.stop="closeTab(tab)"><el-icon><Close/></el-icon></span>
                </div>
              </div>
            </div>
            <div class="vsc__tabs-nav" :class="{ on: tabsView.overflow }">
              <span class="vsc__tabs-arrow" :class="{ off: !tabsView.left }" @click="scrollTabs(-1)"><el-icon><ArrowLeft/></el-icon></span>
              <span class="vsc__tabs-arrow" :class="{ off: !tabsView.right }" @click="scrollTabs(1)"><el-icon><ArrowRight/></el-icon></span>
            </div>
            <span class="vsc__drawer-btn vsc__drawer-btn--right" @click="drawer = drawer === 'config' ? null : 'config'"><el-icon><Setting/></el-icon></span>
          </div>
          <div class="vsc__editor">
            <script-check-panel ref="scp" v-model="editor.form.scriptContent" :language="editor.lang"
                                page @update:language="v => editor.lang = v"
                                @update:diagnostics="onDiagnostics" />
          </div>
          <div class="vsc__problems" :class="{ open: problems.open }">
            <div class="vsc__problems-head" @click="problems.open = !problems.open">
              <span class="vsc__problems-title">
                <span class="vsc__chevron" :class="{ open: problems.open }">▸</span>
                {{ t('scriptCheck.problems') }}
              </span>
              <span v-if="problemErrors" class="scp__problems-count is-error">{{ t('scriptCheck.errors', { n: problemErrors }) }}</span>
              <span v-if="problemWarnings" class="scp__problems-count is-warn">{{ t('scriptCheck.warnings', { n: problemWarnings }) }}</span>
            </div>
            <div class="vsc__problems-body" v-show="problems.open">
              <div v-if="!problems.items.length" class="vsc__empty">{{ t('scriptCheck.noIssues') }}</div>
              <div v-for="(d, i) in problems.items" :key="i" class="vsc__problem" @click="gotoProblem(d)">
                <span class="scp__sev" :class="'is-' + d.severity">{{ d.severity === 'error' ? t('scriptCheck.error') : t('scriptCheck.warning') }}</span>
                <span class="vsc__problem-msg">{{ d.message }}</span>
                <span class="vsc__problem-loc">{{ d.line }}:{{ d.col }}</span>
              </div>
            </div>
          </div>
          <div class="vsc__statusbar">
            <el-icon :size="11"><Monitor/></el-icon><span>{{ langName }}</span>
            <span>Spaces: 4</span>
            <span>UTF-8</span>
            <span class="vsc__statusbar-spacer"></span>
            <span @click="problems.open = !problems.open">{{ t('scriptCheck.problems') }}: {{ problems.items.length }}</span>
          </div>
        </section>

        <aside class="vsc__pane vsc__inspector" :class="{ 'is-open': drawer === 'config' }">
          <div class="vsc__pane-head">
            <span class="vsc__pane-title">{{ t('scriptExplorer.config') }}</span>
            <span class="vsc__pane-close" @click="drawer = null"><el-icon><Close/></el-icon></span>
          </div>
          <div class="vsc__inspector-body">
            <el-form label-position="top">
              <el-form-item :label="t('scriptTask.name')">
                <el-input v-model="editor.form.name" placeholder="my-script.sh / shutdown.ps1" />
              </el-form-item>
              <el-form-item :label="t('scriptTask.description')">
                <el-input v-model="editor.form.description" type="textarea" :rows="2" />
              </el-form-item>
              <el-form-item :label="t('scriptTask.targets')">
                <el-select v-model="editor.form.targetDeviceIds" multiple collapse-tags collapse-tags-tooltip
                           style="width:100%" :placeholder="t('scriptTask.targetPlaceholder')">
                  <el-option v-for="d in devices" :key="d.id" :value="d.id"
                             :label="(d.name || d.mac) + (d.sshConfigured ? '' : ' (' + t('deviceSsh.notConfigured') + ')')" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('scriptTask.triggerType')">
                <el-radio-group v-model="editor.form.triggerType">
                  <el-radio-button label="MANUAL">{{ t('scriptTask.triggerManual') }}</el-radio-button>
                  <el-radio-button label="ONCE">{{ t('scriptTask.triggerOnce') }}</el-radio-button>
                  <el-radio-button label="CRON">{{ t('scriptTask.triggerCron') }}</el-radio-button>
                  <el-radio-button label="ON_BOOT">{{ t('scriptTask.triggerOnBoot') }}</el-radio-button>
                </el-radio-group>
                <div v-if="editor.form.triggerType === 'MANUAL'" class="field-hint">{{ t('scriptTask.triggerManualHint') }}</div>
              </el-form-item>
              <el-form-item v-if="editor.form.triggerType === 'ONCE'" :label="t('scriptTask.executeAt')">
                <el-date-picker v-model="editor.form.executeAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
              </el-form-item>
              <el-form-item v-if="editor.form.triggerType === 'CRON'" :label="t('scriptTask.cronExpression')">
                <el-input v-model="editor.form.cronExpression" placeholder="0 0 3 * * *" class="mono" />
              </el-form-item>
              <el-form-item v-if="editor.form.triggerType === 'ONCE' || editor.form.triggerType === 'CRON'" :label="t('scriptTask.wakeLead')">
                <el-input-number v-model="editor.form.wakeLeadSeconds" :min="0" :max="86400" style="width:100%" />
                <div class="field-hint">{{ t('scriptTask.wakeLeadHint') }}</div>
              </el-form-item>
              <el-form-item :label="t('scriptTask.shutdownMode')">
                <el-select v-model="editor.form.shutdownMode" style="width:100%">
                  <el-option :label="t('scriptTask.shutdownNone')" value="NONE" />
                  <el-option :label="t('scriptTask.shutdownImmediate')" value="IMMEDIATE" />
                  <el-option :label="t('scriptTask.shutdownDelayed')" value="DELAYED" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="editor.form.shutdownMode === 'DELAYED'" :label="t('scriptTask.shutdownDelay')">
                <el-input-number v-model="editor.form.shutdownDelaySeconds" :min="1" :max="86400" style="width:100%" />
              </el-form-item>
              <el-form-item v-if="editor.form.triggerType !== 'MANUAL'" :label="t('scriptTask.enabled')">
                <el-switch v-model="editor.form.enabled" />
              </el-form-item>
              <div class="vsc__inspector-actions">
                <el-button type="primary" :loading="saving" @click="save">
                  <el-icon><Check/></el-icon>&nbsp;{{ t('common.save') }}</el-button>
                <el-button @click="closeActive">{{ t('common.cancel') }}</el-button>
                <el-button v-if="editor.id" type="danger" plain @click="removeCurrent">
                  <el-icon><Delete/></el-icon>&nbsp;{{ t('scriptTask.deleteTitle') }}</el-button>
              </div>
              <div class="field-hint">{{ t('scriptCheck.hint') }}</div>
            </el-form>
          </div>
        </aside>
      </div>
    </div>
  </transition>

  <el-dialog v-model="logs.visible" :title="t('scriptTask.logs') + (logs.taskName ? ' · ' + logs.taskName : '')" width="820px" :fullscreen="phone" append-to-body>
    <el-table :data="logs.items" v-loading="logs.loading" style="width:100%" @row-click="openLogDetail">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column :label="t('scriptTask.device')" min-width="130">
        <template #default="{ row }">{{ deviceName(row.deviceId) }}</template>
      </el-table-column>
      <el-table-column :label="t('scriptTask.triggeredBy')" width="100">
        <template #default="{ row }">{{ triggeredByLabel(row.triggeredBy) }}</template>
      </el-table-column>
      <el-table-column :label="t('scriptTask.exitCode')" width="90">
        <template #default="{ row }">
          <span v-if="row.running" class="stp__muted">{{ t('scriptTask.running') }}</span>
          <span v-else :class="row.exitCode === 0 ? 'stp__ok' : 'stp__fail'">{{ row.exitCode }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="startedAt" :label="t('scriptTask.startedAt')" min-width="170" />
    </el-table>
    <el-pagination v-if="logs.total > logs.size" layout="prev, pager, next" :total="logs.total"
                   :page-size="logs.size" :current-page="logs.page + 1" @current-change="p => loadLogs(logs.taskId, p - 1)" />

    <div v-if="logs.detail" class="stp__detail">
      <div class="stp__detail-head">
        <b>{{ t('scriptTask.detail') }}</b>
        <span class="stp__muted">#{{ logs.detail.id }} · {{ deviceName(logs.detail.deviceId) }}</span>
      </div>
      <div class="stp__detail-label">{{ t('scriptTask.stdout') }}</div>
      <pre class="stp__pre">{{ logs.detail.stdout || '-' }}</pre>
      <div class="stp__detail-label">{{ t('scriptTask.stderr') }}</div>
      <pre class="stp__pre">{{ logs.detail.stderr || '-' }}</pre>
      <div v-if="logs.detail.errorMessage" class="stp__detail-label stp__fail">{{ logs.detail.errorMessage }}</div>
    </div>
  </el-dialog>
</div>
`,
        data() {
            return {
                tasks: [],
                loading: false,
                saving: false,
                mode: "list",
                selectedDeviceId: null,
                explorer: { allOpen: true, deviceOpen: true, expanded: [] },
                problems: { open: true, items: [] },
                tabs: [],
                activeKey: null,
                _tabSeq: 0,
                tabsView: { overflow: false, left: false, right: false },
                drawer: null,
                logs: { visible: false, taskId: null, taskName: "", items: [], total: 0, page: 0, size: 10, loading: false, detail: null }
            };
        },
        computed: {
            phone() {
                return !!(this.nd && this.nd.viewport && this.nd.viewport.phone);
            },
            filteredTasks() {
                if (this.selectedDeviceId == null) return this.tasks;
                return this.tasks.filter((t) => (t.targets || []).some((tg) => tg.deviceId === this.selectedDeviceId));
            },
            editor() {
                const t = this.currentTab();
                return t ? t : { id: null, lang: "BASH", form: emptyForm(), saved: null };
            },
            langName() {
                return this.editor.lang === "POWERSHELL" ? "PowerShell" : "Bash";
            },
            problemErrors() {
                return this.problems.items.filter((d) => d.severity === "error").length;
            },
            problemWarnings() {
                return this.problems.items.length - this.problemErrors;
            }
        },
        created() {
            this.load();
        },
        updated() {
            this.scheduleTabsMeasure();
        },
        watch: {
            mode(v) {
                this.$emit("mode-change", v);
                this.drawer = null;
                this.scheduleTabsMeasure();
            },
            activeKey() {
                this.$nextTick(() => {
                    this.scrollActiveIntoView();
                    this.measureTabs();
                });
            }
        },
        methods: {
            t(key, params) {
                return this.nd.t(key, params);
            },
            api(path, options) {
                return this.nd.api(path, options);
            },
            deviceName(id) {
                const d = this.devices.find((x) => x.id === id);
                return d ? (d.name || d.mac) : ("#" + id);
            },
            deviceStatusClass(status) {
                return "s-" + String(status || "unknown").toLowerCase();
            },
            deviceTasks(d) {
                return this.tasks.filter((t) => (t.targets || []).some((tg) => tg.deviceId === d.id));
            },
            isDeviceOpen(id) {
                return this.explorer.expanded.indexOf(id) >= 0;
            },
            toggleDevice(d) {
                const i = this.explorer.expanded.indexOf(d.id);
                if (i >= 0) this.explorer.expanded.splice(i, 1);
                else this.explorer.expanded.push(d.id);
                this.selectedDeviceId = d.id;
            },
            async load() {
                this.loading = true;
                try {
                    this.tasks = await this.api("/api/script-task");
                } catch (e) {
                    this.$message.error(e.message);
                } finally {
                    this.loading = false;
                }
            },
            snapshotForm(f) {
                return JSON.parse(JSON.stringify(f || this.editor.form));
            },
            currentTab() {
                return this.tabs.find((t) => t.key === this.activeKey) || this.tabs[0] || null;
            },
            tabLabel(t) {
                return (t.form && t.form.name) || this.t("scriptExplorer.untitled");
            },
            tabDirty(t) {
                return !!(t.saved && JSON.stringify(t.form) !== JSON.stringify(t.saved));
            },
            openCreate() {
                const form = emptyForm();
                if (this.selectedDeviceId != null) form.targetDeviceIds = [this.selectedDeviceId];
                const tab = {
                    key: "n" + (++this._tabSeq),
                    taskId: null,
                    lang: "BASH",
                    form,
                    saved: JSON.parse(JSON.stringify(form))
                };
                this.tabs.push(tab);
                this.activeKey = tab.key;
                this.problems.items = [];
                this.mode = "editor";
            },
            openEdit(row) {
                const existing = this.tabs.find((t) => t.taskId === row.id);
                if (existing) {
                    this.activeKey = existing.key;
                    this.drawer = null;
                    this.mode = "editor";
                    return;
                }
                const form = {
                    name: row.name,
                    description: row.description || "",
                    scriptContent: row.scriptContent || "",
                    triggerType: row.triggerType,
                    executeAt: row.executeAt || "",
                    cronExpression: row.cronExpression || "",
                    shutdownMode: row.shutdownMode || "NONE",
                    shutdownDelaySeconds: row.shutdownDelaySeconds || 60,
                    wakeLeadSeconds: row.wakeLeadSeconds || 0,
                    enabled: !!row.enabled,
                    targetDeviceIds: (row.targets || []).map((tg) => tg.deviceId)
                };
                const tab = { key: "t" + row.id, taskId: row.id, lang: "BASH", form, saved: this.snapshotForm(form) };
                this.tabs.push(tab);
                this.activeKey = tab.key;
                this.problems.items = [];
                this.drawer = null;
                this.mode = "editor";
            },
            activate(tab) {
                if (tab) this.activeKey = tab.key;
            },
            closeTabNow(tab) {
                const idx = this.tabs.indexOf(tab);
                if (idx < 0) return;
                this.tabs.splice(idx, 1);
                if (this.activeKey === tab.key) {
                    this.activeKey = this.tabs.length ? this.tabs[Math.min(idx, this.tabs.length - 1)].key : null;
                }
                if (!this.tabs.length) this.mode = "list";
            },
            closeTab(tab) {
                if (!tab) return;
                if (this.tabDirty(tab)) {
                    this.$confirm(this.t("scriptExplorer.unsaved"), this.t("scriptExplorer.back"), {
                        type: "warning",
                        confirmButtonText: this.t("common.confirm"),
                        cancelButtonText: this.t("common.cancel")
                    }).then(() => this.closeTabNow(tab)).catch(() => {});
                } else {
                    this.closeTabNow(tab);
                }
            },
            closeActive() {
                this.closeTab(this.currentTab());
            },
            onDiagnostics(list) {
                this.problems.items = list || [];
            },
            gotoProblem(d) {
                if (this.$refs.scp) this.$refs.scp.goto(d);
            },
            scheduleTabsMeasure() {
                if (this._tabsRaf) return;
                this._tabsRaf = requestAnimationFrame(() => {
                    this._tabsRaf = null;
                    this.measureTabs();
                });
            },
            measureTabs() {
                const el = this.$refs.tabsTrack;
                if (!el) {
                    this.tabsView.overflow = false;
                    this.tabsView.left = false;
                    this.tabsView.right = false;
                    return;
                }
                const max = el.scrollWidth - el.clientWidth;
                const overflow = max > 2;
                this.tabsView.overflow = overflow;
                this.tabsView.left = overflow && el.scrollLeft > 2;
                this.tabsView.right = overflow && el.scrollLeft < max - 2;
            },
            onTabsScroll() {
                this.measureTabs();
            },
            scrollTabs(dir) {
                const el = this.$refs.tabsTrack;
                if (!el) return;
                el.scrollBy({ left: dir * Math.max(el.clientWidth - 64, 96), behavior: "smooth" });
            },
            scrollActiveIntoView() {
                const el = this.$refs.tabsTrack;
                if (!el) return;
                const act = el.querySelector(".vsc__tab.active");
                if (!act) return;
                const bar = el.getBoundingClientRect();
                const tab = act.getBoundingClientRect();
                const pad = 6;
                if (tab.left < bar.left + pad) el.scrollLeft -= bar.left + pad - tab.left;
                else if (tab.right > bar.right - pad) el.scrollLeft += tab.right - bar.right + pad;
            },
            onTabsAfterEnter() {
                this.$nextTick(() => {
                    this.measureTabs();
                    this.scrollActiveIntoView();
                });
            },
            async save() {
                const tab = this.currentTab();
                if (!tab) return;
                const f = this.editor.form;
                if (!f.name || !f.scriptContent) {
                    this.$message.warning(this.t("scriptTask.name"));
                    return;
                }
                if (!f.targetDeviceIds.length) {
                    this.$message.warning(this.t("scriptTask.targetsHint"));
                    return;
                }
                if (f.triggerType === "ONCE" && !f.executeAt) {
                    this.$message.warning(this.t("scriptTask.executeAt"));
                    return;
                }
                if (f.triggerType === "CRON" && !f.cronExpression) {
                    this.$message.warning(this.t("scriptTask.cronExpression"));
                    return;
                }

                const body = {
                    name: f.name,
                    description: f.description,
                    scriptContent: f.scriptContent,
                    triggerType: f.triggerType,
                    executeAt: f.triggerType === "ONCE" ? f.executeAt : null,
                    cronExpression: f.triggerType === "CRON" ? f.cronExpression : null,
                    shutdownMode: f.shutdownMode,
                    shutdownDelaySeconds: f.shutdownMode === "DELAYED" ? Number(f.shutdownDelaySeconds) : null,
                    wakeLeadSeconds: (f.triggerType === "ONCE" || f.triggerType === "CRON") ? (Number(f.wakeLeadSeconds) || 0) : null,
                    enabled: !!f.enabled,
                    targetDeviceIds: f.targetDeviceIds
                };

                this.saving = true;
                try {
                    if (tab.taskId) {
                        await this.api("/api/script-task/" + tab.taskId, { method: "PUT", body });
                    } else {
                        await this.api("/api/script-task", { method: "POST", body });
                    }
                    this.$message.success(this.t("common.success"));
                    await this.load();
                    this.closeTabNow(tab);
                } catch (e) {
                    this.$message.error(e.message);
                } finally {
                    this.saving = false;
                }
            },
            removeCurrent() {
                const tab = this.currentTab();
                if (!tab || !tab.taskId) return;
                this.remove({ id: tab.taskId, name: tab.form.name });
            },
            async toggle(row) {
                try {
                    const updated = await this.api("/api/script-task/" + row.id + "/toggle", { method: "PATCH" });
                    row.enabled = updated.enabled;
                } catch (e) {
                    this.$message.error(e.message);
                }
            },
            remove(row) {
                this.$confirm(this.t("scriptTask.confirmDelete", { name: row.name }), this.t("scriptTask.deleteTitle"), {
                    type: "warning",
                    confirmButtonText: this.t("common.confirm"),
                    cancelButtonText: this.t("common.cancel")
                }).then(async () => {
                    try {
                        await this.api("/api/script-task/" + row.id, { method: "DELETE" });
                        this.$message.success(this.t("common.success"));
                        this.tabs = this.tabs.filter((t) => t.taskId !== row.id);
                        if (this.tabs.length) {
                            if (!this.tabs.some((t) => t.key === this.activeKey)) this.activeKey = this.tabs[0].key;
                        } else {
                            this.mode = "list";
                        }
                        await this.load();
                    } catch (e) {
                        this.$message.error(e.message);
                    }
                }).catch(() => {});
            },
            execute(row) {
                this.$confirm(this.t("scriptTask.confirmExecute", { name: row.name }), this.t("scriptTask.execute"), {
                    confirmButtonText: this.t("common.confirm"),
                    cancelButtonText: this.t("common.cancel")
                }).then(async () => {
                    try {
                        const res = await this.api("/api/script-task/" + row.id + "/execute", { method: "POST" });
                        const logIds = (res && res.logIds) || [];
                        this.$message.info(this.t("scriptTask.executeSubmitted", { n: logIds.length }));
                        this.pollLogs(logIds);
                    } catch (e) {
                        this.$message.error(e.message);
                    }
                }).catch(() => {});
            },
            async pollLogs(logIds) {
                const pending = new Set(logIds);
                for (let i = 0; i < 160 && pending.size; i++) {
                    await new Promise((r) => setTimeout(r, 1500));
                    if (!this._alive) return;
                    for (const id of Array.from(pending)) {
                        try {
                            const d = await this.api("/api/script-task-log/" + id);
                            if (d && !d.running) pending.delete(id);
                        } catch (e) {
                            pending.delete(id);
                        }
                    }
                }
                let failed = 0;
                for (const id of logIds) {
                    try {
                        const d = await this.api("/api/script-task-log/" + id);
                        if (d && d.exitCode !== 0) failed++;
                    } catch (e) { /* ignore */ }
                }
                if (failed) this.$message.warning(this.t("scriptTask.executeFailCount", { n: failed }));
                else this.$message.success(this.t("scriptTask.executeOk"));
                this.load();
            },
            shutdown(row) {
                this.$confirm(this.t("scriptTask.confirmShutdown", { name: row.name }), this.t("scriptTask.shutdown"), {
                    type: "warning",
                    confirmButtonText: this.t("common.confirm"),
                    cancelButtonText: this.t("common.cancel")
                }).then(async () => {
                    try {
                        const res = await this.api("/api/script-task/" + row.id + "/shutdown", { method: "POST" });
                        const results = (res && res.results) || [];
                        const failed = results.filter((r) => !r.shutdownTriggered);
                        if (!failed.length) this.$message.success(this.t("device.shutdownSent"));
                        else this.$message.error(failed.map((r) => (r.name || ("#" + r.deviceId)) + ": " + (r.errorMessage || "failed")).join("; "));
                    } catch (e) {
                        this.$message.error(e.message);
                    }
                }).catch(() => {});
            },
            openLogs(row) {
                this.logs.taskId = row.id;
                this.logs.taskName = row.name;
                this.logs.detail = null;
                this.logs.visible = true;
                this.loadLogs(row.id, 0);
            },
            async loadLogs(taskId, page) {
                this.logs.loading = true;
                try {
                    const res = await this.api("/api/script-task/" + taskId + "/logs?page=" + page + "&size=" + this.logs.size);
                    this.logs.items = res.content || [];
                    this.logs.total = res.total || 0;
                    this.logs.page = res.page || 0;
                } catch (e) {
                    this.$message.error(e.message);
                } finally {
                    this.logs.loading = false;
                }
            },
            async openLogDetail(row) {
                try {
                    this.logs.detail = await this.api("/api/script-task-log/" + row.id);
                } catch (e) {
                    this.$message.error(e.message);
                }
            },
            resultSummary(row) {
                const targets = row.targets || [];
                const done = targets.filter((tg) => tg.lastExitCode != null);
                if (!done.length) {
                    const running = targets.some((tg) => tg.lastRunning);
                    return running
                        ? { text: this.t("scriptTask.running"), cls: "stp__muted" }
                        : { text: this.t("scriptTask.never"), cls: "stp__muted" };
                }
                const failed = done.filter((tg) => tg.lastExitCode !== 0).length;
                const ok = done.length - failed;
                return { text: ok + "/" + targets.length, cls: failed ? "stp__fail" : "stp__ok" };
            },
            triggerLabel(type) {
                return { MANUAL: this.t("scriptTask.triggerManual"), ONCE: this.t("scriptTask.triggerOnce"), CRON: this.t("scriptTask.triggerCron"), ON_BOOT: this.t("scriptTask.triggerOnBoot") }[type] || type;
            },
            triggeredByLabel(by) {
                return { SCHEDULE: this.t("scriptTask.triggerSchedule"), HEARTBEAT: this.t("scriptTask.triggerHeartbeat"), MANUAL: this.t("scriptTask.triggerManual") }[by] || by;
            }
        },
        mounted() {
            this._alive = true;
            this.$emit("mode-change", this.mode);
            this._onTabsResize = () => this.scheduleTabsMeasure();
            window.addEventListener("resize", this._onTabsResize);
            this.scheduleTabsMeasure();
        },
        beforeUnmount() {
            this._alive = false;
            if (this._onTabsResize) window.removeEventListener("resize", this._onTabsResize);
            if (this._tabsRaf) cancelAnimationFrame(this._tabsRaf);
        }
    };
})();