// 喵~导入 React 上下文与生命周期工具，用于管理嵌入模式状态喵~
import React, {createContext, useContext, useEffect, useMemo, useState} from "react";

// 喵~定义父页面允许发送的初始化消息名称喵~
const EMBED_INIT_MESSAGE_TYPE = "PLAN_EMBED_INIT";
// 喵~定义 Plan 向父页面报告就绪的消息名称喵~
const EMBED_READY_MESSAGE_TYPE = "PLAN_EMBED_READY";
// 喵~定义嵌入通信协议版本，避免不同版本消息混用喵~
const EMBED_PROTOCOL_VERSION = 1;
// 喵~定义 iframe 刷新时保存嵌入状态的 sessionStorage 键名喵~
const EMBED_STORAGE_KEY = "plan.embed.state";
// 喵~限制嵌入状态最长有效时间，过期状态不会重新启用路由限制喵~
const EMBED_STORAGE_MAX_AGE_MS = 2 * 60 * 60 * 1000;

// 喵~判断当前窗口是否确实运行在 iframe 中，顶层页面永不启用嵌入状态喵~
const isEmbeddedWindow = () => {
    try {
        return window.self !== window.top;
    } catch (error) {
        // 喵~防御：跨窗口访问失败时按顶层页面处理，避免误启用嵌入限制喵~
        return false;
    }
};

// 喵~安全解码路径片段，非法百分号编码直接返回空值喵~
const decodePathSegment = (pathSegment) => {
    try {
        return decodeURIComponent(pathSegment || "");
    } catch (error) {
        // 喵~防御：非法路径编码不能参与玩家身份匹配喵~
        return "";
    }
};

// 喵~把 URL pathname 拆成非空路径片段，统一处理首尾斜杠喵~
const getPathSegments = (pathname) => String(pathname || "").split("/").filter(Boolean);

// 喵~从路径中提取玩家路由和前置 basename，兼容 Plan 部署在子路径下喵~
const getPlayerRouteInfo = (pathname) => {
    const pathSegments = getPathSegments(pathname);
    const playerSegmentIndex = pathSegments.indexOf("player");
    if (playerSegmentIndex < 0 || !pathSegments[playerSegmentIndex + 1]) return null;
    const playerIdentifier = decodePathSegment(pathSegments[playerSegmentIndex + 1]);
    if (!playerIdentifier) return null;
    return {
        playerIdentifier,
        routePrefix: pathSegments.slice(0, playerSegmentIndex)
    };
};

// 喵~校验并规范化父页面 origin，只允许 HTTP(S) origin 喵~
const normalizeParentOrigin = (parentOriginValue) => {
    try {
        const parentUrl = new URL(parentOriginValue || "");
        if (parentUrl.protocol !== "http:" && parentUrl.protocol !== "https:") return "";
        return parentUrl.origin;
    } catch (error) {
        // 喵~防御：无效父页面地址不能参与 postMessage 目标匹配喵~
        return "";
    }
};

// 喵~校验 nonce 长度和字符，拒绝空值、空白字符及过长输入喵~
const isValidEmbedNonce = (embedNonce) => typeof embedNonce === "string"
    && embedNonce.length >= 16
    && embedNonce.length <= 256
    && !/\s/.test(embedNonce);

// 喵~读取浏览器报告的祖先页面 origin 列表，用于恢复状态时确认父页面没有变化喵~
const getAncestorOrigins = () => {
    try {
        return Array.from(window.location.ancestorOrigins || []);
    } catch (error) {
        // 喵~防御：浏览器不支持 ancestorOrigins 时返回空列表，由调用方决定是否拒绝恢复喵~
        return [];
    }
};

// 喵~判断 origin 是否与当前 iframe 祖先页面一致，避免复用其他父页面留下的状态喵~
const isParentOriginConsistent = (parentOrigin, requireAncestorMatch = false) => {
    const ancestorOrigins = getAncestorOrigins();
    if (!ancestorOrigins.length) return !requireAncestorMatch;
    return ancestorOrigins.includes(parentOrigin);
};

