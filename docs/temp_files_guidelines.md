# 项目临时文件与草稿处理规范 (Temporary Files & Scratch Guidelines)

为了保持项目根目录的整洁，避免无关的临时转储、截图和草稿脚本被提交到 Git 仓库，特制定本规范。

---

## 1. 临时文件的定义与分类

本规范适用的临时文件包括但不限于：
* **UI 转储文件**：如 `holdings-ui.xml`、`trade-entry-ui.xml` 等在自动化测试或布局分析时生成的 XML 视图结构文件。
* **截图与媒体文件**：如 `screencap.png`、`holdings-screen.png` 等 UI 截图和验证录屏。
* **临时数据与日志**：如自动化解析时导出的 `extracted.txt`。
* **临时逻辑草稿**：如起草算法或映射表的 `mapping.txt`、临时测试脚本 `test_option_price.py`。
* **临时调试脚本**：如 `scratch.py`、`scratch.kts` 等在开发调试阶段运行的一次性脚本。

---

## 2. 存放规范

### 2.1 优先放置在 `.gitignore` 已忽略的目录
项目根目录下有几个目录已被 `.gitignore` 全局忽略，临时文件或调试脚本应存放在这些目录中：
* **`tmp/`**：适用于存放一次性的转储文件、临时日志和 UI XML 结构。
* **`scratch/`**：适用于存放开发者调试用的一次性 Kotlin/Python 脚本或小段草稿代码。

### 2.2 AI Agent 专属会话目录
在协同开发或使用 AI Agent（如 Antigravity）时，Agent 生成的临时文件与过程数据应统一存放在 Agent 会话级 scratch 文件夹中，例如：
* **路径**：`C:\Users\Administrator\.gemini\antigravity\brain\<conversation-id>\scratch\`
* **要求**：绝不允许将分析中的临时文件直接生成或放置在项目的根目录下。

---

## 3. Git 提交流程规范

在进行 `git commit` 前，请务必执行以下步骤检查工作区：

1. **查看状态**：
   ```bash
   git status -u
   ```
   检查是否有未跟踪（Untracked）的临时文件出现在根目录或项目源码中。

2. **清除或移动**：
   * 如果是无需保留的临时文件，应执行 `rm` 或 `Remove-Item` 物理删除。
   * 如果是有参考价值的调试脚本或数据，移动到 `scratch/` 或 `docs/` 等规范目录下。

3. **配置 `.gitignore` 阻断**：
   项目根目录下已追加防范规则。如果发现有新型临时文件被跟踪，请及时在 `.gitignore` 中补充忽略规则，避免误提交。
