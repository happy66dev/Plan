// 喵~导入 React 路由工具，用于统一限制嵌入 iframe 的页面地址喵~
import React, {useEffect} from "react";
// 喵~导入导航与当前地址 Hook，便于非法路由替换回玩家概览喵~
import {useLocation, useNavigate} from "react-router";
// 喵~导入嵌入状态与统一路径校验函数，保证所有守卫使用相同白名单规则喵~
import {isPlanEmbedPathAllowed, useEmbed} from "./EmbedContext";

// 喵~在嵌入模式中将任何越界路由替换回锁定玩家概览喵~
const EmbedPlayerRouteGuard = ({children}) => {
    // 喵~读取当前嵌入模式和锁定玩家标识喵~
    const {isEmbedMode, lockedPlayerIdentifier} = useEmbed();
    // 喵~读取 React Router 当前地址喵~
    const location = useLocation();
    // 喵~获取 replace 导航函数，避免非法地址污染浏览器历史喵~
    const navigate = useNavigate();

    useEffect(() => {
        // 喵~普通 Plan 页面不应用嵌入路由限制喵~
        if (!isEmbedMode) return;
        // 喵~当前地址已在允许范围时不执行导航喵~
        if (isPlanEmbedPathAllowed(location.pathname, lockedPlayerIdentifier)) return;
        // 喵~防御：把网络、服务器、管理、其他玩家及未知页面统一替换回锁定玩家概览喵~
        navigate(`/player/${encodeURIComponent(lockedPlayerIdentifier)}/overview${location.search}`, {replace: true});
    }, [isEmbedMode, location.pathname, location.search, lockedPlayerIdentifier, navigate]);

    // 喵~继续渲染现有路由内容，非法路径会由副作用立即替换喵~
    return children;
};

// 喵~导出路由守卫，供应用路由根节点包裹使用喵~
export default EmbedPlayerRouteGuard;
