// 喵~导入 React 上下文与生命周期工具，用于管理嵌入模式状态喵~
import React, {createContext, useContext, useEffect, useMemo, useState} from "react";

// 喵~定义父页面允许发送的初始化消息名称喵~
const EMBED_INIT_MESSAGE_TYPE = "PLAN_EMBED_INIT";
// 喵~定义 Plan 向父页面报告就绪的消息名称喵~
const EMBED_READY_MESSAGE_TYPE = "PLAN_EMBED_READY";
// 喵~定义嵌入通信协议版本，避免不同版本消息混用喵~
const EMBED_PROTOCOL_VERSION = 1;

// 喵~读取 URL 参数并在模块加载时确定候选嵌入配置，确保语言初始化前即可生效喵~
const readEmbedCandidate = () => {
    // 喵~防御：顶层页面不允许进入嵌入模式，避免普通 Plan 页面被意外限制喵~
    if (window.self === window.top) return null;
    // 喵~读取当前页面 URL，便于校验嵌入参数与玩家路由喵~
    const currentUrl = new URL(window.location.href);
    // 喵~读取明确的嵌入开关，避免只靠 iframe 环境误触发喵~
    const isEmbedRequested = currentUrl.searchParams.get("embed") === "1";
    // 喵~读取预期父页面 origin，后续 postMessage 必须精确匹配喵~
    const parentOriginParameter = currentUrl.searchParams.get("embedParentOrigin") || "";
    // 喵~读取由父页面生成的一次性随机 nonce，阻止其他页面猜测初始化消息喵~
    const embedNonce = currentUrl.searchParams.get("embedNonce") || "";
    // 喵~读取父页面锁定的玩家标识，限制嵌入体验只能显示该玩家喵~
    const embedPlayerIdentifier = currentUrl.searchParams.get("embedPlayer") || "";
    // 喵~从 pathname 提取当前玩家路由标识，拒绝网络页和其他页面作为嵌入入口喵~
    const playerRouteMatch = currentUrl.pathname.match(/\/player\/([^/]+)/);
    // 喵~防御：缺少任何必需嵌入参数时不启用嵌入模式喵~
    if (!isEmbedRequested || !parentOriginParameter || !embedNonce || !embedPlayerIdentifier || !playerRouteMatch) return null;
    try {
        // 喵~解析并规范化父页面 origin，拒绝无效 URL 与非 HTTP(S) 协议喵~
        const parentUrl = new URL(parentOriginParameter);
        // 喵~防御：只允许 HTTP(S) 网站作为嵌入父页面喵~
        if (parentUrl.protocol !== "http:" && parentUrl.protocol !== "https:") return null;
        // 喵~防御：nonce 长度过短或包含空白字符时拒绝嵌入喵~
        if (embedNonce.length < 16 || embedNonce.length > 256 || /\s/.test(embedNonce)) return null;
        // 喵~防御：玩家标识必须与当前玩家路由一致喵~
        if (decodeURIComponent(playerRouteMatch[1]) !== embedPlayerIdentifier) return null;
        // 喵~返回经过规范化的候选嵌入配置喵~
        return {parentOrigin: parentUrl.origin, nonce: embedNonce, playerIdentifier: embedPlayerIdentifier};
    } catch (error) {
        // 喵~防御：参数解析失败时保持普通 Plan 页面，避免页面崩溃喵~
        return null;
    }
};

// 喵~保存一次解析结果，避免组件渲染期间反复读取可变 URL 参数喵~
const embedCandidate = readEmbedCandidate();

// 喵~导出候选状态，供语言服务在 React 初始化前锁定中文喵~
export const isPlanEmbedCandidate = () => Boolean(embedCandidate);
// 喵~导出候选配置，供应用路由读取锁定玩家喵~
export const getPlanEmbedCandidate = () => embedCandidate;

// 喵~严格匹配当前玩家允许的嵌入路径喵~
export const isPlanEmbedPathAllowed = (pathname, lockedPlayerIdentifier) => {
    // 喵~防御：未取得锁定玩家时拒绝所有嵌入路由喵~
    if (!lockedPlayerIdentifier) return false;
    // 喵~拆分 URL 路径段，避免首尾斜杠影响匹配喵~
    const pathSegments = pathname.split("/").filter(Boolean);
    // 喵~防御：嵌入体验只允许 player 路由喵~
    if (pathSegments[0] !== "player") return false;
    // 喵~防御：当前页面玩家必须与 iframe 启动时锁定的玩家一致喵~
    if (decodeURIComponent(pathSegments[1] || "") !== lockedPlayerIdentifier) return false;
    // 喵~允许未指定子页的玩家路由喵~
    if (pathSegments.length === 2) return true;
    // 喵~允许固定玩家信息标签页喵~
    if (["overview", "sessions", "pvppve", "servers"].includes(pathSegments[2])) return pathSegments.length === 3;
    // 喵~允许单层插件服务器页喵~
    return pathSegments[2] === "plugins" && pathSegments.length === 4 && pathSegments[3] !== "";
};

