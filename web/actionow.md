## 1. 品牌色彩系统 (Dual-Mode Theme)

核心色依然是 **高能黄 (Voltage Yellow)**，但在不同模式下，背景和辅助色进行了优化适配。

### 1.1 主题配置 (Tailwind CSS / HeroUI Plugin)

```javascript
// tailwind.config.js
const { heroui } = require("@heroui/react");

module.exports = {
  content: [
    "./node_modules/@heroui/theme/dist/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        brand: ["Plus Jakarta Sans", "Inter", "sans-serif"], // 更现代的圆润字体
        mono: ["JetBrains Mono", "monospace"],
      },
    },
  },
  plugins: [
    heroui({
      themes: {
        // --- 明亮模式 ---
        light: {
          colors: {
            background: "#FFFFFF",
            foreground: "#11181C",
            primary: {
              DEFAULT: "#FFD700", // 高能黄
              foreground: "#11181C", // 黄底黑字保证可读性
            },
            secondary: {
              DEFAULT: "#F4F4F5", // 浅灰
              foreground: "#11181C",
            },
            focus: "#FFD700",
          },
        },
        // --- 暗黑模式 ---
        dark: {
          colors: {
            background: "#0B0B0B", // 深邃黑
            foreground: "#ECEDEE",
            primary: {
              DEFAULT: "#FFD700", // 高能黄
              foreground: "#11181C",
            },
            secondary: {
              DEFAULT: "#1F1F22", // 深灰
              foreground: "#ECEDEE",
            },
            focus: "#FFD700",
          },
        },
      },
      // 维持 HeroUI 默认的大圆角设置
      layout: {
        radius: {
          small: "8px",
          medium: "12px",
          large: "18px", 
        },
      },
    }),
  ],
};
```

---

## 2. UI 界面视觉风格

### 2.1 质感定义
*   **圆角 (Rounding)**: 全面采用HeroUI的默认圆角。
*   **阴影 (Shadows)**:
    *   *明亮模式*: 采用柔和的长阴影（Soft Ambient Shadows），营造悬浮感。
    *   *暗黑模式*: 采用微弱的边框描边（1px Border）代替阴影，增加层次感。
*   **模糊 (Glassmorphism)**: 导航栏和侧边栏使用 `backdrop-blur-md`，在暗黑模式下呈现“烟熏玻璃”质感，在明亮模式下呈现“磨砂白”质感。

## 3. 明亮与暗黑风格适配策略

### 3.1 明亮模式 (Creative Studio View)
*   **视觉感受**: 干净、专业、像高级纸质工作簿。
*   **配色**: 白色背景 + 浅灰色边框 + 黄色核心按钮。
*   **文字**: 使用 `text-slate-900` 增强易读性。

### 3.2 暗黑模式 (The Screening Room)
*   **视觉感受**: 沉浸、高端、像私人影院。
*   **配色**: 影棚黑背景 + 深灰组件卡片 + 黄色发光点缀。
*   **文字**: 使用 `text-slate-200` 降低视觉疲劳。

---

## 4. 吉祥物 Kaka (咔咔) 的适配升级

在圆角科技风格下，Kaka 的形象变得更加 Q 弹和 3D 化：
*   **材质**: 类似 Airpods 的亮面烤漆质感（明亮模式）或 磨砂金属（暗黑模式）。
*   **造型**: 镜头大眼睛变得更加圆润，四肢采用圆柱体设计。
*   **配色**: 身体为主体白/黑，细节处（如领带、快门按键）使用**黄色**点亮。

---

## 5. 模块化命名体系 (维持专业度)

即使风格变温和，**行业术语**依然保留，以维持“专业影视工业化”的定位：

| 模块 | UI 呈现 | 交互逻辑 |
| :--- | :--- | :--- |
| **片场 (The Set)** | 主工作区面板 | 容纳所有协作组件的容器 |
| **通告单 (Call Sheet)** | 侧边栏任务/项目列表 | 快速切换不同的创作任务 |
| **导演椅 (Profile)** | 顶部个人中心 | 点击查看成就、算力余额 |
| **剧本指令 (Prompts)** | 输入框 (Textarea) | 自动联想、语法高亮（黄色） |
| **杀青 (Export)** | 导出完成弹窗 | 伴随黄色碎纸屑动画 |

---

## 6. 前端开发建议 (Action Plan)

1.  **使用 HeroUI 默认 Radius**: 不要去手动修改 Tailwind 的 `rounded` 类，直接使用 HeroUI 组件的属性，确保全局一致。
2.  **色彩变量**: 所有的黄色统一引用 `text-primary` 或 `bg-primary`。
3.  **模式切换**:
    *   使用 `next-themes` 插件。
    *   在 `Navbar` 中添加一个圆润的 `Switch` 组件，切换时加入流畅的 **Cross-fade** 过渡效果。
4.  **去工业化**: 移除所有斜杠、网格线和粗糙的金属贴图。取而代之的是细微的 **渐变 (Gradients)** 和 **模糊 (Blurs)**。
5. 任务	技术栈/组件	详细说明
基础骨架	Next.js + HeroUI V3	利用 App Router 实现流式渲染
实时预览	WebSocket / Server-Sent Events	实时反馈 AI 生成进度
状态管理	Zustand	管理“片场”内的镜头参数与全局设置
图标库	Lucide React
动画库	Framer Motion	负责 ACTION! 按钮的震动反馈和页面切换


## 7. 总结：品牌新格调

**ActioNow** 现在的面貌是：
*   **它像 Apple 产品的包装**: 极简、大圆角、舒适、昂贵感。
*   **它保持了电影人的初心**: 核心色黄色代表了灯光、能量和“Action”。
*   **它既好用又好看**: 明亮模式适合白天构思剧本，暗黑模式适合深夜渲染成片。

这样的设计既能吸引专业的影视从业者（专业术语），也能吸引 AIGC 爱好者（HeroUI 带来的极佳易用性与审美）。