// 喵~校验 URL 或 sessionStorage 中的嵌入字段，并返回统一候选配置喵~
const createEmbedCandidate = ({parentOrigin, nonce, playerIdentifier, routePrefix, createdAt}) => {
    const normalizedParentOrigin = normalizeParentOrigin(parentOrigin);
    if (!normalizedParentOrigin || !isValidEmbedNonce(nonce) || !playerIdentifier) return null;
    return {
        parentOrigin: normalizedParentOrigin,
        nonce,
        playerIdentifier,
        routePrefix: Array.isArray(routePrefix) ? routePrefix : [],
        createdAt: Number.isFinite(createdAt) ? createdAt : Date.now()
    };
};

// 喵~从当前 URL 参数读取首次嵌入配置，并校验玩家路由和父页面来源喵~
const readQueryEmbedCandidate = (currentUrl, playerRouteInfo) => {
    if (currentUrl.searchParams.get("embed") !== "1") return null;
    const candidate = createEmbedCandidate({
        parentOrigin: currentUrl.searchParams.get("embedParentOrigin"),
        nonce: currentUrl.searchParams.get("embedNonce"),
        playerIdentifier: currentUrl.searchParams.get("embedPlayer"),
        routePrefix: playerRouteInfo?.routePrefix
    });
    if (!candidate || candidate.playerIdentifier !== playerRouteInfo?.playerIdentifier) return null;
    // 喵~防御：若浏览器提供祖先 origin，URL 声明的父页面必须精确匹配喵~
    if (!isParentOriginConsistent(candidate.parentOrigin)) return null;
    return candidate;
};

// 喵~从 iframe 自身 sessionStorage 读取刷新后候选状态喵~
const readStoredEmbedCandidate = (playerRouteInfo) => {
    if (!isEmbeddedWindow()) return null;
    try {
        const storedValue = JSON.parse(window.sessionStorage.getItem(EMBED_STORAGE_KEY) || "null");
        if (!storedValue || storedValue.protocol !== EMBED_PROTOCOL_VERSION) return null;
        if (!Number.isFinite(storedValue.createdAt)) return null;
        if (Date.now() - storedValue.createdAt < 0 || Date.now() - storedValue.createdAt > EMBED_STORAGE_MAX_AGE_MS) return null;
        const candidate = createEmbedCandidate({
            parentOrigin: storedValue.parentOrigin,
            nonce: storedValue.nonce,
            playerIdentifier: storedValue.playerIdentifier,
            routePrefix: Array.isArray(storedValue.routePrefix) ? storedValue.routePrefix : playerRouteInfo?.routePrefix,
            createdAt: storedValue.createdAt
        });
        if (!candidate || candidate.playerIdentifier !== playerRouteInfo?.playerIdentifier) return null;
        // 喵~防御：恢复状态必须能在当前 iframe 祖先页面中找到原父页面 origin 喵~
        if (!isParentOriginConsistent(candidate.parentOrigin, true)) return null;
        return candidate;
    } catch (error) {
        // 喵~防御：sessionStorage 被禁用或内容损坏时按普通页面处理喵~
        return null;
    }
};

// 喵~读取 URL 或受保护的刷新状态，模块加载后固定本次页面的嵌入候选配置喵~
const readEmbedCandidate = () => {
    if (!isEmbeddedWindow()) return null;
    const currentUrl = new URL(window.location.href);
    const playerRouteInfo = getPlayerRouteInfo(currentUrl.pathname);
    if (!playerRouteInfo) return null;
    return readQueryEmbedCandidate(currentUrl, playerRouteInfo)
        || readStoredEmbedCandidate(playerRouteInfo);
};

// 喵~保存握手成功后的最小嵌入状态，供 iframe 刷新后恢复路由限制喵~
const persistEmbedCandidate = (candidate) => {
    try {
        window.sessionStorage.setItem(EMBED_STORAGE_KEY, JSON.stringify({
            protocol: EMBED_PROTOCOL_VERSION,
            parentOrigin: candidate.parentOrigin,
            nonce: candidate.nonce,
            playerIdentifier: candidate.playerIdentifier,
            routePrefix: candidate.routePrefix,
            createdAt: Date.now()
        }));
    } catch (error) {
        // 喵~防御：存储权限不足时不影响当前页面，只放弃刷新恢复喵~
    }
};

