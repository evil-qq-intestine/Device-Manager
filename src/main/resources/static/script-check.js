/* ============================================================
   ScriptCheck · 脚本代码结构检查（纯前端，无后端）
   - ScriptChecker.check(code, language) -> diagnostic[]
     diagnostic = { line, col, endLine, endCol, severity, key, params }
   - ScriptCheckPanel  工具条 + 编辑器 + 问题面板（Vue 组件）
     编辑器使用 Monaco 内核的 <script-code-editor>（见 monaco-code-editor.js），
     检查结果以行内波浪线(marker) + 问题列表呈现。
   ============================================================ */
(function () {
    "use strict";

    /* ---------------- 诊断工具 ---------------- */
    function addDiag(list, line, col, endLine, endCol, severity, key, params) {
        list.push({
            line: line,
            col: col,
            endLine: endLine == null ? line : endLine,
            endCol: endCol == null ? col + 1 : endCol,
            severity: severity,
            key: key,
            params: params || null
        });
    }

    function collectWord(masked, word) {
        const re = new RegExp("\\b" + word + "\\b", "g");
        const hits = [];
        masked.split("\n").forEach(function (line, idx) {
            re.lastIndex = 0;
            let m;
            while ((m = re.exec(line))) {
                hits.push({ line: idx + 1, col: m.index + 1 });
            }
        });
        return hits;
    }

    function pairKeywords(masked, out, pairs) {
        pairs.forEach(function (p) {
            const opens = collectWord(masked, p.open);
            const closes = collectWord(masked, p.close);
            if (opens.length > closes.length) {
                const at = opens[closes.length];
                addDiag(out, at.line, at.col, at.line, at.col + p.open.length, "warning", p.missingKey, { n: opens.length - closes.length });
            } else if (closes.length > opens.length) {
                const at = closes[opens.length];
                addDiag(out, at.line, at.col, at.line, at.col + p.close.length, "warning", p.extraKey, { n: closes.length - opens.length });
            }
        });
    }

    /* ---------------- Bash ---------------- */
    function checkBash(code) {
        const out = [];
        const lines = code.split(/\r?\n/);
        const mask = lines.map(function (l) { return l.split(""); });
        function blank(li, from, to) {
            const row = mask[li];
            for (let k = from; k < to && k < row.length; k++) row[k] = " ";
        }

        let inSingle = false, inDouble = false, inBacktick = false;
        let singleAt = null, doubleAt = null, backtickAt = null;
        const parens = [], braces = [];
        let heredoc = null;

        for (let li = 0; li < lines.length; li++) {
            const line = lines[li];
            if (heredoc) {
                const cmp = heredoc.stripTabs ? line.replace(/^\t+/, "") : line;
                blank(li, 0, line.length);
                if (cmp === heredoc.word) heredoc = null;
                continue;
            }
            let i = 0;
            while (i < line.length) {
                const c = line[i];
                if (inSingle) {
                    blank(li, i, i + 1);
                    if (c === "'") { inSingle = false; singleAt = null; }
                    i++;
                    continue;
                }
                if (inDouble) {
                    if (c === "\\") { blank(li, i, i + 2); i += 2; continue; }
                    blank(li, i, i + 1);
                    if (c === '"') { inDouble = false; doubleAt = null; }
                    i++;
                    continue;
                }
                if (inBacktick) {
                    if (c === "\\") { blank(li, i, i + 2); i += 2; continue; }
                    blank(li, i, i + 1);
                    if (c === "`") { inBacktick = false; backtickAt = null; }
                    i++;
                    continue;
                }
                if (c === "\\") { blank(li, i, i + 2); i += 2; continue; }
                if (c === "#" && (i === 0 || /[\s;&|(]/.test(line[i - 1]))) {
                    blank(li, i, line.length);
                    break;
                }
                if (c === "'") { inSingle = true; singleAt = { line: li + 1, col: i + 1 }; blank(li, i, i + 1); i++; continue; }
                if (c === '"') { inDouble = true; doubleAt = { line: li + 1, col: i + 1 }; blank(li, i, i + 1); i++; continue; }
                if (c === "`") { inBacktick = true; backtickAt = { line: li + 1, col: i + 1 }; blank(li, i, i + 1); i++; continue; }
                if (c === "<" && line[i + 1] === "<" && line[i + 2] !== "<") {
                    let j = i + 2;
                    let stripTabs = false;
                    if (line[j] === "-") { stripTabs = true; j++; }
                    while (line[j] === " " || line[j] === "\t") j++;
                    let quote = null;
                    if (line[j] === "'" || line[j] === '"') { quote = line[j]; j++; }
                    const start = j;
                    while (j < line.length && (quote ? line[j] !== quote : /[A-Za-z0-9_]/.test(line[j]))) j++;
                    const word = line.slice(start, j);
                    if (quote) j++;
                    if (word) {
                        heredoc = { word: word, stripTabs: stripTabs, line: li + 1, col: i + 1 };
                        blank(li, i, j);
                        i = j;
                        continue;
                    }
                }
                if (c === "(") { parens.push({ line: li + 1, col: i + 1 }); i++; continue; }
                if (c === ")") {
                    if (parens.length) parens.pop();
                    else addDiag(out, li + 1, i + 1, li + 1, i + 2, "error", "unmatchedCloseParen");
                    i++;
                    continue;
                }
                if (c === "{") { braces.push({ line: li + 1, col: i + 1 }); i++; continue; }
                if (c === "}") {
                    if (braces.length) braces.pop();
                    else addDiag(out, li + 1, i + 1, li + 1, i + 2, "error", "unmatchedCloseBrace");
                    i++;
                    continue;
                }
                i++;
            }
        }

        if (inSingle) addDiag(out, singleAt.line, singleAt.col, singleAt.line, singleAt.col + 1, "error", "unterminatedSingle");
        if (inDouble) addDiag(out, doubleAt.line, doubleAt.col, doubleAt.line, doubleAt.col + 1, "error", "unterminatedDouble");
        if (inBacktick) addDiag(out, backtickAt.line, backtickAt.col, backtickAt.line, backtickAt.col + 1, "error", "unterminatedBacktick");
        if (heredoc) addDiag(out, heredoc.line, heredoc.col, heredoc.line, heredoc.col + 1, "error", "unterminatedHeredoc");
        parens.forEach(function (p) { addDiag(out, p.line, p.col, p.line, p.col + 1, "error", "unclosedParen"); });
        braces.forEach(function (b) { addDiag(out, b.line, b.col, b.line, b.col + 1, "error", "unclosedBrace"); });

        if (code.indexOf("\r") >= 0) {
            const at = code.split("\n").findIndex(function (l) { return l.indexOf("\r") >= 0; });
            addDiag(out, at + 1, 1, at + 1, 1, "warning", "crlf");
        }

        const masked = mask.map(function (r) { return r.join(""); }).join("\n");
        pairKeywords(masked, out, [
            { open: "if", close: "fi", missingKey: "missingFi", extraKey: "extraFi" },
            { open: "do", close: "done", missingKey: "missingDone", extraKey: "extraDone" },
            { open: "case", close: "esac", missingKey: "missingEsac", extraKey: "extraEsac" }
        ]);

        return out;
    }

    /* ---------------- PowerShell ---------------- */
    function checkPowerShell(code) {
        const out = [];
        const lines = code.split(/\r?\n/);
        let inSingle = false, inDouble = false, inBlockComment = false;
        let inHereSingle = false, inHereDouble = false;
        let singleAt = null, doubleAt = null;
        const parens = [], braces = [], brackets = [];

        for (let li = 0; li < lines.length; li++) {
            const line = lines[li];
            if (inHereSingle || inHereDouble) {
                const term = inHereSingle ? "'@" : '"@';
                if (line.replace(/^\s+/, "").indexOf(term) === 0) { inHereSingle = false; inHereDouble = false; }
                continue;
            }
            if (inBlockComment) {
                if (line.indexOf("#>") >= 0) inBlockComment = false;
                continue;
            }
            let i = 0;
            while (i < line.length) {
                const c = line[i];
                if (inSingle) {
                    if (c === "'") inSingle = false;
                    i++;
                    continue;
                }
                if (inDouble) {
                    if (c === "`") { i += 2; continue; }
                    if (c === '"') inDouble = false;
                    i++;
                    continue;
                }
                if (c === "`") { i += 2; continue; }
                if (c === "#") break;
                if (c === "<" && line[i + 1] === "#") { inBlockComment = true; i += 2; continue; }
                if (c === "@" && line[i + 1] === "'") { inHereSingle = true; i += 2; continue; }
                if (c === "@" && line[i + 1] === '"') { inHereDouble = true; i += 2; continue; }
                if (c === "'") { inSingle = true; singleAt = { line: li + 1, col: i + 1 }; i++; continue; }
                if (c === '"') { inDouble = true; doubleAt = { line: li + 1, col: i + 1 }; i++; continue; }
                if (c === "(") { parens.push({ line: li + 1, col: i + 1 }); i++; continue; }
                if (c === ")") {
                    if (parens.length) parens.pop();
                    else addDiag(out, li + 1, i + 1, li + 1, i + 2, "error", "unmatchedCloseParen");
                    i++;
                    continue;
                }
                if (c === "{") { braces.push({ line: li + 1, col: i + 1 }); i++; continue; }
                if (c === "}") {
                    if (braces.length) braces.pop();
                    else addDiag(out, li + 1, i + 1, li + 1, i + 2, "error", "unmatchedCloseBrace");
                    i++;
                    continue;
                }
                if (c === "[") { brackets.push({ line: li + 1, col: i + 1 }); i++; continue; }
                if (c === "]") {
                    if (brackets.length) brackets.pop();
                    else addDiag(out, li + 1, i + 1, li + 1, i + 2, "error", "unmatchedCloseBracket");
                    i++;
                    continue;
                }
                i++;
            }
        }

        if (inSingle) addDiag(out, singleAt.line, singleAt.col, singleAt.line, singleAt.col + 1, "error", "unterminatedSingle");
        if (inDouble) addDiag(out, doubleAt.line, doubleAt.col, doubleAt.line, doubleAt.col + 1, "error", "unterminatedDouble");
        if (inHereSingle || inHereDouble) {
            const at = inHereSingle ? "'@" : '"@';
            addDiag(out, lines.length, 1, lines.length, 1, "error", "unterminatedHereString", { term: at });
        }
        if (inBlockComment) addDiag(out, lines.length, 1, lines.length, 1, "error", "unterminatedBlockComment");
        parens.forEach(function (p) { addDiag(out, p.line, p.col, p.line, p.col + 1, "error", "unclosedParen"); });
        braces.forEach(function (b) { addDiag(out, b.line, b.col, b.line, b.col + 1, "error", "unclosedBrace"); });
        brackets.forEach(function (b) { addDiag(out, b.line, b.col, b.line, b.col + 1, "error", "unclosedBracket"); });

        if (code.indexOf("\r") >= 0) {
            const at = code.split("\n").findIndex(function (l) { return l.indexOf("\r") >= 0; });
            addDiag(out, at + 1, 1, at + 1, 1, "warning", "crlf");
        }
        return out;
    }

    function check(code, language) {
        const src = code == null ? "" : String(code);
        const list = String(language).toUpperCase() === "POWERSHELL" ? checkPowerShell(src) : checkBash(src);
        list.sort(function (a, b) {
            return a.line - b.line || a.col - b.col || (a.severity === "error" ? -1 : 1);
        });
        return list;
    }

    window.ScriptChecker = { check: check };

    /* ---------------- 检查面板组件 ---------------- */
    const ScriptCheckPanel = {
        name: "ScriptCheckPanel",
        props: {
            modelValue: { type: String, default: "" },
            language: { type: String, default: "BASH" },
            height: { type: String, default: "320px" },
            page: { type: Boolean, default: false }
        },
        emits: ["update:modelValue", "update:language", "update:diagnostics"],
        inject: ["nd"],
        template: `
<div class="scp" :class="{ 'scp--page': page }">
  <div class="scp__bar">
    <el-radio-group :model-value="language" size="small" @update:model-value="setLanguage">
      <el-radio-button label="BASH">Bash</el-radio-button>
      <el-radio-button label="POWERSHELL">PowerShell</el-radio-button>
    </el-radio-group>
    <el-button size="small" type="primary" plain @click="run"><el-icon><Search/></el-icon>&nbsp;{{ t('scriptCheck.run') }}</el-button>
    <span class="scp__spacer"></span>
    <span v-if="checked && page" class="scp__status" :class="statusClass">{{ statusText }}</span>
  </div>
  <script-code-editor :model-value="modelValue" :language="language" :diagnostics="diagnostics"
                      :height="height" :stretch="page" @update:model-value="v => $emit('update:modelValue', v)" ref="editor" />
  <div class="scp__result" v-if="checked && !page">
    <div v-if="!diagnostics.length" class="scp__ok">{{ t('scriptCheck.noIssues') }}</div>
    <template v-else>
      <div class="scp__problems-head">
        <span class="scp__problems-title">{{ t('scriptCheck.problems') }}</span>
        <span v-if="errorCount" class="scp__problems-count is-error">{{ t('scriptCheck.errors', { n: errorCount }) }}</span>
        <span v-if="warningCount" class="scp__problems-count is-warn">{{ t('scriptCheck.warnings', { n: warningCount }) }}</span>
      </div>
      <div class="scp__list">
        <div v-for="(d, i) in diagnostics" :key="i" class="scp__item" :class="d.severity" @click="goto(d)">
          <span class="scp__sev" :class="'is-' + d.severity">{{ d.severity === 'error' ? t('scriptCheck.error') : t('scriptCheck.warning') }}</span>
          <span class="scp__msg">{{ d.message }}</span>
          <span class="scp__loc">{{ d.line }}:{{ d.col }}</span>
        </div>
      </div>
    </template>
  </div>
</div>
`,
        data: function () {
            return { diagnostics: [], checked: false, timer: null };
        },
        computed: {
            errorCount: function () {
                return this.diagnostics.filter(function (d) { return d.severity === "error"; }).length;
            },
            warningCount: function () {
                return this.diagnostics.length - this.errorCount;
            },
            statusClass: function () {
                return this.errorCount ? "is-error" : (this.warningCount ? "is-warn" : "is-ok");
            },
            statusText: function () {
                if (!this.diagnostics.length) return this.t("scriptCheck.statusOk");
                const parts = [];
                if (this.errorCount) parts.push(this.t("scriptCheck.errors", { n: this.errorCount }));
                if (this.warningCount) parts.push(this.t("scriptCheck.warnings", { n: this.warningCount }));
                return parts.join(" · ");
            }
        },
        watch: {
            modelValue: function () {
                this.schedule();
            },
            language: function () {
                this.schedule();
            }
        },
        created: function () {
            this.run();
        },
        methods: {
            t: function (key, params) {
                return this.nd.t(key, params);
            },
            setLanguage: function (v) {
                this.$emit("update:language", v);
            },
            schedule: function () {
                const self = this;
                if (this.timer) clearTimeout(this.timer);
                this.timer = setTimeout(function () { self.run(); }, 350);
            },
            run: function () {
                this.diagnostics = window.ScriptChecker.check(this.modelValue, this.language).map((d) => Object.assign({}, d, {
                    message: this.t("scriptCheck.msg." + d.key, d.params)
                }));
                this.checked = true;
                this.$emit("update:diagnostics", this.diagnostics);
            },
            goto: function (d) {
                if (this.$refs.editor) this.$refs.editor.focusPosition(d.line, d.col);
            }
        }
    };

    window.ScriptCheckPanel = ScriptCheckPanel;
})();