/* ============================================================
   ScriptCodeEditor · 基于 Monaco Editor（VS Code 内经）
   惰性加载本地 vendor/monaco，提供 VS Code 风格的代码编辑：
- 语法高亮（shell / powershell，Monaco 内置 tokenizer）
    - 行内诊断（markers，来自 ScriptChecker 的检查结果）
    - 变量/命令补全（shell: $VAR/环境变量/常用命令；powershell: $变量/$env:/Cmdlet）
    - minimap / 代码折叠 / 查找替换(Ctrl+F) / 括号配对着色 / 多光标
    - 缩进、Tab、Ctrl+/ 注释切换等 VS Code 键位
    - 主题跟随应用的深浅色（html.dark 类）
    接口与旧版 ScriptCodeEditor 保持一致（props / emits / focusPosition）。
    ============================================================ */
(function () {
    "use strict";

    var VS_BASE = "/vendor/monaco/min/vs";

    /* ---------------- 补全数据 ---------------- */

    var BASH_ENV = ["HOME","PATH","PWD","OLDPWD","SHELL","USER","LOGNAME","HOSTNAME",
        "HOSTTYPE","MACHTYPE","OSTYPE","TERM","LANG","LC_ALL","LC_TIME","TZ","EDITOR",
        "VISUAL","PAGER","IFS","SHLVL","PPID","UID","EUID","GID","RANDOM","SECONDS",
        "LINENO","BASH_VERSION","BASHOPTS","SHELLOPTS","PIPESTATUS","FUNCNAME","BASH_SOURCE",
        "BASH_LINENO","CDPATH","DISPLAY","SSH_TTY","SSH_CONNECTION","SSH_CLIENT","CI","HOME","PWD"];

    var BASH_COMMANDS = ["shutdown","reboot","halt","poweroff","sudo","su","systemctl",
        "service","apt","apt-get","yum","dnf","pacman","docker","docker-compose","kubectl",
        "ip","ifconfig","ping","traceroute","curl","wget","tar","gzip","gunzip","zip","unzip",
        "grep","sed","awk","find","ls","cat","touch","mkdir","rm","rmdir","cp","mv","chmod",
        "chown","whoami","id","date","sleep","timeout","nohup","bash","sh","source","exec",
        "echo","printf","read","exit","cd","pwd","test","export","alias","unset","set",
        "xargs","tail","head","less","more","tee","wc","sort","uniq","cut","tr","env","df","du","ps","kill","pkill","top","htop","journalctl"];

    var PS_VARS = ["$?","$^","$_","$args","$input","$MyInvocation","$PSScriptRoot",
        "$PSCommandPath","$PID","$PSVersionTable","$PSBoundParameters","$PsCulture",
        "$PsUICulture","$PSHOME","$PSModulePath","$Host","$Error","$LastExitCode",
        "$StackTrace","$PWD","$HOME","$PSProvider","$foreach","$switch","$using",
        "$global:","$script:","$local:","$env:"];

    var PS_ENV = ["PATH","HOME","USER","USERNAME","SYSTEMROOT","SystemRoot","TEMP","TMP",
        "WINDIR","WINDOWS_HOME","COMPUTERNAME","PROCESSOR_ARCHITECTURE","NUMBER_OF_PROCESSORS",
        "OS","ProgramData","ProgramFiles","APPDATA","LOCALAPPDATA","PUBLIC","OneDrive",
        "SESSIONNAME","USERPROFILE","COMMONPROGRAMFILES"];

    var PS_CMDLETS = ["Get-Service","Stop-Service","Start-Service","Restart-Service",
        "Set-Service","Get-Process","Start-Process","Stop-Process","Wait-Process",
        "Get-ChildItem","Set-Content","Get-Content","Add-Content","Clear-Content",
        "Set-Item","Get-Item","New-Item","Remove-Item","Rename-Item","Copy-Item","Move-Item",
        "Test-Path","Join-Path","Split-Path","Resolve-Path","Get-ItemProperty",
        "Set-ItemProperty","Get-Command","Get-Help","Get-Host","Get-Location","Set-Location",
        "Select-Object","Where-Object","ForEach-Object","Sort-Object","Group-Object",
        "Measure-Object","Select-String","Compare-Object","ConvertTo-Json","ConvertFrom-Json",
        "ConvertTo-Csv","Export-Csv","Import-Csv","Invoke-RestMethod","Invoke-WebRequest",
        "Invoke-Command","Invoke-Expression","Write-Host","Write-Output","Write-Error",
        "Write-Warning","Read-Host","Clear-Host","Format-Table","Format-List","Format-Wide",
        "Out-Null","Out-File","Out-String","Get-Date","Start-Sleep","Start-Sleep",
        "Restart-Computer","Stop-Computer","Exit-PSSession","Enter-PSSession","Export-Clixml",
        "Import-Clixml","Get-EventLog","Clear-EventLog","Get-WmiObject","Get-CimInstance",
        "Get-NetIPAddress","Get-NetRoute","Test-Connection","Resolve-DnsName","New-Object",
        "Get-Random","Get-Unique","Get-Job","Receive-Job","Start-Job","Stop-Job","Get-ComputerInfo"];

    function scanBashVars(code) {
        var names = {}, re = /^\s*(?:(?:export|readonly|local)\s+)?(?:declare\s+(?:-[A-Za-z]+\s+)?)?([A-Za-z_][A-Za-z0-9_]*)\s*=/gm;
        var reExp = /\bexport\s+([A-Za-z_][A-Za-z0-9_]*)(?=\s|\b)/g;
        var m;
        while ((m = re.exec(code || ""))) names[m[1]] = true;
        while ((m = reExp.exec(code || ""))) if (m[1] && !/\s/.test(m[1])) names[m[1]] = true;
        return Object.keys(names);
    }

    function scanPsVars(code) {
        var names = {}, re = /^\s*\$([A-Za-z_][A-Za-z0-9_]*)\s*=/gm;
        var m;
        while ((m = re.exec(code || ""))) names[m[1]] = true;
        return Object.keys(names);
    }

    function registerCompletions() {
        var monaco = window.monaco;
        if (!monaco || !monaco.languages || window.__scriptCompletionsRegistered) return;
        window.__scriptCompletionsRegistered = true;
        var Kind = monaco.languages.CompletionItemKind || monaco.CompletionItemKind;

        monaco.languages.registerCompletionItemProvider("shell", {
            triggerCharacters: ["$", "{"],
            provideCompletionItems: function (model, position) {
                var code = model.getValue();
                var word = model.getWordUntilPosition(position);
                var range = {
                    startLineNumber: position.lineNumber,
                    endLineNumber: position.lineNumber,
                    startColumn: word.startColumn,
                    endColumn: word.endColumn
                };
                var items = [];
                var vars = scanBashVars(code);
                vars.forEach(function (name) {
                    items.push({
                        label: "$" + name.split("=")[0],
                        kind: Kind.Variable,
                        insertText: "$" + name,
                        range: range,
                        detail: "脚本内变量",
                        sortText: "10" + name
                    });
                });
                BASH_ENV.forEach(function (name) {
                    var label = "$" + name;
                    if (vars.indexOf(name) >= 0) return;
                    items.push({
                        label: label,
                        kind: Kind.Variable,
                        insertText: label,
                        range: range,
                        detail: "环境变量",
                        sortText: "20" + name
                    });
                });
                BASH_COMMANDS.forEach(function (name) {
                    items.push({
                        label: name,
                        kind: Kind.Function,
                        insertText: name + " ",
                        range: range,
                        detail: "常用命令",
                        sortText: "30" + name
                    });
                });
                return { suggestions: items };
            }
        });

        monaco.languages.registerCompletionItemProvider("powershell", {
            triggerCharacters: ["$", "{", ":"],
            provideCompletionItems: function (model, position) {
                var code = model.getValue();
                var word = model.getWordUntilPosition(position);
                var range = {
                    startLineNumber: position.lineNumber,
                    endLineNumber: position.lineNumber,
                    startColumn: word.startColumn,
                    endColumn: word.endColumn
                };
                var items = [];
                var vars = scanPsVars(code);
                vars.forEach(function (name) {
                    var label = "$" + name.split("=")[0];
                    items.push({
                        label: label,
                        kind: Kind.Variable,
                        insertText: label,
                        range: range,
                        detail: "脚本内变量",
                        sortText: "10" + name
                    });
                });
                PS_VARS.forEach(function (name) {
                    items.push({
                        label: name,
                        kind: Kind.Variable,
                        insertText: name + (name.indexOf(":") >= 0 ? "" : ""),
                        range: range,
                        detail: "自动变量",
                        sortText: "20" + name
                    });
                });
                PS_ENV.forEach(function (name) {
                    var label = "$env:" + name;
                    items.push({
                        label: label,
                        kind: Kind.Variable,
                        insertText: label,
                        range: range,
                        detail: "环境变量",
                        sortText: "21" + name
                    });
                });
                PS_CMDLETS.forEach(function (name) {
                    items.push({
                        label: name,
                        kind: Kind.Function,
                        insertText: name + " ",
                        range: range,
                        detail: "PowerShell Cmdlet",
                        sortText: "30" + name
                    });
                });
                return { suggestions: items };
            }
        });
    }

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
            height: { type: String, default: "320px" },
            stretch: { type: Boolean, default: false }
        },
        emits: ["update:modelValue"],
        template: `
<div class="mce" :class="{ 'mce--stretch': stretch }" :style="stretch ? undefined : { height: height }">
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
                .then(function () {
                    registerCompletions();
                    self.initEditor();
                })
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
                    quickSuggestions: { other: true, comments: false, strings: true },
                    suggest: { showWords: true, preview: true },
                    suggestOnTriggerCharacters: true,
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