// 喵~保存一次解析结果，避免组件渲染期间反复读取可变 URL 参数喵~
const embedCandidate = readEmbedCandidate();

// 喵~导出候选状态，供语言服务在 React 初始化前锁定中文喵~
export const isPlanEmbedCandidate = () => Boolean(embedCandidate);
// 喵~导出候选配置，供 React 路由在应用启动后安装统一地址守卫喵~
export const getPlanEmbedCandidate = () => embedCandidate;
// 喵~导出协议版本，供路由重定向构造保留嵌入参数喵~
export const getPlanEmbedProtocolVersion = () => EMBED_PROTOCOL_VERSION;

// 喵~定义嵌入模式允许访问的当前玩家功能标签页喵~
const allowedPlayerTabs = new Set(["overview", "sessions", "pvppve", "servers"]);

// 喵~比较路径前缀，允许 Router 返回去掉 basename 的 pathname喵~
const hasPathPrefix = (pathSegments, expectedPrefix) => expectedPrefix.every((segment, index) => pathSegments[index] === segment);

// 喵~校验路由地址是否属于锁定玩家的允许体验范围，并兼容 basename 喵~
export const isPlanEmbedPathAllowed = (pathname, lockedPlayerIdentifier, routePrefix = []) => {
    if (!lockedPlayerIdentifier) return false;
    const pathSegments = getPathSegments(pathname);
    const possiblePrefixes = [routePrefix, []];
    let playerRouteSegments = null;
    for (const possiblePrefix of possiblePrefixes) {
        if (!hasPathPrefix(pathSegments, possiblePrefix) || pathSegments[possiblePrefix.length] !== "player") continue;
        playerRouteSegments = pathSegments.slice(possiblePrefix.length);
        break;
    }
    if (!playerRouteSegments || decodePathSegment(playerRouteSegments[1]) !== lockedPlayerIdentifier) return false;
    if (playerRouteSegments.length === 2) return true;
    if (allowedPlayerTabs.has(playerRouteSegments[2])) return playerRouteSegments.length === 3;
    return playerRouteSegments[2] === "plugins" && playerRouteSegments.length === 4 && Boolean(playerRouteSegments[3]);
};

// 喵~创建默认嵌入上下文，普通访问保持完全不受影响喵~
const EmbedContext = createContext({isEmbedMode: false, isEmbedActive: false, lockedPlayerIdentifier: ""});

// 喵~提供嵌入状态与受限 postMessage 握手能力，不修改原生页面 UI 喵~
export const EmbedContextProvider = ({children}) => {
    const isEmbedMode = Boolean(embedCandidate);
    const [isEmbedActive, setIsEmbedActive] = useState(false);

    useEffect(() => {
        if (!embedCandidate) return undefined;
        window.parent.postMessage({
            protocol: EMBED_PROTOCOL_VERSION,
            type: EMBED_READY_MESSAGE_TYPE,
            nonce: embedCandidate.nonce,
            playerIdentifier: embedCandidate.playerIdentifier
        }, embedCandidate.parentOrigin);
        const handleEmbedMessage = (event) => {
            if (event.source !== window.parent || event.origin !== embedCandidate.parentOrigin) return;
            if (!event.data || typeof event.data !== "object") return;
            if (event.data.protocol !== EMBED_PROTOCOL_VERSION || event.data.type !== EMBED_INIT_MESSAGE_TYPE) return;
            if (event.data.nonce !== embedCandidate.nonce || event.data.playerIdentifier !== embedCandidate.playerIdentifier) return;
            if (event.data.locale !== "CN") return;
            persistEmbedCandidate(embedCandidate);
            setIsEmbedActive(true);
        };
        window.addEventListener("message", handleEmbedMessage);
        return () => window.removeEventListener("message", handleEmbedMessage);
    }, []);

    const contextValue = useMemo(() => ({
        isEmbedMode,
        isEmbedActive,
        lockedPlayerIdentifier: embedCandidate?.playerIdentifier || ""
    }), [isEmbedMode, isEmbedActive]);
    return <EmbedContext.Provider value={contextValue}>{children}</EmbedContext.Provider>;
};

// 喵~导出读取嵌入状态的 Hook，保留给需要显示状态的扩展组件使用喵~
export const useEmbed = () => useContext(EmbedContext);
