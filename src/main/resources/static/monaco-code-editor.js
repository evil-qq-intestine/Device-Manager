/* ============================================================
   ScriptCodeEditor · 基于 Monaco Editor（VS Code 内经）
   惰性加载本地 vendor/monaco，提供 VS Code 风格的代码编辑：
   - 语法高亮（shell / powershell，Monaco 内置 tokenizer）
   - 行内诊断（markers，来自 ScriptChecker 的检查结果）
   - minimap / 代码折叠 / 查找替换(Ctrl+F) / 括号配对着色 / 多光标
   - 缩进、Tab、Ctrl+/ 注释切换等 VS Code 键位
   - 主题跟随应用的深浅色（html.dark 类）
   接口与旧版 ScriptCodeEditor 保持一致（props / emits / focusPosition）。
   ============================================================ */
(function () {
    "use strict";

    var VS_BASE = "/vendor/monaco/min/vs";

    function isDark() {
        return document.documentElement.classList.contains("dark");
    }

    function langId(language) {
        return String(language).toUpperCase() === "POWERSHELL" ? "powershell" : "shell";
    }

    /* 惰性加载 Monaco（AMD 单文件版），只在使用编辑器时才下载。 */
    function loadMonaco() {
        return new Promise(function (resolve, reject) {
            if (window.monaco && window.monaco.editor) {
                resolve(window.monaco);
                return;
            }
            var tags = document.querySelectorAll("script[data-monaco-loader]");
            if (tags.length) {
                tags[0].addEventListener("load", function () { resolve(window.monaco); }, { once: true });
                return;
            }
            var loader = document.createElement("script");
            loader.src = VS_BASE + "/loader.js";
            loader.setAttribute("data-monaco-loader", "1");
            loader.onload = function () {
                window.require.config({ paths: { vs: VS_BASE } });
                window.require(
                    ["vs/editor/editor.main",
                     "vs/basic-languages/shell/shell",
                     "vs/basic-languages/powershell/powershell"],
                    function () { resolve(window.monaco); },
                    function (err) { reject(err); }
                );
            };
            loader.onerror = function () { reject(new Error("Monaco loader failed to load")); };
            document.head.appendChild(loader);
        });
    }

    window.ScriptCodeEditor = {
        name: "ScriptCodeEditor",
        props: {
            modelValue: { type: String, default: "" },
            language: { type: String, default: "BASH" },
            diagnostics: { type: Array, default: function () { return []; } },
            height: { type: String, default: "320px" }
        },
        emits: ["update:modelValue"],
        template: `
<div class="mce" :style="{ height: height }">
  <div class="mce__container" ref="host"></div>
</div>`,
        data: function () {
            return { ready: false };
        },
        mounted: function () {
            var self = this;
            this._observer = new MutationObserver(function () { self.syncTheme(); });
            this._observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
            loadMonaco()
                .then(function () { self.initEditor(); })
                .catch(function () { /* 保持原样，避免崩溃 */ });
        },
        beforeUnmount: function () {
            if (this._observer) this._observer.disconnect();
            if (this._contentDisposable) this._contentDisposable.dispose();
            if (this.editor) this.editor.dispose();
        },
        methods: {
            initEditor: function () {
                var self = this;
                this.editor = window.monaco.editor.create(this.$refs.host, {
                    value: this.modelValue || "",
                    language: langId(this.language),
                    theme: isDark() ? "vs-dark" : "vs",
                    automaticLayout: true,
                    fontSize: 12.5,
                    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace",
                    lineHeight: 20,
                    minimap: { enabled: true },
                    scrollBeyondLastLine: false,
                    wordWrap: "off",
                    tabSize: 4,
                    insertSpaces: true,
                    bracketPairColorization: { enabled: true },
                    guides: { bracketPairs: "active", indentation: true },
                    renderLineHighlight: "all",
                    renderWhitespace: "selection",
                    smoothScrolling: true,
                    cursorBlinking: "smooth",
                    cursorSmoothCaretAnimation: "on",
                    padding: { top: 6, bottom: 6 },
                    scrollbar: { verticalScrollbarSize: 10, horizontalScrollbarSize: 10 },
                    contextmenu: true,
                    folding: true,
                    find: { addExtraSpaceOnTop: false }
                });
                this._contentDisposable = this.editor.onDidChangeModelContent(function () {
                    var v = self.editor.getValue();
                    if (v !== self.modelValue) self.$emit("update:modelValue", v);
                });
                this.ready = true;
                this.applyMarkers();
                this.syncTheme();
            },
            applyMarkers: function () {
                if (!this.editor || !this.ready) return;
                var monaco = window.monaco;
                var model = this.editor.getModel();
                var markers = (this.diagnostics || []).map(function (d) {
                    var sev = d.severity === "error"
                        ? monaco.MarkerSeverity.Error
                        : monaco.MarkerSeverity.Warning;
                    return {
                        startLineNumber: d.line || 1,
                        startColumn: d.col || 1,
                        endLineNumber: d.endLine || d.line || 1,
                        endColumn: d.endCol || (d.col || 1) + 1,
                        severity: sev,
                        message: d.message || d.key || "issue",
                        source: "script-check"
                    };
                });
                monaco.editor.setModelMarkers(model, "script-check", markers);
            },
            syncTheme: function () {
                if (this.editor && window.monaco) {
                    window.monaco.editor.setTheme(isDark() ? "vs-dark" : "vs");
                }
            },
            focusPosition: function (line, col) {
                if (!this.editor || !this.ready) return;
                var p = { lineNumber: line || 1, column: col || 1 };
                this.editor.revealPositionInCenter(p);
                this.editor.setPosition(p);
                this.editor.focus();
            }
        },
        watch: {
            modelValue: function (v) {
                if (this.editor && v !== this.editor.getValue()) {
                    this.editor.getModel().setValue(v || "");
                }
            },
            language: function (v) {
                if (this.editor) {
                    window.monaco.editor.setModelLanguage(this.editor.getModel(), langId(v));
                }
            },
            diagnostics: function () {
                this.applyMarkers();
            }
        }
    };
})();