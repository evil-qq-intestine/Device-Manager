/* ============================================================
   ScriptTaskPanel · 脚本任务（一个脚本挂多台目标设备）
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
            enabled: true,
            targetDeviceIds: []
        };
    }

    window.ScriptTaskPanel = {
        name: "ScriptTaskPanel",
        props: {
            device: { type: Object, default: null },
            devices: { type: Array, default: () => [] }
        },
        inject: ["nd"],
        template: `
<div class="stp">
  <div class="stp__bar">
    <el-button type="primary" @click="openCreate"><el-icon><Plus/></el-icon>&nbsp;{{ t('scriptTask.add') }}</el-button>
    <div class="stp__spacer"></div>
    <el-button @click="load" :loading="loading"><el-icon><Refresh/></el-icon></el-button>
  </div>

  <el-table v-if="filteredTasks.length" :data="filteredTasks" v-loading="loading" style="width:100%">
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
          <el-popover v-if="row.targets && row.targets.length > 1" placement="top" trigger="hover"
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
  <el-empty v-else :description="t('scriptTask.empty')" v-loading="loading" />

  <el-dialog v-model="editor.visible" :title="editor.id ? t('scriptTask.edit') : t('scriptTask.add')" width="680px" append-to-body>
    <el-form label-position="top">
      <el-form-item :label="t('scriptTask.name')"><el-input v-model="editor.form.name" /></el-form-item>
      <el-form-item :label="t('scriptTask.description')"><el-input v-model="editor.form.description" /></el-form-item>

      <el-form-item :label="t('scriptTask.scriptContent')">
        <el-input v-model="editor.form.scriptContent" type="textarea" :rows="6" class="mono" />
      </el-form-item>

      <el-form-item :label="t('scriptTask.targets')">
        <el-select v-model="editor.form.targetDeviceIds" multiple collapse-tags collapse-tags-tooltip
                   style="width:100%" :placeholder="t('scriptTask.targetPlaceholder')">
          <el-option v-for="d in devices" :key="d.id" :value="d.id" :label="(d.name || d.mac) + (d.sshConfigured ? '' : ' (未配置 SSH)')" />
        </el-select>
        <div class="field-hint">{{ t('scriptTask.targetsHint') }}</div>
      </el-form-item>

      <el-form-item :label="t('scriptTask.triggerType')">
        <el-radio-group v-model="editor.form.triggerType">
          <el-radio-button label="ONCE">{{ t('scriptTask.triggerOnce') }}</el-radio-button>
          <el-radio-button label="CRON">{{ t('scriptTask.triggerCron') }}</el-radio-button>
          <el-radio-button label="ON_BOOT">{{ t('scriptTask.triggerOnBoot') }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="editor.form.triggerType === 'ONCE'" :label="t('scriptTask.executeAt')">
        <el-date-picker v-model="editor.form.executeAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
      </el-form-item>
      <el-form-item v-if="editor.form.triggerType === 'CRON'" :label="t('scriptTask.cronExpression')">
        <el-input v-model="editor.form.cronExpression" placeholder="0 0 3 * * *" class="mono" />
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
      <el-form-item :label="t('scriptTask.enabled')"><el-switch v-model="editor.form.enabled" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editor.visible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="logs.visible" :title="t('scriptTask.logs') + (logs.taskName ? ' · ' + logs.taskName : '')" width="820px" append-to-body>
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
                editor: { visible: false, id: null, form: emptyForm() },
                logs: { visible: false, taskId: null, taskName: "", items: [], total: 0, page: 0, size: 10, loading: false, detail: null }
            };
        },
        computed: {
            filteredTasks() {
                if (!this.device) return this.tasks;
                return this.tasks.filter((t) => (t.targets || []).some((tg) => tg.deviceId === this.device.id));
            }
        },
        created() {
            this.load();
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
            openCreate() {
                this.editor.id = null;
                this.editor.form = emptyForm();
                if (this.device) this.editor.form.targetDeviceIds = [this.device.id];
                this.editor.visible = true;
            },
            openEdit(row) {
                this.editor.id = row.id;
                this.editor.form = {
                    name: row.name,
                    description: row.description || "",
                    scriptContent: row.scriptContent || "",
                    triggerType: row.triggerType,
                    executeAt: row.executeAt || "",
                    cronExpression: row.cronExpression || "",
                    shutdownMode: row.shutdownMode || "NONE",
                    shutdownDelaySeconds: row.shutdownDelaySeconds || 60,
                    enabled: !!row.enabled,
                    targetDeviceIds: (row.targets || []).map((tg) => tg.deviceId)
                };
                this.editor.visible = true;
            },
            async save() {
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
                    enabled: !!f.enabled,
                    targetDeviceIds: f.targetDeviceIds
                };

                this.saving = true;
                try {
                    if (this.editor.id) {
                        await this.api("/api/script-task/" + this.editor.id, { method: "PUT", body });
                    } else {
                        await this.api("/api/script-task", { method: "POST", body });
                    }
                    this.$message.success(this.t("common.success"));
                    this.editor.visible = false;
                    await this.load();
                } catch (e) {
                    this.$message.error(e.message);
                } finally {
                    this.saving = false;
                }
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
                return { ONCE: this.t("scriptTask.triggerOnce"), CRON: this.t("scriptTask.triggerCron"), ON_BOOT: this.t("scriptTask.triggerOnBoot") }[type] || type;
            },
            triggeredByLabel(by) {
                return { SCHEDULE: this.t("scriptTask.triggerSchedule"), HEARTBEAT: this.t("scriptTask.triggerHeartbeat"), MANUAL: this.t("scriptTask.triggerManual") }[by] || by;
            }
        },
        mounted() {
            this._alive = true;
        },
        beforeUnmount() {
            this._alive = false;
        }
    };
})();