// 喵~判断点击目标是否属于允许的当前玩家链接喵~
const isSafeEmbedAnchor = (anchor, lockedPlayerIdentifier) => {
    // 喵~防御：缺少链接或锁定玩家时拒绝点击喵~
    if (!anchor || !lockedPlayerIdentifier) return false;
    try {
        // 喵~解析链接为绝对地址，处理相对链接与根路径链接喵~
        const targetUrl = new URL(anchor.href, window.location.href);
        // 喵~防御：禁止所有外部链接在嵌入体验内导航喵~
        if (targetUrl.origin !== window.location.origin) return false;
        // 喵~只允许当前玩家白名单路径喵~
        return isPlanEmbedPathAllowed(targetUrl.pathname, lockedPlayerIdentifier);
    } catch (error) {
        // 喵~防御：非法 href 一律阻止喵~
        return false;
    }
};

// 喵~创建默认嵌入上下文，普通访问保持完全不受影响喵~
const EmbedContext = createContext({isEmbedMode: false, isEmbedActive: false, lockedPlayerIdentifier: ""});

// 喵~提供嵌入状态、握手和统一点击拦截能力喵~
export const EmbedContextProvider = ({children}) => {
    // 喵~候选 iframe 立即采用受限 UI，避免完整导航短暂显示喵~
    const isEmbedMode = Boolean(embedCandidate);
    // 喵~记录父页面是否已完成来源与 nonce 校验喵~
    const [isEmbedActive, setIsEmbedActive] = useState(false);

    useEffect(() => {
        // 喵~防御：普通页面不注册跨窗口与点击监听喵~
        if (!embedCandidate) return undefined;
        // 喵~通知父页面 iframe 已准备接收初始化消息喵~
        window.parent.postMessage({protocol: EMBED_PROTOCOL_VERSION, type: EMBED_READY_MESSAGE_TYPE, nonce: embedCandidate.nonce, playerIdentifier: embedCandidate.playerIdentifier}, embedCandidate.parentOrigin);
        // 喵~处理父页面初始化消息并严格校验来源、窗口和 nonce 喵~
        const handleEmbedMessage = (event) => {
            // 喵~防御：只接受当前 iframe 父窗口消息喵~
            if (event.source !== window.parent || event.origin !== embedCandidate.parentOrigin) return;
            // 喵~防御：消息必须是对象且字段完全匹配喵~
            if (!event.data || typeof event.data !== "object" || event.data.protocol !== EMBED_PROTOCOL_VERSION || event.data.type !== EMBED_INIT_MESSAGE_TYPE || event.data.nonce !== embedCandidate.nonce || event.data.playerIdentifier !== embedCandidate.playerIdentifier || event.data.locale !== "CN") return;
            // 喵~标记握手完成喵~
            setIsEmbedActive(true);
        };
        // 喵~在文档捕获阶段拦截所有链接，覆盖第三方扩展与遗留按钮喵~
        const blockUnsafeEmbedNavigation = (event) => {
            // 喵~防御：只在嵌入候选模式处理点击喵~
            if (!isEmbedMode) return;
            // 喵~从点击目标向上查找最近的超链接喵~
            const clickedElement = event.target instanceof Element ? event.target.closest("a") : null;
            // 喵~允许当前玩家内部白名单链接，其余链接全部阻止喵~
            if (clickedElement && !isSafeEmbedAnchor(clickedElement, embedCandidate.playerIdentifier)) {
                event.preventDefault();
                event.stopImmediatePropagation();
            }
        };
        // 喵~注册窗口通信监听器喵~
        window.addEventListener("message", handleEmbedMessage);
        // 喵~注册捕获阶段点击守卫喵~
        document.addEventListener("click", blockUnsafeEmbedNavigation, true);
        // 喵~组件卸载时移除监听器喵~
        return () => {
            window.removeEventListener("message", handleEmbedMessage);
            document.removeEventListener("click", blockUnsafeEmbedNavigation, true);
        };
    }, [isEmbedMode]);

    // 喵~缓存上下文值，减少无关重渲染喵~
    const contextValue = useMemo(() => ({isEmbedMode, isEmbedActive, lockedPlayerIdentifier: embedCandidate?.playerIdentifier || ""}), [isEmbedMode, isEmbedActive]);
    // 喵~向子组件提供嵌入状态喵~
    return <EmbedContext.Provider value={contextValue}>{children}</EmbedContext.Provider>;
};

// 喵~导出读取嵌入状态的 Hook 喵~
export const useEmbed = () => useContext(EmbedContext);
